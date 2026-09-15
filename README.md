# ZenPulse

A Wear OS app for Galaxy Watch that notices when your body looks physiologically activated and
offers a minute of guided breathing. Built for young people living with anxiety and stress.

> **ZenPulse is a wellbeing tool, not a medical device.** It does not diagnose anything, it cannot
> predict a panic attack, and it is not emergency support. It reports signals from your own body,
> relative to your own baseline, and offers a breathing exercise. Nothing more.

Kotlin · Jetpack Compose for Wear OS · Health Services · Wearable Data Layer.

---

## How it works

```
 PPG sensor ──► heart rate ──► HRV proxy ─┐
                                          ├─► 10s window ──► personal baseline ──► detector ──► episode
 accelerometer ──► motion intensity ──────┘                    (learned, EWMA)      (z-scores)      │
                                                                                                    ▼
                                                                                    gentle nudge + breathing
```

**1. Windowing.** Raw sensors are far too noisy to decide anything on — the accelerometer alone
delivers ~50 samples a second. Ten seconds of samples are aggregated into one window, so a single
bad PPG reading can never move a decision on its own.

**2. A personal baseline.** Stress only means anything *relative to the individual*. A resting
heart rate of 85 is alarming for one person and normal for another, so an absolute threshold would
fire constantly for some users and never for others. ZenPulse learns the mean *and the spread* of
your heart rate and HRV during calm periods, using an exponentially weighted update that tracks
slow real change (fitness, medication, seasons) without letting one bad afternoon redefine normal.

**3. Detection in z-scores.** The detector asks the only question that generalises across people:
*how unusual is this, for you?* It combines heart-rate elevation and HRV suppression — weighted
towards HRV, which is the more specific marker of autonomic arousal — into a 0–1 activation score.

**4. Gates, not guesses.** Three rules stop the common false positives:

| Situation | What ZenPulse does |
|---|---|
| You're moving | Declines to assess. Exercise raises heart rate; no clever scoring recovers from conflating the two. |
| No HRV available | Scores on heart rate alone, capped below the top level. HR alone is too unspecific to justify "highly activated". |
| Baseline not learned yet | Says it's still learning, with progress, rather than guessing. |

**5. Hysteresis.** A level must persist across several windows before it is adopted — about 30
seconds of sustained signal to escalate. Nothing flickers, and one noisy reading can never trigger
an alert.

**6. The intervention.** Paced breathing with a prolonged exhale, plus a haptic pulse at every
phase change so it can be followed with your wrist down and eyes closed. At the most activated
level the app offers a *hold-free* pattern: breath-holding can intensify air hunger during panic,
so the highest level gets the gentlest exercise, not the most advanced one.

---

## Design decisions worth knowing

These are the choices that shaped the app more than any individual feature.

**Biased against false positives, everywhere.** For someone with anxiety, a watch that wrongly
announces they are panicking can *cause* the episode it claims to detect. Escalation is slower
than de-escalation, movement voids the reading entirely, and standard-deviation floors stop a
3 BPM rise in a very steady person from becoming a huge z-score.

**The copy never diagnoses.** Every string describes the body, not the person: "your body looks
activated", never "you are stressed". No red in the palette — the top of the scale is amber,
because a red screen reads as *something is wrong with you*.

**No scores, streaks, or weekly totals.** Turning self-regulation into a performance to be graded
is how a calming tool becomes another source of pressure. History shows what happened; it does not
keep score. The exercise ends with "however you feel now is fine."

**At most one nudge per 20 minutes.** An app that buzzes repeatedly at someone already activated
becomes another stressor.

**Privacy by default.** No `INTERNET` permission in either module — health data physically cannot
leave the devices. Raw session logging is off by default. Episode history is capped rather than
kept forever, because months of mental-health data on a watch that gets lost or handed to a repair
shop is a real risk.

**The app checks itself.** Each episode can be marked "yes, that matched" or "no, I was fine" in
one optional tap. A detector never checked against how the person actually felt will drift into
confident nonsense.

---

## Architecture

