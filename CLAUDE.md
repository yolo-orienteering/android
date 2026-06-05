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
│   └── build.gradle.kts                           # namespace ch.seccom.omate, SDK versions
├── build.gradle.kts                               # Root plugins
├── settings.gradle.kts                            # Single :app module
└── gradle/libs.versions.toml                      # Version catalog
```

## How It Works

`MainActivity` (`app/src/main/java/ch/seccom/omate/MainActivity.kt`) creates a WebView with
JavaScript and DOM storage enabled, then:

- Loads the start URL into the WebView.
- Keeps navigation **inside the app** for internal domains (`o-mate.app`, `www.o-mate.app`,
  from the `internal_domains` array in `res/values/config.xml`); opens everything else in
  the system browser via `openExternally()`, which is wrapped in try/catch so a missing
  handler can never crash the app.
- Handles `webcal://` / `webcals://` calendar-subscription links by handing them to the
  system (calendar app), falling back to the `http(s)` feed if nothing handles `webcal`.
  The WebView cannot load these schemes, so opening one in-place would crash.
- Appends `o-mate-app/<versionCode>` to the WebView **User-Agent**. This drives the
  frontend's **force-update gate** (see the [root CLAUDE.md](../CLAUDE.md) capability
  handshake): bumping `versionCode` is the lever that lets the frontend require an update.
  Store deep-links (`play.google.com`) must keep opening externally — handled by
  `openExternally`. webcal handling landed in **versionCode 2** — keep in lockstep with iOS.
- Handles the hardware/gesture **back button** to navigate WebView history.

## Key Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Install on connected device/emulator
./gradlew assembleRelease      # Build release APK
./gradlew test                 # Unit tests
./gradlew connectedAndroidTest # Instrumented tests (needs a device)
```

## Keeping this doc current

When you make a code change that affects anything described here — stack/deps, SDK versions,
the start URL / internal-domain handling, build commands, or conventions — **update this
`CLAUDE.md` as part of the same change.** See [root CLAUDE.md](../CLAUDE.md) for the full policy.

## Conventions & Gotchas

- Package/namespace `ch.seccom.omate`; Activities in PascalCase; resources in snake_case.
- ⚠️ **Hardcoded dev URL:** `MainActivity` loads a LAN address (e.g.
  `http://localhost:3000/`) in development, with the production `https://o-mate.app`
  URL commented out. Switch this back before building a release. Consider build
  variants/flavors if you touch this often.
- `usesCleartextTraffic="true"` is enabled in the manifest to allow the `http://` dev URL.
- `res/values/strings.xml` still carries a legacy `app_name` of `yolo-android` — the
  shipping identity is o-mate (`ch.seccom.omate`).
- Tests are example boilerplate only; there is no meaningful test coverage yet.
