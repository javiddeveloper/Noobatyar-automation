# Local vs. production: how each project switches

This repo has four runnable pieces — `backend`, `front_client`, `mobile_owner`,
`mobile_client` — and each one used to point at the **real production
server** by default, with no supported way to test against a local backend
without hand-editing source files. This doc describes the config that now
exists for each, so an agent (or a human) can spin up a fully local stack
(local backend + local front + local mobile app talking to each other) without
guessing, and can tell at a glance which mode any given running instance is in.

If you are an agent picking up local-dev work in this repo, read this file
first — it replaces trial and error.

## The core problem: three different "localhost"s

Whatever runs the backend (`python manage.py runserver`) binds to this
machine's loopback interface. Three different clients need three different
hostnames to reach that same loopback, because "localhost" means "this
device" to each of them, not "the machine running the backend":

| Client                                   | Hostname to reach the host machine |
|-------------------------------------------|-------------------------------------|
| Browser on this machine (front_client)    | `127.0.0.1` / `localhost`           |
| Android Emulator                          | `10.0.2.2`                          |
| iOS Simulator                             | `127.0.0.1` (shares the host's loopback) |
| Real device on the same Wi-Fi/LAN         | this machine's LAN IP (e.g. `192.168.1.x`) |

Every local-mode config below picks the right one of these for its client.
`backend/core/settings.py` already whitelists `127.0.0.1,10.0.2.2,localhost`
in `ALLOWED_HOSTS` by default, so you only need to worry about the *host*
side of each URL, not Django rejecting the request.

## backend (Django)

**File:** `backend/core/settings.py` reads everything through `os.getenv(...)`
already — it always did. What was missing was a way to set those env vars
without prefixing every command, and a way to keep the fix from disappearing
after a server restart or a `pkill`.

**Now:** `settings.py` calls `load_dotenv(BASE_DIR / '.env.local')` at import
time (see `core/settings.py:9-14`), using `python-dotenv`
(`requirements.txt` already listed it; it's now actually installed and used).
`load_dotenv`'s default (`override=False`) means **a real environment
variable always wins over the file** — so this changes nothing about how
production runs (docker-compose passes real env vars directly; there is no
`.env.local` in the container, and even if there were, it wouldn't override
anything already set).

- `backend/.env.local` — your actual local values. **Gitignored**
  (`.gitignore` already had `.env` / `.env.*` / `!.env.example` from a past
  incident where a server password leaked through committed scripts — this
  file rides on that same rule). Already created with working values (see
  below).
- `backend/.env.example` — committed template, no secrets, documents the same
  keys with comments. Copy it to `.env.local` on a fresh checkout.

Run it exactly like before, no env prefix needed:

```bash
cd backend && source .venv/bin/activate && python manage.py runserver 0.0.0.0:8000
```

(`0.0.0.0` rather than `127.0.0.1` so it also accepts the emulator's `10.0.2.2`
route and LAN connections from a real device — binding to `127.0.0.1` also
happens to work for the emulator case because of how its NAT redirects
loopback traffic, but `0.0.0.0` is the version that also covers a real device
on the LAN.)

### What's in `.env.local` and why

| Key | Local value | Why |
|---|---|---|
| `DEBUG` | `True` | Prod refuses to boot with the default `SECRET_KEY` unless `DEBUG=True` (settings.py:14) — this is what makes local runs work with no `SECRET_KEY` set at all. |
| `ZIBAL_MERCHANT_ID` | `zibal` | Zibal's public sandbox merchant. Every payment request succeeds and moves no real money. Production's real merchant id is baked into settings.py as the fallback default (`6a0d8775dc2e6664d8adf3fd`) and used automatically whenever this env var is absent — i.e. in the deployed container. |
| `CLIENT_WEB_URL` | `http://10.0.2.2:3000` | Where Zibal's checkout redirects the payer's browser after payment, and where deposit-payment links point. Must resolve from wherever that browser actually runs — see the client table above. **This is the one everyone forgets to change.** If it's still pointing at `https://app.noobatyar.ir` (the prod default), Zibal will redirect the payer straight into *production*, and the `payment-result` page that's supposed to call your local backend's verify endpoint never runs — the local transaction sits at `pending` forever and the app reports "پرداخت ناموفق". This bit us during this session before the env var was actually wired up. |
| `SITE_URL` | `http://127.0.0.1:8000` | Cosmetic base-URL setting, low stakes. |
| `OTP_DEV_CODE` | `123456` | Skips real SMS OTP send/verify. Forced empty whenever `DEBUG=False`, so this can never leak into prod even if the var were somehow set there. |
| `SMS_DEV_MODE` | `True` | Logs outgoing SMS instead of sending them — booking/reminder/subscription texts won't hit a real phone number. Also forced off outside `DEBUG`. |

Two more things you'll hit on a fresh local DB that aren't `.env` related:

- **Argon2 hasher**: `PASSWORD_HASHERS` uses Argon2 (settings.py:123-126) but
  `argon2-cffi` wasn't actually installed in `.venv` even though it's listed
  in `requirements.txt` (the venv predates that line). Login fails with
  `ValueError: Couldn't load 'Argon2PasswordHasher'` until you
  `pip install argon2-cffi==23.1.0 argon2-cffi-bindings==25.1.0` (or fix
  `requirements.txt`'s broken local wheel path for `altgraph` and run the full
  `pip install -r requirements.txt`).
- **Pending migrations**: switching branches locally can leave the sqlite db
  behind the code (e.g. `business.0012_...`, `0013_...` not yet applied) —
  run `python manage.py migrate` after every branch switch, before you assume
  a 500 is a real bug.
- **Seed data**: `backend/seed_test_data.py` creates a test owner
  (`09100000001` / `testpass123`) and five sample businesses. Run with
  `python manage.py shell < seed_test_data.py`.

### Testing an actual Zibal sandbox payment end-to-end

Calling the verify endpoint (`/api/accounting/payment-result`) directly with a
freshly-created `trackId` returns `"transaction failed"` — that's correct,
not a bug. Zibal's sandbox only marks a `trackId` as paid once you've actually
visited `payment_url` (`https://gateway.zibal.ir/start/<trackId>`) and clicked
through their fake-checkout success button. The real flow:

1. Trigger the purchase in the app → it opens `payment_url` in an external
   browser (`HomeViewModel` sends `HomeEvent.OpenUrl(...)`, not a WebView, so
   the app itself never sees the redirect chain).
2. Click the sandbox's success option on that page.
3. Zibal redirects to `CLIENT_WEB_URL + /home/payment-result?trackId=...`.
4. That page (`front_client/app/home/payment-result/page.tsx`) reads
   `NEXT_PUBLIC_API_URL` (already `http://127.0.0.1:8000` in
   `front_client/.env.local`) and calls the backend's verify endpoint itself.
5. Backend marks the transaction `success` and activates the subscription.
6. The page tries to deep-link back into the app
   (`noobatyar://payment/result?...`).

If step 3 lands on `app.noobatyar.ir` instead of your local front_client, go
back and fix `CLIENT_WEB_URL`.

## front_client (Next.js)

Already env-driven before this session — nothing new here, just documenting
it for consistency:

- `front_client/.env.local` (gitignored) — `NEXT_PUBLIC_API_URL=http://127.0.0.1:8000`
  for local. Production's value (`https://api.noobatyar.ir`) is set directly
  in `docker-compose.yml`'s `environment:` block for the deployed container,
  not read from a file.
- `npm --prefix front_client run dev` (or the `front_client` entry in
  `.claude/launch.json`) picks it up automatically — Next.js loads
  `.env.local` itself, no extra wiring needed.

## mobile_owner (Kotlin Multiplatform — Android + iOS)

**Before this session:** `composeApp/build.gradle.kts`'s `buildkonfig` block
hardcoded `BASE_URL = "https://api.noobatyar.ir"` for every build, on every
platform. Testing locally meant hand-editing that line and remembering to
revert it before committing — easy to forget, and there was no way to have
both a "local" and a "prod" build installed side by side to compare.

**Now (Android only):** two Gradle product flavors,
`local` and `prod`, each with its own `BuildKonfig.BASE_URL` /
`BOOKING_BASE_URL`:

```kotlin
// composeApp/build.gradle.kts
buildkonfig {
    packageName = "xyz.sattar.javid.proqueue"
    defaultConfigs {                    // used by "prod" and by iOS (no flavor concept there)
        buildConfigField(STRING, "BASE_URL", "https://api.noobatyar.ir")
        buildConfigField(STRING, "BOOKING_BASE_URL", "https://app.noobatyar.ir")
    }
    defaultConfigs("local") {           // matches the Android product flavor named "local"
        buildConfigField(STRING, "BASE_URL", "http://10.0.2.2:8000")
        buildConfigField(STRING, "BOOKING_BASE_URL", "http://10.0.2.2:3000")
    }
}

android {
    flavorDimensions += "env"
    productFlavors {
        create("prod") { dimension = "env" }
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"   // installs alongside prod, doesn't overwrite it
            versionNameSuffix = "-local"
        }
    }
}
```

Build/install whichever you need:

```bash
# Local (talks to 10.0.2.2:8000 — an emulator's alias for this machine)
./gradlew :composeApp:assembleLocalDebug
adb install -r composeApp/build/outputs/apk/local/debug/composeApp-local-universal-debug.apk
adb shell monkey -p xyz.sattar.javid.proqueue.local -c android.intent.category.LAUNCHER 1

# Production
./gradlew :composeApp:assembleProdDebug
adb install -r composeApp/build/outputs/apk/prod/debug/composeApp-prod-universal-debug.apk
adb shell monkey -p xyz.sattar.javid.proqueue -c android.intent.category.LAUNCHER 1
```

`xyz.sattar.javid.proqueue` (prod) and `xyz.sattar.javid.proqueue.local`
(local) are different application IDs, so both can be installed on the same
emulator/device at once without one overwriting the other.

On a **real device** on the same Wi-Fi (not the emulator), `10.0.2.2` doesn't
resolve — override `BASE_URL`/`BOOKING_BASE_URL` in the `local` flavor block
with this machine's LAN IP instead, temporarily.

**iOS has no flavor concept in this setup** — `defaultConfigs` (the prod
values) apply to every iOS build regardless. There's no per-environment
`.xcconfig` wired up for `BASE_URL` yet (`iosApp/Configuration/Config.xcconfig`
only carries `TEAM_ID` / bundle id / version, not the API host). For now,
testing the iOS Simulator against a local backend means temporarily editing
`defaultConfigs` in `build.gradle.kts` the old way (it affects iOS too since
iOS always reads the unflavored default) and reverting before committing. If
this becomes a recurring need, the fix would mirror the Android flavor
approach using buildkonfig's per-KMP-target config instead of Android
product flavors.

## mobile_client (Kotlin Multiplatform — customer-facing app)

**Not yet flavored.** `HttpClientFactory.android.kt` hardcodes
`host = "api.noobatyar.ir"` directly in the `DefaultRequest` block (no
buildkonfig involved at all here), with an inline comment pointing at
`10.0.2.2:8000` as the manual local override. If this app needs local testing
support, it should get the same `productFlavors` + `buildkonfig` treatment as
`mobile_owner` above — that work hasn't been done.

## Quick reference: what talks to what, locally

```
Android Emulator (mobile_owner "local" flavor)
        │  BASE_URL = http://10.0.2.2:8000
        ▼
Django dev server — 0.0.0.0:8000  (backend/.env.local)
        │  CLIENT_WEB_URL = http://10.0.2.2:3000  (Zibal redirects here after checkout)
        ▼
Next.js dev server — front_client, port 3000  (NEXT_PUBLIC_API_URL = http://127.0.0.1:8000)
        │  browser-side fetch back to the backend
        ▼
Django dev server (same instance as above)
```

## Checklist for spinning up a fully local stack

1. `backend/.env.local` exists (copy from `.env.example` if not).
2. `cd backend && source .venv/bin/activate && pip install argon2-cffi==23.1.0 argon2-cffi-bindings==25.1.0` if login throws the Argon2 `ValueError`.
3. `python manage.py migrate` (branch switches leave pending migrations behind).
4. `python manage.py runserver 0.0.0.0:8000`
5. `npm --prefix front_client run dev` (reads `front_client/.env.local` automatically).
6. Build/install the `local` flavor of `mobile_owner` if testing the Android app (see above).
7. Only if testing a real payment: confirm `CLIENT_WEB_URL` in `.env.local` matches whichever client's browser will actually run the checkout (emulator vs. simulator vs. LAN device — see the hostname table at the top).
