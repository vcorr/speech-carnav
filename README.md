# FinNav

Say a Finnish destination in the car and Google Maps starts navigating — no touching.
*"Hey Google, open FinNav"* → *"Kuuntelen…"* → *"Hämeenkatu 10, Tampere"* → route on the Android Auto screen.

Status: **Phase 0** — a throwaway spike app that tests whether the idea works on a real phone and car.
See [PLAN.md](PLAN.md) for the design and [docs/SPIKES.md](docs/SPIKES.md) for the test protocol.

## Modules

| Module | What | Needs |
|---|---|---|
| `core` | Pure Kotlin logic: URI building, transcript scoring, corpus, statistics, run log | JDK 17+ |
| `app` | The Android app (Phase 0: `spike/`) | Android SDK |

`settings.gradle.kts` includes `app` only when an Android SDK is configured, so `core` builds and tests anywhere.

## Install on the phone (no computer needed)

Every push builds the app on GitHub (`.github/workflows/build-apk.yml`) and publishes it as the
**spike-latest** pre-release.

1. On the phone, open the repository on GitHub → **Releases** → *FinNav spike (latest build)*.
2. Download `finnav-spike.apk` and open it. The first time, Android asks you to allow installing
   apps from your browser (or Files): allow it.
3. Open **FinNav** once, unlocked, and grant the microphone permission.

Later builds install over the previous one (they share a fixed debug signing key).

## Build and install with Android Studio

1. Open the project in Android Studio and let it create `local.properties` (SDK path). Accept any
   suggested Android Gradle Plugin upgrade.
2. Connect the phone with USB debugging enabled and run the `app` configuration. Installing this way
   (ADB) avoids the store requirements for sideloaded apps.
3. Open **FinNav** once, unlocked, and grant the microphone permission.

Command line:

```bash
./gradlew :core:test
```

```bash
./gradlew :app:installDebug
```

## Privacy

No audio is stored. Recognised text and the run log stay on the phone until you share them yourself.
