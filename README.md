# ZenPulse (Wear OS)

Wellness heart-rate monitor for Galaxy Watch — the foundation for a stress-detection app.
Built with **Kotlin + Jetpack Compose for Wear OS** and **Google Health Services**.

> Wellness tool, not a medical device. It reports heart rate and (later) stress-related signals
> for personal well-being. It does not diagnose any condition.

## Week 1 — done

Minimal foundation that reads **real-time heart rate** on a real watch:

- Wear OS app skeleton (Kotlin + Compose for Wear OS).
- `HealthServicesManager` — streams live BPM from the `MeasureClient` as a cold `Flow`.
- Live BPM screen with sensor-availability status (off-wrist / acquiring / measuring).
- `BODY_SENSORS` runtime-permission flow and a capability check.

### Architecture

```
presentation/
  MainActivity.kt        Permission flow + Compose host
  HeartRateScreen.kt     Live BPM UI (Wear Compose Material)
  HeartRateViewModel.kt  UI state; owns the measurement coroutine
data/
  HealthServicesManager.kt  MeasureClient wrapper -> Flow<HeartRateMessage>
```

The sensor callback is registered only while a collector is active (`callbackFlow` + `awaitClose`),
so no measurement session leaks and the battery isn't drained when nothing is listening — this
matters because battery is a hard constraint later in the plan.

## Build & deploy to a real Galaxy Watch

1. Open the project in **Android Studio** (Hedgehog or newer).
2. On the watch: **Settings → About watch → Software → tap build number 7×** to enable
   Developer options, then enable **ADB debugging** and **Debug over Wi-Fi** (or Bluetooth).
3. Pair over Wi-Fi:
   ```bash
   adb connect <watch-ip>:5555
   ./gradlew :app:installDebug        # or press Run in Android Studio
   ```
4. Launch **ZenPulse** on the watch, tap **Grant** to allow body sensors, then **Start**.

Requires Wear OS 3+ (API 30+), which every Galaxy Watch 4 and newer runs.

## Command-line build

```bash
./gradlew assembleDebug        # builds app/build/outputs/apk/debug/app-debug.apk
```

## Roadmap (from the 7-week plan)

- **Week 2** — add IBI (for HRV) + accelerometer; log to file for offline analysis.
- **Week 3** — Wearable Data Layer: push watch → phone companion; NeuroKit2 offline research.
- **Week 4** — personalized baseline + rule-based stress detection ported to Kotlin.
- **Week 5** — alert back to the watch + guided-breathing intervention.
- **Week 6** — battery management (passive monitoring), onboarding, self-testing.
- **Week 7** — demo + external testers.
