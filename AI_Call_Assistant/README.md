# AI Call Assistant

A Version-1-prototype Android call assistant with:
- Kotlin + Jetpack Compose Android app
- English/Hindi UI and speech
- Incoming-call screening role
- Multi-turn AI conversation with live transcript
- Call history with per-call summaries (generated when a call ends)
- FastAPI backend, SQLite database
- AI provider abstraction (works in DEMO mode with no API key)
- WebSocket endpoint for real-time telephony sessions

## What changed in this pass (bugs fixed)

The uploaded scaffold had a few things that would have stopped it from
working. These are fixed:

1. **`AICallScreeningService.kt` didn't compile.** `CallResponse.Builder()`
   needs to be `CallScreeningService.CallResponse.Builder()` since it's a
   nested class — fixed.
2. **Every chat message created a brand-new "call" row.** A 5-message
   conversation showed up as 5 unrelated entries in call history instead of
   one call. The backend now uses a session lifecycle
   (`/api/session/start` → repeated `/api/chat` → `/api/session/end`) and
   updates a single row per call, finishing with an AI-generated (or
   demo-mode) summary.
3. **No cleartext networking config.** Android 9+ blocks plain `http://`
   by default, which would have made every request to your local FastAPI
   backend fail silently. Added `network_security_config.xml` (dev-only —
   see the comments in that file for what to change before any real
   release).
4. **No launcher icon.** Added a simple vector-based adaptive icon so the
   app doesn't build with a placeholder/blank icon.
5. Added a "call history detail" screen so tapping a past call shows its
   real saved transcript and summary (previously history rows weren't
   openable in a meaningful way).
6. Added `buildTypes`, `proguard-rules.pro`, and a pinned Gradle wrapper
   version so the project matches normal Android Studio project conventions.

## Fastest possible "AI answers the call" flow (within Android's real limits)

