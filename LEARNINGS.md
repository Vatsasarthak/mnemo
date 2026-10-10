# Learnings

One entry per decision. These become your interview notes.

| Milestone | Decision | Alternative rejected | Why |
| --- | --- | --- | --- |
| 1 | Store API keys as unsalted SHA-256 hex | bcrypt / argon2 | Keys are 32 random bytes (256 bits), so brute force is hopeless and a slow hash adds nothing. A deterministic hash also allows one indexed `WHERE key_hash = ?` lookup; salted bcrypt would force checking every row |
| 1 | Plain SQL with `JdbcClient` | JPA / Hibernate | Small tables, explicit SQL, and batch inserts later are simpler without an ORM |
| 1 | Auth as Spring MVC `HandlerInterceptor`s | Spring Security, or a servlet `Filter` | Security is heavy for one bearer-token check; interceptor exceptions reach `@RestControllerAdvice`, a Filter's do not |
| 1 | Constant-time compare for `ADMIN_TOKEN` (`MessageDigest.isEqual`) | `String.equals` | `equals` returns at the first wrong byte, leaking through timing how much of the token matched |
| 1 | Flyway migrations | `schema.sql` / Hibernate DDL | Versioned, repeatable schema that is identical in tests and locally |
| 1 | Load `.env` via `spring.config.import: optional:file:.env[.properties]` | A dotenv library | Built into Spring Boot; no extra dependency |
| 1 | Errors as RFC 9457 `ProblemDetail` | Ad-hoc error JSON | One consistent error shape for 400/401/502/504 |
| 1 | Run the JVM in UTC (`-Duser.timezone=UTC`) | Rely on the host time zone | Windows JVMs report `Asia/Calcutta`, which the postgres:17 image rejects; servers should not depend on host zone |
| 1 | Call Gemini over HTTP/1.1 | JDK HttpClient default (HTTP/2) | The h2c upgrade on plain `http://` breaks against WireMock; one request per call gains nothing from HTTP/2 |
| 1 | Test against Testcontainers Postgres + WireMock Gemini | H2 / mocks, or the real Gemini API | Real SQL behaviour; failure paths (502, 504) without spending free-tier quota |
