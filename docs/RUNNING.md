# Running and Testing AdvancedLogin

Step by step: set up once, start the app, then try a QR login, first with two browser windows
on one PC, then with a real phone and its camera.

Commands are written for **Windows (PowerShell)**, the main dev machine. On macOS/Linux use
`./mvnw` instead of `.\mvnw.cmd` and `export VAR=value` instead of `$env:VAR="value"`.

---

## 1. What runs where

```
 Phone (same Wi-Fi) ──https──┐
                             ▼
 Desktop browser ──────▶ Vite dev server :5173 ──/api──▶ Spring Boot :8080 ──▶ Redis :6379
                         (frontend/, bun)               (backend/, Java 21)    (Docker, localhost only)
```

- The browser (desktop *and* phone) only ever talks to **Vite on :5173**. Vite proxies `/api/*`
  to Spring Boot, so there's one origin, no CORS, and cookies behave as in production.
- Spring Boot and Redis are only reached from your PC, never directly from the phone.

---

## 2. One-time setup

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `& "$env:JAVA_HOME\bin\java" -version` |
| Docker Desktop | running | `docker ps` |
| bun | 1.x | `bun --version` |
| Git | any | `git --version` |

Clone both repos side by side:

```powershell
mkdir AdvancedLogin; cd AdvancedLogin
git clone https://github.com/riorisqi/advancedlogin-backend.git backend
git clone https://github.com/riorisqi/advancedlogin-frontend.git frontend
```

Install the frontend's packages once:

```powershell
cd frontend
bun install
cd ..
```

Maven doesn't need installing: `mvnw.cmd` downloads it on first use.

---

## 3. Start everything (3 terminals)

### Terminal 1: Redis

```powershell
cd backend
docker compose up -d
```

