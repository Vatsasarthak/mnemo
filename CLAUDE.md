# CLAUDE.md

This project is **Mnemo**, an LLM gateway with semantic caching.

Read docs/PROJECT_BRIEF.md before any work. We build one milestone at a time,
in the order the brief lists them. Check docs/PROGRESS.md to see which milestone is current.

## Rules
- Explain your plan and wait for my OK before writing code.
- I write the core logic myself: cache lookup, the Lua token bucket, the circuit breaker.
  For those, review my code and explain bugs; do not rewrite them.
- You may write config, DTOs, Docker Compose, test scaffolding and boilerplate.
- Never put the Gemini API key in code or chat; it lives in .env only.
- End every milestone by asking me 5 questions about the code we wrote.
- Teach as you go: when you introduce a library, annotation or pattern I may not know,
  explain it in two or three sentences.

## Project facts
- Java 21, Spring Boot (current stable), Maven wrapper (`mvnw`).
- Base package: `dev.vatsa.mnemo`.
- Redis 8 and PostgreSQL run in Docker: `docker compose up -d` from the repo root.
- Gemini models: `gemini-3.8-flash` (primary), `gemini-3.5-flash-lite` (fallback),
  `gemini-embedding-2` at 768 dimensions. Call the REST API with Spring `RestClient`, no SDK.
- Config comes from environment variables listed in `.env.example`.
- Tests: JUnit 5, Testcontainers (real Redis and Postgres), WireMock (fake Gemini).
  Load and failure tests run against WireMock, never the real Gemini API.
- Dev machine is Windows: give PowerShell commands, and use `mvnw.cmd` there.

## Git workflow
- One branch per milestone, e.g. `m1-skeleton`. Small commits with clear messages.
- Open a pull request per milestone and summarize what was built and why.
- After each milestone, add the decisions we made to LEARNINGS.md
  (decision, alternative rejected, reason) and tick the milestone in docs/PROGRESS.md.
