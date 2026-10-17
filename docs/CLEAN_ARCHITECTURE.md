# Clean Architecture in AdvancedLogin

How the backend and the frontend are structured, why, and what it costs. Written as a study
guide: read it once top to bottom, then come back to the section you need.

Related: [ARCHITECTURE.md](ARCHITECTURE.md) (the QR login design itself); its
[§9](ARCHITECTURE.md#9-project-structure--clean-architecture) has the folder trees.

---

## 1. The one rule

> **Source-code dependencies only point inwards.**

Business rules sit in the centre and know nothing about Spring, Redis, HTTP, React or the
browser. Everything technical sits around the edge and depends on the centre, never the other
way round.

```
        ┌──────────────────────────────────────────────┐
        │  config / app   (wiring: creates and connects) │
        │   ┌──────────────────────────────────────────┐ │
        │   │  adapters / infrastructure / presentation │ │
        │   │  (Spring MVC, Redis, SSE, fetch, React UI)│ │
        │   │   ┌────────────────────────────────────┐ │ │
        │   │   │  application (use cases + ports)    │ │ │
        │   │   │   ┌──────────────────────────────┐ │ │ │
        │   │   │   │  domain (rules, plain code)   │ │ │ │
        │   │   │   └──────────────────────────────┘ │ │ │
        │   │   └────────────────────────────────────┘ │ │
        │   └──────────────────────────────────────────┘ │
        └──────────────────────────────────────────────┘
                 every arrow points inwards ──▶
```

Why "inwards"? The inner rings change for *business* reasons ("a QR may only be approved by
whoever scanned it"). The outer rings change for *technical* reasons (SSE instead of polling,
Postgres instead of memory). When the arrows point inwards, a technical change never forces you
to touch business code.

---

## 2. Ports: how the centre talks to the outside without depending on it

A **port** is an interface owned by the application layer. There are two kinds:

| | Inbound port (`port/in`) | Outbound port (`port/out`) |
|---|---|---|
| Question it answers | "What may the outside ask us to do?" | "What do we need from the outside?" |
| Example | `ApproveQrSessionUseCase` | `QrSessionStore`, `QrStatusPublisher`, `RandomTokenGenerator` |
| Implemented by | the application service (`QrSessionService`) | an adapter (`RedisQrSessionStore`, ...) |
| Called by | an inbound adapter (`QrMobileController`) | the application service |

The second kind is the trick that makes everything work: **dependency inversion**.

```
Without ports (classic layering):        With an outbound port:

QrSessionService ──imports──▶ Redis      QrSessionService ──uses──▶ QrSessionStore (interface)
                                                                          ▲
                                         RedisQrSessionStore ─implements──┘
```

At runtime the call still goes service → Redis. But in the *source code*, the arrow now points
from the Redis adapter to the application. The use case declares what it needs; the technology
fits itself to that need. This style is also called **hexagonal architecture** or
**ports and adapters**; "clean architecture" is the same idea with named rings.

---

## 3. Backend (Spring Boot)

### 3.1 The rings

| Ring | Package | Holds | May import | Example |
|---|---|---|---|---|
| Domain | `domain` | business objects and rules, plain Java | JDK only | `QrSession.scan/approve/reject/consume`, `BindingHash`, `User` |
| Application | `application` | use cases + ports | domain | `QrSessionService`, `CreateQrSessionService`, `AuthenticateUserService` |
| Adapter | `adapter.in.*`, `adapter.out.*`, `adapter.common.*` | technology | application, domain | `QrDesktopController`, `RedisQrSessionStore`, `GeoIpLocationLookup` |
| Config | `config` | composition root: builds and wires objects | everything | `UseCaseConfig`, `SecurityConfig`, `RateLimitConfig` |

- **Inbound adapters** (`adapter.in`) turn something from outside into a use-case call: HTTP
  controllers, the SSE endpoint, the Redis Pub/Sub subscriber, the rate-limit filter.
- **Outbound adapters** (`adapter.out`) implement outbound ports: Redis store, Pub/Sub publisher,
  BCrypt hasher, `SecureRandom` tokens, GeoIP, the in-memory user store.

### 3.2 One request through the rings: "the phone taps Approve"

```
POST /api/qr/sessions/{id}/approve
 └─ QrMobileController                      adapter.in.web   parse id, get the logged-in user
     └─ ApproveQrSessionUseCase.approve     application      (interface; QrSessionService implements it)
         ├─ RandomTokenGenerator.newToken   → SecureRandomTokenGenerator     (adapter.out)
         ├─ QrSessionStore.findById         → RedisQrSessionStore            (adapter.out)
         ├─ session.approve(user, code, …)  → domain: is this allowed? new state + expiry
         ├─ QrSessionStore.compareAndSet    → lua/qr_transition.lua          (adapter.out)
         └─ QrStatusPublisher.publish       → RedisQrStatusPublisher         (adapter.out)
                                                └─ Redis Pub/Sub
                                                    └─ RedisQrEventSubscriber   (adapter.in.redis)
                                                        └─ QrStatusStreams → SSE → desktop
```

Notice who does what:

- The **controller** has no rules. It translates HTTP ↔ use case and DTO ↔ domain.
- The **domain** has no I/O. `QrSession.approve` just answers "allowed or not, and what's the
  new state?" It throws `IllegalQrTransitionException` if not.
- The **service** only orchestrates: read → let the domain decide → compare-and-set → announce.
- **Redis** appears only in adapters.

### 3.3 Wiring: the composition root

Use-case classes carry **no Spring annotations** (`@Service`, `@Transactional`, ...). They are
created in `config/UseCaseConfig` as `@Bean`s:

```java
@Bean
QrSessionService qrSessionService(QrSessionStore store, QrStatusPublisher publisher,
                                  RandomTokenGenerator tokens, UserRepository users,
                                  Clock clock, QrTimeouts timeouts) {
    return new QrSessionService(store, publisher, tokens, users, clock, timeouts);
}
```

Adapters *are* Spring components (`@Component`, `@RestController`). Spring finds them, sees that
`RedisQrSessionStore` implements `QrSessionStore`, and injects it here. The application layer
never knows Spring exists, which is why its unit tests just call `new`.

Also note `Clock`: use cases ask a `Clock` for "now" instead of calling `Instant.now()`, so tests
can freeze time (`Clock.fixed(...)`).

### 3.4 Data crosses the edges as different types

Each edge has its own shapes, and adapters translate:

| Edge | Outside shape | Inside shape | Translated in |
|---|---|---|---|
| HTTP in | `LoginRequestResponse` record (JSON) | `QrSession` | `QrMobileController` |
| Redis | hash fields (`status`, `bindHash`, epoch millis) | `QrSession` | `RedisQrSessionStore.toFields/fromFields` |
| Pub/Sub | `"CONFIRMED:1792…:Xy7…"` | `QrStatusUpdate` | `QrEventChannel.encode/decode` |
| Session | `SessionUser` (Serializable) | `UserId` | `QrMobileController.userId(...)` |

So renaming a JSON field never touches the domain, and the domain can enforce invariants (e.g.
"`exchangeCode` exists exactly while CONFIRMED") without caring how JSON or Redis represent it.

### 3.5 Errors

The domain and application throw meaningful exceptions (`IllegalQrTransitionException`,
`QrSessionNotFoundException`, `InvalidExchangeCodeException`, `TooManyRequestsException`). One
inbound adapter, `ApiExceptionHandler`, maps them to HTTP (409, 404, 400, 429) as RFC 7807
problem+json. The core never mentions status codes.

---

## 4. Frontend (React + TypeScript)

The same rule, adapted to a browser app.

### 4.1 The rings

| Ring | Folder | Holds | May import | Example |
|---|---|---|---|---|
| Domain | `src/domain/` | types and pure functions; no React, no `fetch` | nothing outside domain | `QrStatus` union, `parseApproveLink`, `secondsUntilExpiry` |
| Application | `src/application/` | use-case **hooks**, app-wide state, **ports** | domain (+ React) | `useQrLogin`, `useQrApproval`, `useQrScanner`, `AuthProvider` |
| Infrastructure | `src/infrastructure/` | port implementations: HTTP, SSE, camera | application ports, domain | `httpQrGateway`, `sseQrStatusStream`, `cameraQrCodeScanner`, `httpClient` |
| Presentation | `src/presentation/` | pages and components | application hooks, domain | `QrLoginPanel`, `MobileApprovePage` |
| Composition root | `src/app/` | creates implementations, routes | everything | `DependenciesProvider`, `App` |

Mapping to the backend:

| Backend | Frontend |
|---|---|
| `domain` | `domain/` |
| `application` use cases | `application/` hooks (`useQrLogin` ≈ a use case with state) |
| `port/out` interfaces | `application/ports/` (`QrGateway`, `QrStatusStream`, `QrCodeScanner`, …) |
| outbound adapters | `infrastructure/` |
| inbound adapters (controllers) | `presentation/` (the UI is how the user "calls in") |
| `config/UseCaseConfig` | `app/DependenciesProvider.tsx` |

### 4.2 Dependency injection with React context

There's no Spring on the frontend, so injection is done with a React context:

```ts
// application/dependencies.ts: the ports the app needs (types only)
export type Dependencies = {
  qrGateway: QrGateway
  qrStatusStream: QrStatusStream
  // ...
}
export const DependenciesContext = createContext<Dependencies | null>(null)
export function useDependencies(): Dependencies { /* reads the context */ }

// app/DependenciesProvider.tsx: the composition root picks real implementations
const dependencies: Dependencies = {
  qrGateway: createHttpQrGateway(),
  qrStatusStream: createSseQrStatusStream(),
  // ...
}
```

A hook calls `useDependencies()` and gets *interfaces*. It never imports from
`infrastructure/`. In a test or a Storybook story you'd wrap the component in a provider with
fakes, e.g. a `QrStatusStream` that emits `SCANNED` after one second, with no backend at all.

### 4.3 One flow through the rings: "the desktop follows its QR status"

```
QrLoginPanel                         presentation   renders state, no fetch, no URLs
 └─ useQrLogin()                     application    create session, countdown, auto-refresh
     ├─ qrGateway.createSession()    → httpQrGateway (infrastructure): POST, DTO → QrSession,
     │                                  server clock → local clock
     ├─ qrStatusStream.subscribe()   → sseQrStatusStream (infrastructure): EventSource
     │     onUnavailable ─▶ startPolling() → qrGateway.getStatus() (polling fallback)
     ├─ applySnapshot(prev, snapshot)  pure state transition (application)
     └─ qrGateway.exchange() + auth.refresh()   when CONFIRMED
```

- `QrLoginPanel` doesn't know whether updates come from SSE or polling, or that HTTP exists.
- `useQrLogin` doesn't know about `EventSource`. It knows "a stream that can be unavailable".
- `sseQrStatusStream` doesn't know about React. It's a plain function returning an unsubscribe.

### 4.4 Errors and HTTP stay in infrastructure

Infrastructure turns HTTP details into application errors:

| HTTP | Becomes | Thrown by |
|---|---|---|
| 401 on login | `InvalidCredentialsError` | `httpAuthGateway` |
| 429 on login | `TooManyAttemptsError(retryAfterSeconds)` | `httpAuthGateway` |
| 404 on a QR | `QrNotFoundError`, or status `EXPIRED` on the desktop | `httpQrApprovalGateway`, `httpQrGateway` |
| 409 on a QR | `QrConflictError(detail)` | `httpQrApprovalGateway` |

Pages check `err instanceof InvalidCredentialsError`, never `res.status === 401`.

### 4.5 Pragmatic choices on the frontend

- **React is allowed in `application/`.** Hooks *are* the natural use-case unit in React, and
  forbidding them would mean writing a mini state framework. What's forbidden there is I/O
  (`fetch`, `EventSource`, `getUserMedia`).
- **Pure logic is pulled out of hooks when it gets interesting.** `applySnapshot` (a pure
  state transition) and `parseApproveLink` (security-relevant parsing) are plain functions, and
  the latter is unit-tested with `bun test`.

---

## 5. How the rule is enforced

Rules nobody checks erode. Both repos fail the build when the arrows point the wrong way.

| Repo | Tool | Where | Fails |
|---|---|---|---|
| backend | ArchUnit | `src/test/.../architecture/CleanArchitectureTest.java` | `./mvnw test` |
| frontend | oxlint `no-restricted-imports` | `.oxlintrc.json` (`overrides` per folder) | `bun run lint` |

Backend rules:
1. Layers: config → adapter → application → domain, nothing upward.
2. `domain` and `application` may not import `org.springframework..`, `jakarta.servlet..`,
   `io.lettuce..` or `tools.jackson..`.
3. `adapter.in` and `adapter.out` may not import each other (shared wire formats go in
   `adapter.common`).

Frontend rules: `domain/` imports no React and no outer folder; `application/` no
`infrastructure/`, `presentation/` or `app/`; `presentation/` no `infrastructure/` or `app/`.

Try it: add `@org.springframework.stereotype.Service` to a class in `application/`, or import
`httpClient` in a page, and run the build.

---

## 6. What it bought us, and what it costs

**Benefits we actually used:**

- **Fast, focused tests.** `QrSessionTest` covers every state transition in milliseconds, with
  no Spring and no Redis. `QrSessionServiceTest` stages a lost compare-and-set race with a
  20-line in-memory fake, which would be hard to stage against a real Redis.
- **Technology swaps stayed local.** Polling → SSE (slice 5) changed adapters and one hook; the
  use cases and the domain didn't change. Users in memory → Postgres would be one new
  `UserRepository` adapter.
- **Security rules live in one place.** "Only the scanner may approve" and "the code is valid
  once" are in `QrSession`, not spread over controllers.

**Costs:**

- **More files and indirection.** Interface + implementation per use case, DTO ↔ domain mapping
  at every edge. For a plain CRUD screen this is often overkill. It pays off here because the
  core (a state machine with races and security rules) is the hard part.
- **Manual wiring.** Use cases aren't `@Service`, so `UseCaseConfig` lists them explicitly.
- **Navigation.** "Where does this happen?" sometimes means following an interface to its
  implementation. IDE "go to implementation" helps.

**Rule of thumb:** apply the full ceremony where the rules are rich (the QR flow), and keep it
light where they aren't (the health check is barely more than a passthrough, kept layered mainly
as the simplest example).

