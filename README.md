# Mnemo

**An LLM gateway with semantic caching.** A Java service in front of the Gemini API with a two-layer exact and semantic cache,
per-key rate limiting, and model fallback.

> Work in progress. The measured-numbers table and architecture diagram go here at milestone 5.

## Run locally

Prerequisites: JDK 21, Docker Desktop.

```powershell
copy .env.example .env      # then fill in GEMINI_API_KEY and ADMIN_TOKEN (32+ chars)
docker compose up -d        # starts Redis 8 and PostgreSQL
.\mvnw.cmd spring-boot:run  # starts the gateway on http://localhost:8080
```

Run the tests (needs Docker running; they use a WireMock fake, never the real Gemini API):

```powershell
.\mvnw.cmd test
```

### Try it

```powershell
$admin = "<your ADMIN_TOKEN>"

# 1. Create a client API key (shown once; only its SHA-256 hash is stored)
$created = Invoke-RestMethod -Method Post http://localhost:8080/admin/keys `
  -Headers @{ Authorization = "Bearer $admin" } -ContentType "application/json" `
  -Body '{"name": "laundry-app"}'
$key = $created.key

# 2. Chat through the gateway
Invoke-RestMethod -Method Post http://localhost:8080/v1/chat `
  -Headers @{ Authorization = "Bearer $key" } -ContentType "application/json" `
  -Body '{"system": "You are a support assistant for a laundry app.",
          "messages": [{"role": "user", "content": "How long does dry cleaning take?"}],
          "temperature": 0.2}'
```

A missing, unknown or revoked key returns `401`; a failed Gemini call returns `502`, a timeout `504`.
Over a key's requests-per-minute or daily token limit returns `429` with a `Retry-After` header.

### Rate limiting: known limitations (interim PostgreSQL version)

Rate limits currently live in PostgreSQL. The brief's Redis Lua token bucket is not built yet.

- **The daily token limit is soft.** Usage is checked before the Gemini call and the actual tokens are
  added after it, so concurrent in-flight requests can push a key past its limit, by roughly
  (requests in flight × tokens per request). Strict enforcement would need a reservation before the call.
- **Requests per minute use fixed windows.** The limit is exact within each UTC minute, even under
  concurrency, but a client can send up to 2× the limit across a minute boundary.
- **No cleanup.** One `api_key_rate_windows` row is written per key per minute and never deleted.
- **No `X-RateLimit-Remaining` header yet.**
- Windows use each gateway instance's clock, so instances with clock skew disagree slightly.

### Troubleshooting

**`password authentication failed for user ...` at startup.** Something else may own port 5432. A native
Windows PostgreSQL service listens on IPv4 `0.0.0.0:5432`, so `localhost` reaches it instead of the
Docker container. Check with:

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen | Select-Object LocalAddress, OwningProcess
Get-Service postgresql*
```

Fix it by stopping that service (admin PowerShell: `Stop-Service postgresql-x64-18`) or by mapping the
container to another port and setting `POSTGRES_PORT` in `.env` to match.

See [docs/PROJECT_BRIEF.md](docs/PROJECT_BRIEF.md) for the full design.
