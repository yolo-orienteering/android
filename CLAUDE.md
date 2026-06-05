# O-Mate Android — Native WebView Wrapper

## Project Overview

Native Android wrapper for **o-mate**, a Swiss orienteering sports app. This is a
**thin WebView shell** — it loads the Nuxt frontend (`https://o-mate.app` in
production) and adds native navigation and external-link handling. Essentially all
product logic lives in the [frontend](../frontend/); see the [root CLAUDE.md](../CLAUDE.md)
for the overall architecture.

## Tech Stack

- **Language:** Kotlin 2.0.21 (no Java sources)
- **UI:** Traditional XML layouts (ConstraintLayout) — **no Jetpack Compose**
- **Build:** Gradle 8.13 (Kotlin DSL), Android Gradle Plugin 8.13.2, Java 11 target
- **Min SDK 24** (Android 7.0) · **Target/Compile SDK 35** (Android 15)
- **Dependencies:** intentionally minimal — `core-ktx`, `appcompat`, Material 3,
  `activity`, `constraintlayout`. No networking, DI, image-loading, or state libraries —
  the WebView does the work.

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/ch/seccom/omate/MainActivity.kt   # The whole app: WebView wrapper
│   │   ├── res/layout/activity_main.xml           # Single ConstraintLayout + WebView
│   │   ├── res/values/strings.xml                 # app_name, start_page
│   │   ├── res/values/config.xml                  # internal_domains string-array
│   │   ├── res/values/themes.xml                  # Material 3 DayNight theme
│   │   └── AndroidManifest.xml                    # MainActivity + INTERNET permission
│   ├── src/test/...                               # JUnit boilerplate (no real tests yet)
│   ├── src/androidTest/...                         # Espresso boilerplate
│   └── build.gradle.kts                           # namespace, SDK versions, per-env START_URL
├── build.gradle.kts                               # Root plugins
├── settings.gradle.kts                            # Single :app module
└── gradle/libs.versions.toml                      # Version catalog
```

## How It Works

`MainActivity` (`app/src/main/java/ch/seccom/omate/MainActivity.kt`) creates a WebView with
JavaScript and DOM storage enabled, then:

- Loads the start URL (`BuildConfig.START_URL`) into the WebView. The URL is environment-
  specific: **debug builds → localhost dev** (`http://10.0.2.2:3000/` by default),
  **release builds → production** (`https://o-mate.app`). See the Environments section below.
- Keeps navigation **inside the app** for internal domains (`o-mate.app`, `www.o-mate.app`,
  from the `internal_domains` array in `res/values/config.xml`); opens everything else in
  the system browser via `openExternally()`, which is wrapped in try/catch so a missing
  handler can never crash the app.
- Opens `webcal://` / `webcals://` **and any `.ics` URL** in a calendar app via
  `openCalendarSubscription()`. Android has **no native `webcal://` handler** (unlike iOS),
  so a plain `ACTION_VIEW` just makes a browser download the file. Instead it tries (1) a
  calendar app that registers `webcal://`, then (2) **Google Calendar's "add by URL"**
  (`calendar.google.com/calendar/render?cid=…`) — the standard Android way to subscribe.
  ⚠️ The feed must be reachable by the calendar provider's servers to sync, so a **LAN IP
  opens Calendar but can't be fetched** — test real subscription against a public URL.
- Appends `o-mate-app/<versionCode>` to the WebView **User-Agent**. This drives the
  frontend's **force-update gate** (see the [root CLAUDE.md](../CLAUDE.md) capability
  handshake): bumping `versionCode` is the lever that lets the frontend require an update.
  Store deep-links (`play.google.com`) must keep opening externally — handled by
  `openExternally`. webcal handling landed in **versionCode 2** — keep in lockstep with iOS.
- Handles the hardware/gesture **back button** to navigate WebView history.
- **Survives recreation without going blank:** the activity declares
  `android:configChanges=...` so rotation/UI-mode changes keep the live WebView instead of
  recreating it; for genuine recreation (process death after a long time backgrounded) it
  `saveState`/`restoreState`s the WebView and falls back to a fresh load. The old
  `if (savedInstanceState == null) loadUrl` guard alone produced a blank screen on rotation.
- **Deep links / Android App Links:** an `https://o-mate.app` (or `www.o-mate.app`) link
  opened from mail or another browser launches the app (intent-filter with `autoVerify`,
  `launchMode="singleTop"`); the incoming URL is loaded in `onCreate`/`onNewIntent`.
  Auto-verification needs `/.well-known/assetlinks.json` on the domain — scaffolded at
  `frontend/public/.well-known/assetlinks.json` (replace the SHA-256 fingerprint).
  The filter matches **all** `o-mate.app` paths (no per-route allow-list to maintain). The
  calendar feed is **not** excluded by path — it lives on the **API host**
  (`admin.o-mate.app`, built from `apiUrl`), a different host this filter doesn't match, so
  webcal/.ics links are never claimed. In-app `.ics`/`webcal` taps are also intercepted in
  `handleUrl`. **Invariant:** keep the calendar feed off the `o-mate.app` host (it already is).

## Environments (start URL)

The page the WebView loads is `BuildConfig.START_URL`, set per build type in
`app/build.gradle.kts` — so dev vs. production is automatic, no code edits:

| Build type | Default `START_URL`        | Used by                                    |
| ---------- | -------------------------- | ------------------------------------------ |
| `debug`    | `http://10.0.2.2:3000/`    | Run ▶ in Android Studio / `installDebug`   |
| `release`  | `https://o-mate.app`       | `assembleRelease` / `bundleRelease`        |

`10.0.2.2` is the emulator's alias for the host machine's loopback (where the Nuxt dev
server runs). Override either value without editing the build file via a Gradle property or
environment variable — e.g. for a **physical device** on your LAN:

```bash
./gradlew installDebug -PdevStartUrl=http://192.168.1.19:3000/
# or
DEV_START_URL=http://192.168.1.19:3000/ ./gradlew installDebug
# release equivalents: -PprodStartUrl=... / PROD_START_URL=...
```

(`buildFeatures { buildConfig = true }` must stay enabled for `BuildConfig.START_URL`.)

## Key Commands

```bash
./gradlew assembleDebug        # Build debug APK (dev start URL)
./gradlew installDebug         # Install on connected device/emulator
./gradlew assembleRelease      # Build release APK (production start URL)
./gradlew test                 # Unit tests
./gradlew connectedAndroidTest # Instrumented tests (needs a device)
```

## Keeping this doc current

When you make a code change that affects anything described here — stack/deps, SDK versions,
the start URL / internal-domain handling, build commands, or conventions — **update this
`CLAUDE.md` as part of the same change.** See [root CLAUDE.md](../CLAUDE.md) for the full policy.

## Conventions & Gotchas

- Package/namespace `ch.seccom.omate`; Activities in PascalCase; resources in snake_case.
- The start URL is **not** hardcoded anymore — it comes from `BuildConfig.START_URL` per
  build type (see Environments above). Debug → dev, release → production, automatically.
- `usesCleartextTraffic="true"` is enabled in the manifest to allow the `http://` dev URL.
- `res/values/strings.xml` still carries a legacy `app_name` of `yolo-android` — the
  shipping identity is o-mate (`ch.seccom.omate`).
- Tests are example boilerplate only; there is no meaningful test coverage yet.