---

## 7. Where we bent the textbook (on purpose)

| Choice | Why |
|---|---|
| JDK types (`Clock`, `MessageDigest`, `Instant`) in domain/application | Part of the language, not a framework |
| `QrSessionService` implements five use-case interfaces | Splitting into five classes adds files, not clarity; callers still depend on narrow interfaces |
| `adapter.common.redis.QrEventChannel` | Publisher (out) and subscriber (in) must share the Pub/Sub format but may not import each other |
| Inbound adapter → inbound adapter (`RedisQrEventSubscriber` → `QrStatusStreams`) | Both are "edge" code delivering to the browser; no business rule in between |
| `CheckRateLimitUseCase` used inside `AuthenticateUserService` | One use case using another inbound port within the application layer is fine |
| React hooks in the frontend `application/` | See [§4.5](#45-pragmatic-choices-on-the-frontend) |

---

## 8. Compared with classic Spring layering

```
Classic:                                 Here:
Controller → Service → Repository(JPA)   Controller → UseCase(interface) ← Service → Port(interface) ← Adapter
             ▲                                                                 business code owns the port
   business code imports persistence
```

The difference is the direction of the last arrow. In the classic style, changing the database
reaches into service code. Here the service owns the interface, and the database adapter adapts
to it.

---

## 9. Exercises (to make it stick)

1. **Swap an adapter.** Write `JdbcUserRepository implements UserRepository` (H2 is enough).
   Only `adapter.out.persistence` and wiring should change. Do the use-case tests still pass
   untouched?
2. **Add a port.** Add `SecurityEvents` (see [SECURITY.md, A1](SECURITY.md#2-audit-logging-a1-p1)) as an outbound
   port, call it from `QrSessionService`, implement it with SLF4J. Check that ArchUnit stays green.
3. **Break the rule on purpose.** Inject `StringRedisTemplate` into `QrSessionService` and watch
   `CleanArchitectureTest` fail. Then undo it.
4. **Frontend fake.** Create a `QrStatusStream` fake that emits PENDING → SCANNED → CONFIRMED on
   timers, and render `QrLoginPanel` with it, no backend running.