```
app/  (Wear OS)
  domain/            Pure Kotlin. No Android imports, fully unit-tested.
    SensorWindow         10s aggregate of the sensors
    WindowAggregator     raw samples -> windows (motion as SD, not mean)
    HrvEstimator         BPM -> synthetic intervals -> rolling RMSSD proxy
    BaselineTracker      EWMA mean + variance of your calm state
    StressDetector       z-scores, gating, hysteresis, sensitivity
    BreathingPattern     phase timelines for the exercises
    StressEpisode        a recorded episode + your verdict on it
  monitor/
    StressMonitor        the runtime core wiring sensors -> detection -> episodes -> alerts
  data/
    HealthServicesManager    live heart rate (MeasureClient)
    AccelerometerManager     motion (SensorManager)
    PassiveMonitorManager    background HR for baseline learning, low battery cost
    SettingsStore            settings + persisted baseline (DataStore)
    EpisodeStore             episode history (JSON)
    SessionLogger            optional raw CSV for research
    PhoneSyncManager         episode sync to the phone (Data Layer)
  service/MonitoringService  foreground service so monitoring survives screen-off
  presentation/              Compose UI: home, breathing, history, onboarding, settings
mobile/  (phone companion)
    EpisodeSyncService       receives syncs even when the app is closed
    MainActivity             readable history + CSV export
research/
    analyze_session.py       offline NeuroKit2 analysis of a pulled session
```

The domain layer has no Android dependencies **on purpose**: the logic that decides whether to
alert a vulnerable person should be testable on a plain JVM, not only on a watch.

---

## The HRV caveat — read this one

Health Services does not expose true beat-to-beat inter-beat intervals (IBI) on most Wear OS
hardware, including current Galaxy Watch generations, through its public API. It gives an
already-smoothed BPM.

So `HrvEstimator` computes a **proxy**: each BPM sample becomes a synthetic interval
(`60000 / bpm`), and the rolling root-mean-square of successive differences is tracked — the same
shape of computation as clinical RMSSD, fed lower-resolution input. It will *under-read* true
variability because it inherits the firmware's smoothing.

Treat it as a relative, within-person signal. Never compare it across people or devices, and never
read it as a clinical HRV measurement. Getting real beat-to-beat data on a Galaxy Watch means
integrating the Samsung Health Sensor SDK (separate from Health Services) for raw PPG — that is
the clear next step if the proxy proves too weak in testing.

`research/analyze_session.py` exists to interrogate this, not to admire it.

---

## Build and run

```bash
./gradlew :app:assembleDebug        # watch app
./gradlew :mobile:assembleDebug     # phone companion
./gradlew :app:testDebugUnitTest    # the domain test suite
```

Deploying to a real Galaxy Watch:

1. On the watch: **Settings → About watch → Software → tap build number 7×**, then enable
   **ADB debugging** and **Debug over Wi-Fi**.
2. `adb connect <watch-ip>:5555 && ./gradlew :app:installDebug`
3. Launch ZenPulse, allow body sensors, and wear it normally for ten minutes of calm so it can
   learn your baseline before it will say anything.

Requires Wear OS 3+ (API 30+) — every Galaxy Watch 4 and newer. The phone companion needs the
**same `applicationId` and signing key** as the watch app, which is why `:mobile` also uses
`com.zenpulse.wear`; the Wearable Data Layer only pairs apps that match.

## Tests

51 unit tests cover the whole domain layer — baseline learning and rejection rules, every
detection gate, hysteresis timing, the HR-only ceiling, breathing phase timelines, and window
aggregation.

```bash
./gradlew :app:testDebugUnitTest
```

## Research script

```bash
adb pull /data/data/com.zenpulse.wear/files/sessions/session_20260915_142233.csv
pip install -r research/requirements.txt
python research/analyze_session.py session_20260915_142233.csv --plot session.png
```

Reports signal coverage, heart-rate and motion summaries, and cross-checks the on-watch HRV proxy
against NeuroKit2's RMSSD computed from the same input.

---

## Status and what still needs verifying

Built: sensing, personal baseline, detection, episodes, alerts, guided breathing, history with
self-report, onboarding, settings, background baseline learning, foreground monitoring, phone sync
with CSV export, and the offline research script.

**Verified by actually running it:** the domain layer (51 passing JVM tests) and
`research/analyze_session.py` (executed end-to-end against a synthetic session, including the
NeuroKit2 comparison).

**Not yet compiled or run:** everything that touches the Android SDK. The environment this was
written in cannot reach Google's Maven repository, so the Android build could not be executed. The
Kotlin was written carefully but it has never been through a compiler. Expect to fix small things
on the first real build, and check these in particular:

- `PassiveListenerService` registration and its callback signature against the Health Services
  sample for `1.0.0-beta03`.
- The `health` foreground-service type on pre-Android-14 watches.
- Wear Compose component signatures (`ToggleChipDefaults.switchIcon`, `ScalingLazyColumn`) against
  the exact `1.3.1` artifacts.

**Before any release to real users**, two things are non-negotiable: the detector must be checked
against how people actually felt (the in-app self-report exists for exactly this), and the crisis
guidance in Settings must be localised — pointing to real, verified support services for the
region the app ships in, rather than the generic wording there now.

**Known limitation:** the HRV proxy, as described above. Everything downstream inherits its
quality.

## Licence

See [LICENSE](LICENSE).
