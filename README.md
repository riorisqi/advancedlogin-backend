# AdvancedLogin — Backend

QR code login, built end to end as a learning project: like WhatsApp Web, Discord or Steam,
a logged-in phone scans a QR code shown on a computer, approves, and the computer is signed in
without typing a password there.

This repo is the **Spring Boot API** and also holds the **design docs**.
The React app lives in the sibling repo [`advancedlogin-frontend`](https://github.com/riorisqi/advancedlogin-frontend).

| | |
|---|---|
| Design, API, state machine, Redis keys | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Project context & decisions | [docs/CLAUDE.md](docs/CLAUDE.md) |
| Attacks and the tests that stop them | [ARCHITECTURE.md §11](docs/ARCHITECTURE.md#11-attack-yourself) |
| Clean architecture study guide (backend + frontend) | [docs/CLEAN_ARCHITECTURE.md](docs/CLEAN_ARCHITECTURE.md) |
| Security status, audit logging plan, hardening backlog | [docs/SECURITY.md](docs/SECURITY.md) |

<p align="center"><img src="docs/images/sequence.svg" alt="QR login sequence" width="720"></p>

## Stack

- Java 21, Spring Boot 4.1 (Spring MVC, Spring Security 7, Spring Session)
- Redis 8: QR sessions (hash + TTL), Lua compare-and-set, Pub/Sub, HTTP sessions
- Server-Sent Events (`SseEmitter`) for live status to the desktop
- Tests: JUnit 5, AssertJ, Testcontainers (real Redis), ArchUnit (architecture rules)

## Quick start

Prerequisites: **JDK 21** and **Docker**.

```bash
docker compose up -d                                   # Redis + RedisInsight, localhost only
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # API on http://localhost:8080 (Windows: mvnw.cmd)
```

Check it: `curl http://localhost:8080/api/health` → `{"status":"UP","redis":"UP"}`.

Then start the frontend (see its README) and open http://localhost:5173.

> **JDK not on PATH?** Point `JAVA_HOME` at it first, e.g. in PowerShell:
> `$env:JAVA_HOME="D:\path\to\jdk-21"`. The Maven wrapper downloads Maven itself.

### Test accounts

There is no sign-up flow. Dev accounts are seeded at startup from `app.seed-users` in
[`application-dev.yml`](src/main/resources/application-dev.yml) (passwords are BCrypt-hashed on load).
They only exist with the **`dev` profile**, so they can never ship to production. In IntelliJ, set
*Active profiles* to `dev` in the run configuration.

### Local Redis

`docker-compose.yml` binds Redis (and RedisInsight) to `127.0.0.1` only and requires a password:
`advancedlogin-dev` unless you set `REDIS_PASSWORD` (the backend's `dev` profile uses the same
default). Outside the `dev` profile there is no default: set `REDIS_PASSWORD`.
Use one on the phone side to sign in, then approve QR logins with it.

### Trying a QR login locally

You need two separate browser "identities": cookies are per browser profile.

1. **Desktop:** open http://localhost:5173 in a normal window. It shows a QR code.
2. **Phone:** open an **incognito/private** window (or another browser), go to
   http://localhost:5173/m and sign in with a test account.
3. Click **dev: open approve link** under the QR on the desktop, copy the URL into the phone
   window, check the details, press **Approve**.
4. The desktop switches to "Welcome, …". It's now signed in with its own session.

### With a real phone (camera scanner)

1. PC and phone on the **same Wi-Fi**. Backend running as usual.
2. In `frontend/`: `bun run dev:lan`. It prints a `Network: https://192.168.x.x:5173/` address.
   (Windows may ask to allow Node/bun through the firewall: allow it for private networks.)
3. **Desktop:** open that `https://192.168.x.x:5173` address (not localhost: the QR link is built
   from the address the desktop uses, and the phone can't reach "localhost").
4. **Phone:** open the same address + `/m`. Accept the certificate warning once (the dev cert is
   self-signed), sign in, tap **Scan QR code**, allow the camera, point it at the screen.
5. Check the details on the phone (browser, OS, the PC's LAN IP), tap **Approve**. The desktop
   signs in.

Why HTTPS: browsers only allow camera access on secure origins (`https://` or `localhost`).

## Running tests

```bash
./mvnw test
```

Integration tests start a throwaway Redis container, so **Docker must be running**. What's covered:

| Level | Examples |
|---|---|
| Domain (plain Java) | `QrSessionTest`: every allowed/forbidden state transition |
| Application (fakes) | `QrSessionServiceTest`: lost compare-and-set race, publish-after-write |
| HTTP + Redis | `QrLoginEndToEndIntegrationTest`: desktop ends up logged in, replay/stolen code fail |
| | `QrApprovalFlowIntegrationTest`: two phones scanning at the same instant, exactly one wins |
| | `QrStatusStreamIntegrationTest`: SSE stream, update published by "another instance" |
| | `RateLimitIntegrationTest`: 429 + `Retry-After`, per-username login lockout |
| Architecture | `CleanArchitectureTest`: dependencies only point inwards (fails the build otherwise) |

## Code layout (clean architecture)

```
src/main/java/com/advancedlogin/
├── domain/        plain Java: QrSession state machine, User, value objects
├── application/   use cases + ports (interfaces the use cases need), no Spring imports
├── adapter/
│   ├── in/web     REST controllers, SSE streams, DTOs
│   ├── in/redis   Pub/Sub subscriber
│   ├── out/redis  Redis store (+ Lua scripts), Pub/Sub publisher, health check
│   └── out/...    password hashing, token generation, in-memory users
└── config/        wiring: use-case beans, Spring Security, Redis messaging
src/main/resources/lua/   qr_create.lua, qr_transition.lua (atomic compare-and-set)
```

Details and the reasoning behind each layer: [CLEAN_ARCHITECTURE.md](docs/CLEAN_ARCHITECTURE.md).

## API at a glance

| Method & path | Who | Purpose |
|---|---|---|
| `GET /api/health` | anyone | backend + Redis reachability |
| `GET /api/auth/csrf` | anyone | (re)issue the `XSRF-TOKEN` cookie |
| `POST /api/auth/login` · `POST /api/auth/logout` · `GET /api/me` | phone / anyone | password session |
| `POST /api/qr/sessions` | desktop (anonymous) | create a QR session, sets `qr_bind` cookie |
| `GET /api/qr/sessions/{id}` · `GET …/{id}/events` | desktop (`qr_bind`) | status snapshot · live SSE stream |
| `POST /api/qr/sessions/{id}/scan` · `/approve` · `/reject` | phone (logged in) | claim, approve, decline |
| `POST /api/qr/sessions/{id}/exchange` | desktop (`qr_bind`) | trade one-time code for a session |

All POSTs need the CSRF header (`X-XSRF-TOKEN`). Errors are RFC 7807 `application/problem+json`.
Full contract: [ARCHITECTURE.md §5](docs/ARCHITECTURE.md).

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `app.qr.timeouts.pending / scanned / confirmed / terminal` | 90s / 60s / 30s / 15s | lifetime of each QR state |
| `app.qr.approve-base-url` | empty → the desktop's `Origin` | base of the URL inside the QR; the phone must reach it. Set it in production (env: `APP_QR_APPROVE_BASE_URL`) |
| `app.qr.secure-cookies` | `false` | set `true` behind HTTPS |
| `app.rate-limit.enabled` / `.rules.<name>.requests` / `.window` | `true`, see `application.yml` | per-IP and per-username limits ([ARCHITECTURE.md §7](docs/ARCHITECTURE.md)) |
| `app.geoip.database` | empty | optional MaxMind-format `.mmdb` (GeoLite2-City / DB-IP City Lite) for "City, Country" on the phone (env: `APP_GEOIP_DATABASE`). LAN IPs always show "Local network" |
| `spring.data.redis.host / port / password` | `localhost` / `6379` / `REDIS_PASSWORD` | Redis (dev profile defaults the password to `advancedlogin-dev`) |
| profile `dev` | off | test accounts + dev Redis password (`application-dev.yml`) |
| `spring.session.data.redis.namespace` | `advancedlogin:session` | key prefix of HTTP sessions |
| `server.forward-headers-strategy` | `native` | trust `X-Forwarded-For` from local/private proxies only (the Vite dev proxy), so the real client IP is recorded |

## Peeking into Redis

RedisInsight runs at http://localhost:5540 (add a database: host `redis`, port `6379`, password `advancedlogin-dev`), or:

```bash
docker exec -it -e REDISCLI_AUTH=advancedlogin-dev backend-redis-1 redis-cli
> SCAN 0 MATCH qr:s:*            # live QR sessions
> SCAN 0 MATCH rl:*              # rate-limit counters (rl:<rule>:<ip or username>)
> HGETALL qr:s:<qrId>            # status, bindHash, scannedBy, ...
> TTL qr:s:<qrId>                # seconds until it expires
> PSUBSCRIBE qr:events:*         # watch status changes fly by
```

## Troubleshooting

- **`Unable to establish loopback connection` when Tomcat starts:** Java's NIO selector on
  Windows uses a Unix-domain socket in the temp folder, and some environments block it. Add the
  JVM option `-Djdk.net.unixdomain.tmpdir=<some writable folder>` (e.g. this project's `target`).
- **Port 8080 already in use:** usually another instance (e.g. started from the IDE). Stop it,
  or run a second copy with `--server.port=8090` and point the frontend at it with
  `BACKEND_URL=http://localhost:8090`.
- **Integration tests fail to start:** Docker isn't running (Testcontainers needs it).
- **`NOAUTH Authentication required`:** the backend has no Redis password. Run with the `dev`
  profile, or set `REDIS_PASSWORD`.
- **Login says "Invalid username or password" for alice/bob:** the `dev` profile isn't active, so
  the test accounts weren't seeded.
- **`429 Too Many Requests` while experimenting:** you hit a rate limit. Wait for `Retry-After`,
  clear the counters (`redis-cli --scan --pattern 'rl:*' | xargs redis-cli DEL`), or set
  `app.rate-limit.enabled=false` locally.
