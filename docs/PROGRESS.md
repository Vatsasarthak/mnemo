# Progress

Current milestone: **1 — Skeleton and proxy** (code done; manual real-Gemini check pending)

- [ ] 1. Skeleton and proxy
- [ ] 2. Exact cache and rate limiting
- [ ] 3. Semantic cache
- [ ] 4. Resilience and streaming
- [ ] 5. Usage, metrics and numbers

## Milestone 1 status

| Done criterion | Status |
| --- | --- |
| A valid key gets Gemini's answer | Passes against WireMock (`ChatEndpointTest`). **Not yet verified against the real Gemini API**: the local run was blocked because a native Windows PostgreSQL service owns port 5432 (see README, Troubleshooting) |
| A bad key gets `401` | Done: unknown, missing, malformed and revoked keys all tested |
| Keys are stored hashed | Done: test checks the stored value is the SHA-256 of the raw key |
| `.env` in `.gitignore` from the first commit | Done: never committed (`git log --all -- .env` is empty) |

Tick milestone 1 once the README "Try it" steps return a real Gemini answer.

## Measured numbers (fill in as you measure them)

| Number | Value | Date measured |
| --- | --- | --- |
| Cache hit rate (500-query replay) | | |
| False-hit rate at chosen threshold | | |
| Chosen similarity threshold | | |
| p50 / p99 latency, MISS | | |
| p50 / p99 latency, HIT-SEMANTIC | | |
| Rate-limit accuracy (allowed vs limit) | | |
| Success rate with 30% primary failures | | |
