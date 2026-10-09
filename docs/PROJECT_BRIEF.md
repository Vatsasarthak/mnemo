# Mnemo — Project Brief

Oct 9, 2026 · Vatsa

## Overview

Mnemo is an LLM gateway: a Java service that sits between apps and the Gemini API. It answers repeated or similar questions from a Redis semantic cache, enforces per-key rate limits, and falls back to a second model when the first fails.

**The problem.** Teams calling LLM APIs pay for the same question many times, hit provider rate limits, and break when one model has an outage. A gateway fixes all three in one place, with no change to the apps behind it.

**The pitch.** One endpoint in front of Gemini that makes LLM calls cheaper, faster and harder to break, and proves it with measured numbers.

## Scope

Version 1 is five features done deeply, not ten done shallowly. Everything else waits until after placements.

| In v1 | Left out of v1 |
| --- | --- |
| API-key auth for client apps | User accounts, login UI, billing |
| Exact-match cache, then semantic cache | Multi-turn conversation caching |
| Per-key rate limiting in Redis | Admin dashboard (use metrics and logs) |
| Model fallback, retries, circuit breaker | Multiple providers beyond Gemini |
| Streaming responses | Kubernetes, cloud deployment |
| Usage and cost tracking per key | Prompt guardrails and content filtering |

The cache only covers single-turn requests: one system prompt plus one user message. Multi-turn chats pass straight through to Gemini uncached.

## Architecture

Every request passes six steps in order, and either cache can answer before Gemini is called.

```
Client app
    |
    v
+------------------------- Gateway (Spring Boot) --------------------------+
|                                                                          |
|  1. Auth            hashed API key checked against Postgres  -> 401      |
|        | valid key                                                       |
|  2. Rate limit      Lua token bucket in Redis                -> 429      |
|        | within limit                                                    |
|  3. Exact cache     SHA-256 of normalized prompt in Redis    -> HIT      |
|        | miss                                                            |
|  4. Semantic cache  Gemini embedding + KNN in Redis          -> HIT      |
|        | miss                                                            |
|  5. Model router    gemini-3.8-flash, fallback 3.5-flash-lite            |
|                     retries + circuit breaker; saves to both caches      |
|        |                                                                 |
|  6. Record usage    async queue -> Postgres usage_events (batched)       |
+--------------------------------------------------------------------------+

Stores:  PostgreSQL (api_keys, usage_events)
         Redis 8 (token buckets, exact entries, HNSW vector index)
         Gemini API (gemini-3.8-flash, gemini-3.5-flash-lite, gemini-embedding-2)
```

A hit at step 3 or 4 returns straight to the client; only a miss reaches the model router. Usage is recorded off the request path, so it never adds latency.

## Tech stack

Java and Spring Boot for the service, Redis for the cache and rate limits, Postgres for keys and usage, Gemini behind it all.

| Layer | Choice | Why |
| --- | --- | --- |
| Language | Java 21 | Virtual threads for many waiting LLM calls; fixes core-Java gaps |
| Framework | Spring Boot (current stable) | Web, config, Actuator metrics out of the box |
| Primary model | `gemini-3.8-flash` | Current stable Flash model, on the free tier |
| Fallback model | `gemini-3.5-flash-lite` | Cheaper, faster stable model, also free tier |
| Embeddings | `gemini-embedding-2`, 768 dimensions | Current stable embedding model; 768 keeps vectors small |
| Cache and rate limits | Redis 8 (official Docker image) | Vector search is built into Redis 8, no extra module |
| Keys and usage | PostgreSQL | Durable records; already on your resume |
| Gemini calls | Plain REST via Spring `RestClient` | No SDK wrapper, so you learn the real API |
| Resilience | Your own retry and circuit breaker | About 100 lines; a strong interview talking point |
| Tests | JUnit 5, Testcontainers, WireMock | Real Redis and Postgres in tests; fake Gemini for failures |
| Load test | k6 | Scripted load with latency percentiles |
| Local setup | Docker Compose | One command starts Redis and Postgres |

