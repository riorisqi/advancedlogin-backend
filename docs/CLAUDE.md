# AdvancedLogin — QR Code Login (learning project)

## About me
- Backend developer: Java + Spring Boot at work.
- Goal: learn frontend in depth (React + TypeScript) and learn how QR login works
  (like Steam, WhatsApp Web, Discord, Tokopedia/Shopee) by building it end to end.
- Prefer explanations of *why*, not just code. This is a learning project.

## Stack (decided)
- **Frontend:** Vite + React + TypeScript + Tailwind CSS (via `@tailwindcss/vite`)
  - QR rendering: `qrcode.react` (or similar)
- **Backend:** Java + Spring Boot, Spring Security
  - Redis for QR sessions (TTL-based expiry)
  - Real-time updates: SSE (`SseEmitter`) + Redis Pub/Sub fan-out
- **"Phone" client:** start as a mobile-friendly web page (logged in) that scans the QR
  with the camera; maybe React Native / Flutter later.

## How QR login works (agreed flow)
Actors: Desktop browser (not logged in), Phone app (already logged in), Backend.

1. **Desktop requests a QR session** → backend creates a random, single-use ID,
   status `PENDING`, short TTL (~60–120s), stored in Redis. Bound to the requesting
   browser (e.g. a cookie/nonce).
2. **Desktop renders the QR** (contains the ID, as a URL/deep link) and subscribes to
   status updates via SSE / WebSocket / polling.
3. **Phone scans** → sends the ID + its own auth token → status `SCANNED`.
   Desktop shows "Confirm on your phone".
4. **User approves on phone** → status `CONFIRMED` → backend issues a one-time code to
   the desktop over the real-time channel → desktop exchanges it for a session
   cookie / JWT → logged in.
- Other terminal states: `EXPIRED`, `REJECTED` (user declined), `CONSUMED`.

## Security points to cover
- Short expiry; single use; IDs must be unguessable (cryptographically random).
- Bind QR session to the requesting browser so copied QR content can't be redeemed
  by another browser.
- **QRLjacking defense:** phone shows the requesting device/browser, IP/location and
  time before the user approves.
- Phone never sends a password; it vouches with its existing authenticated session.
- One-time exchange code (not the token itself) pushed to the desktop.
- Rate limiting on QR session creation and status endpoints.

## Design
Full architecture & API design: [ARCHITECTURE.md](ARCHITECTURE.md)
(SSE for real-time, Spring Session + HttpOnly cookies, Redis Lua CAS for transitions).

## Code structure: clean architecture (both repos)
- Layers: domain ← application (use cases + ports) ← adapters/infrastructure & presentation
  ← config/app (composition root). Dependencies only point inwards.
- Backend: domain/ and application/ have no Spring imports; use cases are wired as @Bean in
  config/UseCaseConfig. Enforced by ArchUnit (`architecture/CleanArchitectureTest`).
- Frontend: domain/ has no React; presentation/ uses hooks from application/, never
  infrastructure/. Ports filled in app/DependenciesProvider. Enforced by oxlint
  `no-restricted-imports` overrides.
- Details: [ARCHITECTURE.md §9](ARCHITECTURE.md#9-project-structure--clean-architecture) and the study guide
  [CLEAN_ARCHITECTURE.md](CLEAN_ARCHITECTURE.md).

## Running locally
- Backend needs the `dev` profile (test accounts + dev Redis password): `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
- Redis (docker compose) is bound to 127.0.0.1 and requires `REDIS_PASSWORD` (dev default `advancedlogin-dev`).

## Security
Status, audit-logging plan and prioritized backlog: [SECURITY.md](SECURITY.md).

## Repo layout
- Two separate git repos under `AdvancedLogin/`: `backend/` (this one, also holds
  docs + docker-compose) and `frontend/`. The top-level folder is not a repo.
- Work in vertical slices: each milestone = backend endpoint first, then the
  frontend for it, ending with something clickable (see [ARCHITECTURE.md §10](ARCHITECTURE.md#10-build-order-milestones)).