This starts Redis (password `advancedlogin-dev`, bound to `127.0.0.1:6379`) and RedisInsight
(http://localhost:5540). It keeps running in the background. Stop it later with
`docker compose down`.

### Terminal 2: backend

```powershell
cd backend
$env:JAVA_HOME = "D:\Dev\java lib collection\jdk-21.0.12.1+1"   # your JDK 21 folder
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Wait for `Started AdvancedLoginApplication`. The **`dev` profile is required**: it creates the
test accounts and supplies the dev Redis password.

**From IntelliJ instead:** open `backend/` and set the project SDK to JDK 21
(*File → Project Structure → Project → SDK*). Then activate the `dev` profile. How depends on
the edition:

- **IntelliJ IDEA Community** (no Spring Boot run configuration): run `AdvancedLoginApplication`
  once (green arrow next to `main`), then *Run → Edit Configurations…* → select it → in
  **Environment variables** add `SPRING_PROFILES_ACTIVE=dev`. (Alternative: *Modify options →
  Add VM options* → `-Dspring.profiles.active=dev`.)
- **IntelliJ IDEA Ultimate:** the *Spring Boot* run configuration has an **Active profiles**
  field: enter `dev`. (The Community steps above work in Ultimate too.)

Any of these does the same as `-Dspring-boot.run.profiles=dev` on the Maven command line.

### Terminal 3: frontend

```powershell
cd frontend
bun dev
```

It prints `Local: http://localhost:5173/`.

### Check it's all up

Open http://localhost:5173/status. **Backend: UP** and **Redis: UP** means all three pieces
talk to each other.

### Test accounts

| Username | Password |
|---|---|
| `alice` | `alice-dev-pass` |
| `bob` | `bob-dev-pass` |

Defined in `backend/src/main/resources/application-dev.yml`; they exist only with the `dev`
profile.

---

## 4. Try a QR login on one PC (no phone)

Cookies belong to a browser profile, so the "desktop" and the "phone" need two separate ones.
A normal window plus an incognito/private window is enough.

1. **Desktop:** in a normal window open **http://localhost:5173**. You see a QR code with a
   countdown, and a grey line `dev: live updates via SSE stream`.
2. **Phone:** open an **incognito/private** window at **http://localhost:5173/m**, and sign in as
   `alice` / `alice-dev-pass`. You land on "Hi, Alice".
3. On the desktop, right-click **dev: open approve link** (under the QR) → copy link address.
   Paste it into the incognito window. (Or on the phone page: **Scan QR code** → *Paste the
   link instead*.)
4. The phone page shows *Log in on a computer?* with the browser, OS, IP, location and a timer.
   Meanwhile the desktop QR switches to **Scanned: confirm the login on your phone**.
5. Tap **Approve**. Within a second the desktop shows **Welcome, Alice**: it's now signed in
   with its own session, and no password was typed on it.

Things to try next:
- **Reject** instead: the desktop shows *Login declined*.
- Wait 90 s without scanning: the QR refreshes by itself (3 times, then *Reload QR code*).
- Paste the approve link into a *third* profile signed in as `bob` after alice scanned:
  *Can't use this QR code*.
- More attacks to try by hand: [ARCHITECTURE.md §11](ARCHITECTURE.md#11-attack-yourself).

---

## 5. Test with a real phone (camera scanner)

### Why this needs extra steps

- The phone must reach your PC over the network, so it can't use `localhost`.
- Browsers only allow the **camera** on secure origins (`https://` or `localhost`), so the dev
  server must serve HTTPS. `bun run dev:lan` does both, with a self-signed certificate.

### Steps

1. **Same network.** The phone and the PC must be on the same router: phone on its Wi-Fi, PC on
   the same Wi-Fi *or* a LAN cable to that router (both work). Guest Wi-Fi networks often block
   devices from seeing each other ("client isolation"), so use the main one.

2. **Let Windows accept connections from the phone** (once). Windows blocks incoming
   connections on networks marked **Public**, and a LAN cable or new Wi-Fi is often Public by
   default.
   - Check: `Get-NetConnectionProfile` → `NetworkCategory` should be **Private** for your
     home network. If it says Public: *Settings → Network & internet → Ethernet* (or *Wi-Fi*) →
     your network → **Network profile type: Private**.
   - Allow the dev server's port, in an **Administrator** PowerShell:

     ```powershell
     New-NetFirewallRule -DisplayName "AdvancedLogin dev (5173)" -Direction Inbound -Protocol TCP -LocalPort 5173 -Action Allow -Profile Private
     ```

     (Or accept the "allow bun on Private networks" prompt if Windows shows one.) Remove it later
     with `Remove-NetFirewallRule -DisplayName "AdvancedLogin dev (5173)"`.

3. **Start the frontend in LAN mode** (instead of `bun dev`; Redis and the backend stay as in [§3](#3-start-everything-3-terminals)):

   ```powershell
   cd frontend
   bun run dev:lan
   ```

   It prints addresses like:

   ```
   ➜  Local:   https://localhost:5173/
   ➜  Network: https://192.168.x.x:5173/     Ethernet (or Wi-Fi)
   ➜  Network: https://172.x.x.x:5173/       vEthernet (WSL ...)
   ```

   Use the **192.168.x.x** one (the adapter connected to your router). **Never the `172.x.x.x`
   vEthernet/WSL one**: that's a virtual network that only exists *inside* your PC, so the phone
   can't reach it. Not sure? Run `ipconfig` and take the IPv4 address of the adapter connected
   to your router.

   LAN mode has **no hot reload**: Bun crashes on WebSocket connections over HTTPS, so the
   WebSocket Vite uses for live updates is switched off (see `vite.config.ts`). After a code
   change, refresh the page. The browser console shows one harmless
   `[vite] failed to connect to websocket` message. (Normal `bun dev` keeps hot reload.)

4. **Desktop:** on the PC, open **`https://192.168.x.x:5173`**, the network address, *not*
   localhost. The QR's link is built from the address the desktop uses, and the phone can't open
   "localhost". Accept the certificate warning (*Advanced → Continue/Proceed*). You see the QR.

5. **Phone:** open **`https://192.168.x.x:5173/m`** in the phone's browser and accept the
   certificate warning once:
   - **Android (Chrome):** *Advanced → Proceed to 192.168.x.x (unsafe)*.
   - **iPhone (Safari):** *Show Details → visit this website → Visit Website*.

   Sign in as `alice` / `alice-dev-pass`.

6. **Scan:** tap **Scan QR code**, allow camera access, and point the phone at the QR on your
   PC screen. Hold it 20–40 cm away and avoid glare. It opens the approve page automatically.

7. **Check and approve:** the phone shows the desktop's browser, OS, **the PC's LAN IP**,
   *Location: Local network* and a timer. Tap **Approve**: the PC shows **Welcome, Alice**.

You can also scan with the phone's **built-in camera app**: the QR contains a normal
`https://192.168.x.x:5173/m/approve/...` link, so it opens the same approve page.

### Make the certificate warning go away (optional)

The warning appears because the dev certificate is self-signed. To get a trusted certificate,
use [mkcert](https://github.com/FiloSottile/mkcert): `mkcert -install`, create a certificate for
your LAN IP, install mkcert's root CA on the phone, and point Vite's `server.https` at the files
instead of `basicSsl()`. More setup; only worth it if you test on phones a lot.

---

## 6. Run the automated tests

```powershell
# backend: unit, architecture and integration tests (Docker must be running)
cd backend
.\mvnw.cmd test

# frontend: unit tests, lint (includes the architecture rules), type-check + build
cd ..\frontend
bun test
bun run lint
bun run build
```

The backend integration tests start their own throwaway Redis container (Testcontainers), so
they don't touch your dev Redis.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `/status` shows **Backend DOWN** (502) | backend not running, or still starting | start Terminal 2; wait for `Started …` |
| **Redis DOWN** | Docker not running / container stopped | start Docker Desktop, `docker compose up -d` |
| Backend log: `NOAUTH Authentication required` | backend started without the `dev` profile | add `-Dspring-boot.run.profiles=dev`; in IntelliJ set env var `SPRING_PROFILES_ACTIVE=dev` (see [§3](#3-start-everything-3-terminals)); or set `REDIS_PASSWORD` |
| "Invalid username or password" for alice | `dev` profile not active, so no test accounts | as above |
| "Too many sign-in attempts. Try again in N seconds" | login rate limit (5 per username per minute) | wait, or clear counters (below) |
| Backend won't start: `Unable to establish loopback connection` | Java NIO + Windows temp folder | add VM option `-Djdk.net.unixdomain.tmpdir=<any writable folder>` |
| Port 8080 in use | another backend (e.g. IntelliJ) already running | stop it, or run a 2nd on `--server.port=8090` and start the frontend with `$env:BACKEND_URL="http://localhost:8090"; bun dev` |
| Phone: page never loads / times out | network marked *Public*, no firewall rule, wrong IP, or guest Wi-Fi | set the network to *Private* and add the firewall rule ([§5](#5-test-with-a-real-phone-camera-scanner) step 2); use the 192.168.x.x address, never 172.x.x.x (WSL); same router |
| `bun run dev:lan` crashes: `TypeError: undefined is not an object (evaluating 'socket')` | Bun bug with WebSockets over HTTPS (Vite's hot-reload socket) | fixed in `vite.config.ts` (LAN mode disables the WebSocket); pull the latest frontend |
| Phone: "The camera only works over HTTPS" | opened `http://` | use `bun run dev:lan` and the `https://` address |
| Phone: "Camera access was blocked" | permission denied earlier | browser site settings → Camera → Allow, then reload |
| Phone: "That QR code points to http://localhost:5173" | desktop opened via localhost | open the desktop at the `https://192.168.x.x:5173` address too |
| Desktop line says `polling (fallback)` | the SSE stream couldn't be used | works anyway; check the browser console/network for `/events` |

Clear the rate-limit counters (dev only):

```powershell
docker exec -e REDISCLI_AUTH=advancedlogin-dev backend-redis-1 sh -c "redis-cli --scan --pattern 'rl:*' | xargs -r redis-cli DEL"
```

---

## 8. Stopping

- Frontend / backend terminals: `Ctrl+C`.
- Redis: `cd backend; docker compose down` (data is in-memory only; nothing is lost that matters).
