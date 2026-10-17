# Security & Audit: Status and Backlog

What's already protected, what isn't yet, and how to add each missing piece in the clean
architecture style ([CLEAN_ARCHITECTURE.md](CLEAN_ARCHITECTURE.md)).

- Attacks already defended, with the tests that prove it: [ARCHITECTURE.md §11](ARCHITECTURE.md#11-attack-yourself)
- Priorities: **P0** fix before anyone else is on your network / before deploying ·
  **P1** needed for production · **P2** strong improvements · **P3** nice to have

---

## 1. Already in place (summary)

| Area | Control |
|---|---|
| QR session | 256-bit random ids, short TTLs per state, single use, Lua compare-and-set |
| Browser binding | `qr_bind` HttpOnly + SameSite=Strict cookie, only its SHA-256 stored |
| Exchange | one-time code + binding cookie, constant-time compare, cleared after use |
| QRLjacking | phone shows browser, OS, IP, location, time; scanner accepts only our own link |
| Sessions | Spring Session in Redis, HttpOnly + SameSite=Lax cookie, new id + new CSRF token at login |
| CSRF | double-submit cookie (`XSRF-TOKEN` → `X-XSRF-TOKEN`) on every POST |
| Passwords | BCrypt (delegating encoder), timing-equalised unknown users |
| Abuse | Redis rate limits per IP (QR create/status/exchange, login) and per username |
| API headers | Spring Security defaults: `X-Frame-Options: DENY`, `nosniff`, `no-store` |
| Errors | RFC 7807 bodies, no stack traces (Boot defaults) |

---

## 2. Audit logging (A1, P1)

**Today:** only three infrastructure warnings are logged (GeoIP database, Pub/Sub publish
failure, rate limiter unavailable). Nothing records *who did what*. Without that you can't answer
"who logged into this account and from where?", spot a brute-force attempt, or investigate a
reported takeover.

### 2.1 Events to record

| Event | When | Fields |
|---|---|---|
| `LOGIN_SUCCEEDED` | password login OK | userId, username, ip, userAgent |
| `LOGIN_FAILED` | wrong password / unknown user | username (as typed), ip, reason=`INVALID_CREDENTIALS` |
| `LOGIN_RATE_LIMITED` | 429 on login | username or ip, rule (`login-ip` / `login-user`) |
| `LOGOUT` | logout | userId, ip |
| `QR_CREATED` | desktop creates a QR | qrId (or its hash), desktop ip, userAgent, location |
| `QR_SCANNED` | phone scans | qrId, userId, phone ip, desktop ip |
| `QR_SCAN_CONFLICT` | second user tries to scan/approve | qrId, userId, ip |
| `QR_APPROVED` / `QR_REJECTED` | phone decides | qrId, userId, phone ip, desktop ip + location |
| `QR_LOGIN_COMPLETED` | exchange OK: desktop now logged in | qrId, userId, desktop ip, new session created |
| `QR_EXCHANGE_FAILED` | wrong code / unbound / not approved | qrId, ip, reason |
| `RATE_LIMITED` | any 429 | rule, subject (ip) |

`QR_APPROVED` and `QR_LOGIN_COMPLETED` together give the "new login on Chrome / Windows,
Jakarta" story a user-facing notification (S5) would need.

### 2.2 Never log

Passwords (even wrong ones: they're often a typo of the real one), exchange codes, `qr_bind`
secrets, `SESSION` ids, CSRF tokens, full `Authorization`/`Cookie` headers. `User.toString()`
and `QrSession.toString()` already hide the hash and the code; keep it that way.

### 2.3 How, the clean-architecture way

```
application/audit/port/out/SecurityEvents.java      (outbound port, owned by the application)
    void loginSucceeded(UserId, ClientInfo); void qrApproved(QrSessionId, UserId, ...); ...

application: AuthenticateUserService, QrSessionService, CreateQrSessionService call it
             after the fact they report actually happened (like publish-after-CAS)

adapter/out/audit/Slf4jSecurityEvents.java          (adapter: logger named "audit")
    log.info with structured key/values, e.g. event=QR_APPROVED userId=… qrId=…
```

- Structured output: Spring Boot can emit JSON logs (`logging.structured.format.console: ecs`
  or `logstash`), so each event is a searchable record. Route the `audit` logger to its own
  file/appender with longer retention.
- Who calls it: the **use cases**, since they know what happened. Not the controllers.
- Tests: a fake `SecurityEvents` that records calls, asserted in the existing service tests.
- Privacy: IPs and locations are personal data. Decide on a retention period (e.g. 90 days)
  and document it.
- Correlation: add a request id (MDC) so one login's events can be followed across log lines.

---

## 3. Backlog

### P0: fix now (local network exposure)

**S1. ✅ Done: Redis is open to your whole network, without a password.**
`docker-compose.yml` publishes `"6379:6379"`, so Redis listens on `0.0.0.0` and
`requirepass` is empty. RedisInsight (`:5540`) has no login either. Anyone on the same Wi-Fi
(you're inviting them in with `bun run dev:lan`) can read exchange codes, read or forge HTTP
sessions, or wipe everything. Spring Session stores JDK-serialized objects, so writable Redis
also means untrusted deserialization.
Fixed: compose binds both ports to `127.0.0.1` and Redis runs with `--requirepass`
(`REDIS_PASSWORD`, dev default `advancedlogin-dev`); the backend reads the same variable. Still to
do for production: a real secret, Redis ACLs (a dedicated user with only the needed commands) and TLS.

**S2. ✅ Done: Test accounts ship in the default configuration.**
`app.seed-users` (alice/bob with known passwords) lives in `application.yml`, so every
environment gets them. Move it to `application-dev.yml` (profile `dev`), and make
`DevUserSeeder` `@Profile("dev")`. Fixed exactly so; `AdvancedLoginApplicationTests` checks that
the accounts don't exist in the default profile.

### P1: needed before production

**S3. Clickjacking on the SPA (especially `/m/approve`).** API responses carry
`X-Frame-Options: DENY`, but the HTML pages (served by Vite / the static host) don't. An attacker
could frame the approve page with *their* QR id and trick a tap on "Approve". Fix: serve every
page with `Content-Security-Policy: frame-ancestors 'none'` (and `X-Frame-Options: DENY` for old
browsers). Set it in the production web server / CDN, and in `vite.config.ts` `server.headers`
for dev.

**S4. Security headers for the SPA.**
- `Content-Security-Policy`: `default-src 'self'; script-src 'self'; img-src 'self' data:;
  connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'`. XSS is the
  one thing that defeats HttpOnly *and* CSRF protection, since it runs inside our origin.
- `Referrer-Policy: no-referrer` (approve URLs contain the qrId).
- `Permissions-Policy: camera=(self), microphone=(), geolocation=()`.

**S5. HTTPS everywhere, then lock cookies down.** In production: TLS at the proxy, HSTS
(`Strict-Transport-Security`), `server.servlet.session.cookie.secure=true`,
`app.qr.secure-cookies=true`. Consider the `__Host-` cookie prefix (`__Host-SESSION`), which
forces Secure + Path=/ + no Domain, so a sibling subdomain can't overwrite it.

**S6. Set `app.qr.approve-base-url` in production.** When empty, the QR link is built from the
desktop's `Origin` header (fine in dev, see `ApproveLinks`). In production, pin it to the real
public URL.

**S7. Rate-limit the phone endpoints too.** `scan`, `approve`, `reject` and `logout` currently
have no limit (they need a logged-in session, but a stolen session could still hammer them). Add a
per-user rule (e.g. 30/min) in the filter or the use case.

**S8. Audit logging.** Section 2 (A1).

**S9. Session lifetime.** Today: 30 min idle (`spring.session.timeout`). Add an *absolute*
lifetime (e.g. 12 h) so a stolen cookie can't be kept alive forever by activity, and decide on
remember-me policy deliberately.

**S10. Dependency and secret hygiene.** Dependabot / Renovate on both repos, `./mvnw
org.owasp:dependency-check-maven:check` or GitHub dependency review, `bun audit`, and secret
scanning (no `.env`/keys in git).

### P2: strong improvements to QR login itself

**S11. Number matching (anti-QRLjacking, anti-tap-fatigue).** Desktop shows a 2-digit number
after the scan; the phone shows three choices and the user must pick the matching one. A relayed
QR fails because the victim can't see the attacker's screen. (Microsoft Authenticator uses this
technique.) Fits the state machine: generate on `scan`, store in the session, check in `approve`.

**S12. Step-up on the phone before approving.** Require the device's screen lock or a PIN/
biometric (WebAuthn `userVerification: required`) to approve, so an unlocked, stolen phone can't
approve logins on its own.

**S13. "New login" notifications and a device list.** After `QR_LOGIN_COMPLETED`, notify the
user ("New sign-in: Chrome on Windows, Jakarta, 14:32"), and show active sessions with "sign out
this device". Needs session indexing (Spring Session's indexed repository, or our own
`user → sessions` set in Redis).

**S14. Safer session serialization.** Switch Spring Session from JDK serialization to JSON
(`GenericJackson2JsonRedisSerializer` / Spring Security's Jackson modules). Smaller blast radius
if Redis is ever writable by someone else (S1).

**S15. Progressive login delays / lockout policy.** Beyond the fixed 5/min: exponential backoff
per username, and alerting on many usernames failing from one IP (password spraying).

### P3: nice to have

- **S16.** Bot friction on anonymous `POST /api/qr/sessions` beyond IP limits (proof-of-work or a
  CAPTCHA after N requests), if QR farming becomes a problem.
- **S17.** Monitoring: Spring Boot Actuator + metrics for logins, 429s and QR outcomes, with
  alerts on spikes.
- **S18.** Password policy and breached-password check (HIBP k-anonymity), once there's a
  sign-up or password-change flow.
- **S19.** Native phone app (React Native / Flutter): certificate pinning, device binding,
  secure storage for tokens.
- **S20.** GeoIP: keep the database updated; consider showing only country for privacy.

---

## 4. Suggested order

1. ~~**S1 + S2**~~ done: Redis is localhost-only with a password, test users are dev-profile only.
2. **A1 audit logging** (an afternoon): biggest visibility win, and a nice clean-architecture
   exercise (new port + adapter + tests).
3. **S3 + S4** headers (dev: `vite.config.ts`; prod: web server config).
4. **S7, S9**: close the remaining rate-limit and session-lifetime gaps.
5. **S11 number matching**: the most interesting QR-specific upgrade.
6. The rest as the project heads toward a real deployment.