**Free-tier rules.** Google says free-tier content may be used to improve its products, so never send personal or private data. Free-tier rate limits are low, so run load tests against the WireMock fake, not real Gemini.

**Open question:** the docs don't say whether `gemini-embedding-2` is on the free tier. Check the rate-limit page in Google AI Studio when you create your key.

Checked 9 Oct 2026: [Gemini models](https://ai.google.dev/gemini-api/docs/models) · [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing) · [Gemini embeddings](https://ai.google.dev/gemini-api/docs/embeddings) · [Redis 8 release](https://redis.io/blog/redis-8-ga/) · [Redis vector search](https://redis.io/docs/latest/develop/ai/search-and-query/vectors/)

## API design

Three endpoints. Apps only ever call `/v1/chat`; the gateway decides cache, model and limits.

| Endpoint | Who calls it | What it does |
| --- | --- | --- |
| `POST /v1/chat` | Client apps | Chat request; cached, rate-limited, with fallback |
| `GET /v1/usage` | Client apps | Their own requests, tokens, cache hits and cost saved |
| `POST /admin/keys` | You, with an admin token | Creates an API key; shown once, stored only as a hash |

**Headers.** Requests send `Authorization: Bearer <gateway key>`. Responses return `X-Cache` (`HIT-EXACT`, `HIT-SEMANTIC`, `MISS` or `BYPASS`), `X-Model-Used` and `X-RateLimit-Remaining`.

**Errors.** `401` bad key · `429` over limit, with `Retry-After` · `502` both models failed · `504` upstream timeout.

Example request:

```json
{
  "system": "You are a support assistant for a laundry app.",
  "messages": [{ "role": "user", "content": "How long does dry cleaning take?" }],
  "temperature": 0.2,
  "stream": false
}
```

Example response, served from the semantic cache:

```json
{
  "content": "Dry cleaning usually takes 2 to 3 days...",
  "model": "gemini-3.8-flash",
  "cache": { "status": "HIT-SEMANTIC", "similarity": 0.94 },
  "usage": { "inputTokens": 0, "outputTokens": 0 },
  "latencyMs": 18
}
```

A cache hit reports zero tokens because Gemini was never called. That number is your cost saving.

## Core features

The semantic cache is the heart of the project; the other four make it production-shaped. Each number below is a starting default you tune and justify.

### Semantic cache

1. Normalize the prompt: trim, lowercase, collapse spaces.
2. Scope every cache entry to one API key, one model and one system prompt. Never share answers across keys, or one customer's data leaks to another.
3. **Exact layer first.** SHA-256 of the normalized request, looked up in Redis. A hit costs no embedding call.
4. **Semantic layer next.** Embed the user message, then run a KNN search for the 1 nearest cached prompt within that key's scope.
5. If similarity is at or above the threshold, return the cached answer. Start at 0.92 and tune it with the eval set (Measurement).
6. On a miss, call Gemini, then store the answer in both layers with a TTL, such as 24 hours.
7. Skip the cache when temperature is above 0.5 or the request sends `"cache": false`.

Redis returns cosine **distance**, not similarity: similarity = 1 − distance. Getting this backwards is the classic bug.

### Rate limiting

- A token bucket per API key, stored in Redis. A Lua script does check-and-decrement in one atomic step, so limits hold across many gateway instances.
- Two limits: requests per minute, and LLM tokens per day. Cache hits count toward requests but not tokens.
- Over the limit returns `429` with `Retry-After`.
- Decide what happens if Redis is down: fail open (allow) or fail closed (block). Write the choice and the reason in the README.

### Fallback, retries and circuit breaker

- Every Gemini call has a timeout, such as 15 seconds.
- Retry the primary model up to 2 times on `429`, `5xx` or timeout, with exponential backoff plus jitter. Never retry a `400`.
- A circuit breaker per model: after 5 straight failures it **opens** and skips that model for 30 seconds. It then goes **half-open** and lets 1 trial request through.
- While the primary's breaker is open, requests go straight to the fallback model. If both fail, return `502`.

### Streaming

- With `"stream": true`, the gateway relays Gemini's chunks to the client as Server-Sent Events as they arrive.
- It assembles the full answer alongside, and caches it only if the stream finishes cleanly.
- A cache hit on a streaming request is sent as one event.

### Usage tracking

- Every request records: key, model used, cache status, input and output tokens (from Gemini's usage metadata), latency and time.
- Writes happen off the request path: a bounded in-memory queue flushed to Postgres in batches, so tracking never slows a response.
- Cost saved per hit = the tokens the original answer used × that model's price.

## Data model

Postgres holds what must survive a restart; Redis holds what must be fast and can expire.

**Postgres tables**

| Table | Columns | Notes |
| --- | --- | --- |
| `api_keys` | `id`, `name`, `key_hash`, `rpm_limit`, `daily_token_limit`, `created_at`, `revoked_at` | Store only the SHA-256 hash, never the raw key |
| `usage_events` | `id`, `api_key_id`, `model`, `cache_status`, `input_tokens`, `output_tokens`, `latency_ms`, `created_at` | Index on (`api_key_id`, `created_at`) for the usage endpoint |

**Redis keys**

| Key | Type | Holds | Expires |
| --- | --- | --- | --- |
| `cache:exact:{keyId}:{sha256}` | String | Cached answer as JSON | 24 h |
| `cache:sem:{entryId}` | Hash | Prompt, answer, tokens, scope tags, embedding vector | 24 h |
| `rl:req:{keyId}` | Hash | Token bucket: tokens left, last refill time | Idle timeout |
| `rl:tok:{keyId}:{date}` | Counter | LLM tokens used today | 1 day |

**Vector index** over the semantic entries, filtered by scope tags:

```
FT.CREATE idx:semcache ON HASH PREFIX 1 cache:sem:
  SCHEMA tenant TAG model TAG sys TAG
  embedding VECTOR HNSW 6 TYPE FLOAT32 DIM 768 DISTANCE_METRIC COSINE
```

Circuit-breaker state lives in memory per gateway instance in v1. Moving it to Redis so all instances share it is a good stretch goal and interview topic.

## Milestones

Five milestones of about one week each at 1.5 hours a day. Don't start the next one until the current one's done-criteria pass and you can explain every file.

1. **Skeleton and proxy.** Spring Boot project, Docker Compose with Redis 8 and Postgres, the `api_keys` table and admin endpoint, and `/v1/chat` calling `gemini-3.8-flash` without streaming.
    - Done when: a valid key gets Gemini's answer, a bad key gets `401`, and keys are stored hashed.
    - Gemini key lives in `.env`, which is in `.gitignore` from the first commit.
2. **Exact cache and rate limiting.** Exact-match layer, the `X-Cache` header, and the Lua token bucket.
    - Done when: a repeated request returns `HIT-EXACT` and WireMock confirms Gemini got zero calls.
    - Done when: 100 parallel requests against a limit of 60 let exactly 60 through (Testcontainers test).
3. **Semantic cache.** Embeddings, the vector index, KNN search with scope tags, and the similarity threshold.
    - Done when: a reworded question returns `HIT-SEMANTIC`.
    - Done when: the eval set (Measurement) reports the false-hit rate at your chosen threshold.
4. **Resilience and streaming.** Timeouts, retries with backoff, the circuit breaker, model fallback and SSE streaming.
    - Done when: with WireMock returning `503` for the primary, requests are served by the fallback.
    - Done when: tests show the breaker opening after 5 failures and closing again through half-open.
5. **Usage, metrics and numbers.** Async usage events, `/v1/usage`, Micrometer metrics, the k6 load test, and the README.
    - Done when: the README has an architecture diagram and a table of your real measured numbers.

## Measurement

Six numbers turn this from "uses AI" into a shortlist-worthy project. Measure each one; never estimate.

| Number | How to get it |
| --- | --- |
| Cache hit rate | Replay about 500 queries: 100 base questions, each reworded 5 ways. Hit rate = hits ÷ total. |
| False-hit rate | 200 labelled prompt pairs, half same meaning, half different. Count wrong hits at thresholds 0.85 to 0.97. |
| Latency, p50 and p99 | k6 plus Micrometer histograms, split by `HIT-EXACT`, `HIT-SEMANTIC` and `MISS`. |
| Gemini calls avoided | Calls not made ÷ total requests over the replay, plus the tokens and cost saved. |
| Rate-limit accuracy | k6 with 50 virtual users against a fixed limit: requests allowed vs the limit. |
| Success under failure | WireMock fails 30% of primary calls: success rate with fallback on vs off. |

**Choosing the threshold.** Plot false-hit rate against hit rate across the thresholds. Pick the point where wrong answers stay rare enough to defend, and write that reasoning in the README.

**Saving quota.** Load and failure tests run against WireMock, with a fixed delay to mimic Gemini, so they measure the gateway itself. Only the eval sets need real embeddings; save those vectors to a file so you embed each prompt once.

## Interview questions

If you can answer all 14 without notes, the project is yours. Say each answer out loud at the end of the milestone that builds it.

| Question | A good answer covers |
| --- | --- |
| Why a semantic cache, not just exact match? | Rewordings miss an exact cache; semantic costs an embedding call and risks wrong hits; exact runs first |
| How did you pick the similarity threshold? | The eval set, the hit-rate vs false-hit trade-off, your actual numbers |
| What does a wrong cache hit cost, and how do you limit it? | A wrong answer served; scoping, TTL, skipping high temperature, the opt-out flag |
| Why scope the cache per API key? | Otherwise one customer's answer, and data, reaches another |
| Why a Lua script for rate limiting? | Read-check-write must be atomic; without it, instances race and over-admit |
| Token bucket vs fixed window vs sliding window? | Bursts vs smoothness vs spikes at window edges; why you chose the bucket |
| What happens if Redis goes down? | Cache: treat as a miss and still serve. Limiter: your fail-open or fail-closed choice and why |
| Walk through the circuit breaker states. | Closed, open, half-open; the thresholds; how it protects you and the provider |
| Why add jitter to retries? | Synchronized retries hit the provider together, a thundering herd |
| How would it handle 10× traffic? | Stateless instances behind a load balancer, shared Redis, index memory, sharding by key |
| Why are usage writes async, and what can you lose? | Latency off the request path; buffered events lost on a crash; the outbox pattern as the fix |
| HNSW vs a flat index? | Approximate and fast vs exact and slow; when the size of the cache makes it matter |
| How do you cache a streamed answer? | Assemble it alongside the stream; store it only after a clean finish |
| Why store API keys hashed? | A leaked database then leaks no usable keys, same as passwords |

The async-usage question links straight to your outbox project. Use it: it shows your projects connect.

## Building with Claude Code

Claude Code writes the plumbing; you write the parts interviewers will ask about. That split is what makes this project yours.

**Setup.** This brief lives in the repo as `docs/PROJECT_BRIEF.md`, and `CLAUDE.md` sits at the repo root; Claude Code reads it at the start of every session.

**Habits**

- One branch and one pull request per milestone. It's real Git practice, and the PR history shows how you work.
- Keep `LEARNINGS.md`: each decision, the alternative you rejected, and why. It becomes your interview notes.
- If you can't explain a file, stop and ask Claude Code to walk you through it before moving on.

## Resume line

Fill every bracket with a number you measured. If a number is weak, drop that clause rather than round it up.

> **Mnemo — LLM Gateway with Semantic Caching** · Java, Spring Boot, Redis, PostgreSQL, Gemini API · GitHub
>
> - Built an LLM gateway with a two-layer exact and semantic cache (Redis vector search), serving [X]% of a 500-query replay without calling the model, at a [Y]% false-hit rate.
> - Cut p99 latency from [A] s on a miss to [B] ms on a cache hit; added per-key token-bucket rate limiting with atomic Lua scripts, accurate to [N] of [M] requests under 50 concurrent users.
> - Kept [Z]% of requests succeeding with 30% injected primary-model failures, using retries with jitter, a circuit breaker and model fallback.

Keep the repo public with a README that leads with the measured-numbers table and the architecture diagram.