The moment a call rings, `AICallScreeningService` now:
1. Immediately starts a backend session for that caller (so it already exists
   in call history, with a live transcript, before you've even picked up).
2. Fires a high-priority notification with an "Answer with AI" action.

Tapping that notification jumps straight into the AI conversation screen for
that exact call — no re-typing the number, no waiting. This is the fastest
AI-assisted handoff possible on stock Android without a telephony provider.

The app's own launcher icon now uses the profile photo you provided, cropped
into a circular badge on the app's brand-blue background
(`android-app/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png`).

## Important Android limitation (unchanged, and inherent to Android)

Android's public `CallScreeningService` API is for screening/identifying
calls — it does **not** give a third-party app a way to inject AI-generated
audio into an ordinary cellular call. That's a platform restriction, not
something fixable in app code. So this project has two call paths:

1. **Device path (this prototype):** detect/screen incoming cellular calls,
   show caller info, and let the user talk to the AI assistant inside the
   app itself (tap-to-talk, multi-turn, live transcript).
2. **AI telephony path (Version 2, needs a telephony/VoIP provider):** a
   carrier or VoIP provider (e.g. Twilio, Exotel, Plivo) forwards the actual
   call audio to the backend's `/ws/telephony` endpoint, which does
   STT → AI → TTS and streams audio back. This is the only way to get a
   caller talking to the AI automatically on a real phone call — implement
   the provider adapter in `backend/app/telephony.py`.

## Project layout

    android-app/     Android application (Kotlin, Jetpack Compose)
    backend/         FastAPI AI backend
    docs/            setup and architecture notes

## Get an APK without installing Android Studio (recommended)

This project includes a GitHub Actions workflow
(`.github/workflows/build-apk.yml`) that builds a real, installable
`app-debug.apk` automatically on GitHub's own servers — you don't need
Android Studio, a local SDK, or Gradle on your machine at all.

1. Create a new repository on [github.com](https://github.com) (free account
   works fine; the repo can be public or private).
2. Push this whole project folder to it:

       cd AI_Call_Assistant
       git init
       git add .
       git commit -m "Initial commit"
       git branch -M main
       git remote add origin https://github.com/<your-username>/<your-repo>.git
       git push -u origin main

3. That push automatically starts the build. Open your repo on github.com,
   click the **Actions** tab, and watch "Build Debug APK" run (takes about
   3-5 minutes).
4. When it finishes (green checkmark), open that run, scroll down to
   **Artifacts**, and download **app-debug-apk** — unzip it and you have
   `app-debug.apk`, ready to transfer to your phone and install.
5. Optional: if you tag a commit like `v1.0.0`
   (`git tag v1.0.0 && git push origin v1.0.0`), the workflow also attaches
   the APK to a GitHub Release, giving you a permanent, shareable download
   link instead of a one-off workflow artifact.

You can also trigger a build manually any time, without pushing new code:
repo -> **Actions** tab -> **Build Debug APK** -> **Run workflow**.

## Build the Android app locally (alternative to GitHub Actions)

You need **Android Studio** (free) if you'd rather build on your own machine
instead of using the GitHub Actions method above — a sandboxed build
environment like the one used to prepare this project can't download the
Android SDK/Gradle dependencies (they live on Google's Maven repos, which
aren't reachable there), so that compile step has to happen either on
GitHub's servers (previous section) or on your own machine via Android
Studio.

1. Install Android Studio (Ladybug or newer) + JDK 17.
2. Open the `android-app` folder as a project.
3. Let Gradle sync (Android Studio will offer to generate the Gradle
   wrapper jar/scripts automatically on first sync if they're missing —
   accept that prompt).
4. Run the `app` configuration on an emulator or a device running Android
   8.0+ (minSdk 26).

## Run the backend

Python 3.11+ recommended.

    cd backend
    python -m venv .venv
    source .venv/bin/activate      # Windows: .venv\Scripts\activate
    pip install -r requirements.txt
    cp .env.example .env           # Windows: copy .env.example .env
    uvicorn app.main:app --host 0.0.0.0 --port 8000

The backend works in **DEMO mode** with no AI API key — it uses simple
rule-based intent detection/replies so you can test the whole app end to
end before wiring up a real LLM.

## Configure the Android app's backend URL

- Android emulator: `http://10.0.2.2:8000/` (already the default)
- Physical phone: your PC's LAN IP, e.g. `http://192.168.1.10:8000/`

Change `API_BASE_URL` in `android-app/app/build.gradle.kts`, and if you use
a different LAN IP, also add it as a `<domain>` in
`android-app/app/src/main/res/xml/network_security_config.xml` (or just
leave the current dev config, which allows all cleartext HTTP for now).

## Backend API

    POST /api/session/start   {number, language}        -> {session_id}
    POST /api/chat            {session_id, message}      -> {reply, intent}
    POST /api/session/end     {session_id}                -> {summary, intent}
    GET  /api/calls                                       -> [call summaries]
    GET  /api/calls/{id}                                  -> full call detail (incl. transcript)
    WS   /ws/telephony                                    -> real-time provider bridge (dev/testing frame format)

## AI provider

Set these in `backend/.env` to use a real LLM instead of demo mode:

    AI_BASE_URL=
    AI_API_KEY=
    AI_MODEL=

The backend expects an OpenAI-compatible `/chat/completions` endpoint.

## Security

- Never put an AI API key in the Android APK — keep it on the FastAPI
  server only.
- The current `network_security_config.xml` allows plain HTTP everywhere,
  which is fine for local development but must be removed before any real
  release (deploy the backend behind HTTPS instead).

## What's implemented now

- Home dashboard with live call history (loading/error states)
- Incoming call screening service (fixed compile bug)
- Multi-turn AI conversation with a proper session lifecycle, live
  transcript, and end-of-call summary
- Call history detail screen (view past transcript + summary)
- Speech recognition (STT) and text-to-speech (TTS), Hindi + English
- FastAPI backend with SQLite persistence, DEMO mode, and pluggable LLM
- WebSocket real-time session endpoint for a future telephony provider
- Recording/telephony architecture hooks (`backend/app/telephony.py`)

## What still requires a telephony provider (Version 2)

Automatically answering a real cellular call and having the AI speak to the
caller live requires a telephony/VoIP media bridge — no ordinary
third-party Android app can inject audio into a normal cellular call. This
is documented in `docs/ARCHITECTURE.md`.
