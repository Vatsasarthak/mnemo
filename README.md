# Mnemo

**An LLM gateway with semantic caching.** A Java service in front of the Gemini API with a two-layer exact and semantic cache,
per-key rate limiting, and model fallback.

> Work in progress. The measured-numbers table and architecture diagram go here at milestone 5.

## Run locally

Prerequisites: JDK 21, Docker Desktop.

```powershell
copy .env.example .env      # then fill in GEMINI_API_KEY and ADMIN_TOKEN
docker compose up -d        # starts Redis 8 and PostgreSQL
.\mvnw.cmd spring-boot:run  # available after milestone 1
```

See [docs/PROJECT_BRIEF.md](docs/PROJECT_BRIEF.md) for the full design.
