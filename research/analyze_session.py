#!/usr/bin/env python3
"""Offline analysis of a ZenPulse session CSV pulled off the watch.

Usage:
    adb pull /data/data/com.zenpulse.wear/files/sessions/session_20260915_142233.csv
    python analyze_session.py session_20260915_142233.csv --plot out.png

What this is for
----------------
The watch computes an HRV *proxy* from smoothed BPM, because Health Services does not expose
true beat-to-beat intervals on most Wear OS hardware. That proxy is the weakest link in the whole
detection chain, so this script exists to interrogate it rather than to admire it:

  1. It recomputes RMSSD from the same input using NeuroKit2, an established library, and reports
     how closely the on-watch proxy tracks it. Agreement here only proves the watch implements the
     formula correctly — it says nothing about whether the underlying signal is good enough.

  2. It reports what fraction of the session had a usable sensor signal at all. A session that was
     70% off-wrist cannot support any conclusion, and that is worth knowing before drawing one.

What it is not
--------------
This cannot validate the proxy against ground truth. Doing that needs simultaneous recording from
a chest strap (Polar H10 or equivalent) exporting real RR intervals, compared against the watch
over the same window. Until someone runs that comparison, treat every HRV number produced here —
and in the app — as a relative signal within one person, never as a clinical measurement.
"""

import argparse
import sys

import numpy as np
import pandas as pd

EXPECTED_COLUMNS = {"elapsed_ms", "bpm", "availability", "hrv_proxy_ms"}


def load_session(path: str) -> pd.DataFrame:
    """Read a session CSV and keep only rows where the sensor actually had a signal."""
    frame = pd.read_csv(path)

    missing = EXPECTED_COLUMNS - set(frame.columns)
    if missing:
        raise SystemExit(f"{path} is missing expected columns: {sorted(missing)}")

    total_rows = len(frame)
    if total_rows == 0:
        raise SystemExit(f"{path} contains no rows")

    # AVAILABLE is the only state where a BPM value means anything. Anything else is the watch
    # reporting a number while admitting it could not see the wearer.
    usable = frame[(frame["availability"] == "AVAILABLE") & frame["bpm"].notna()].copy()
    coverage = len(usable) / total_rows

    print(f"Session: {path}")
    print(f"  Duration:       {frame['elapsed_ms'].max() / 60000:.1f} min")
    print(f"  Usable signal:  {coverage:.0%} of samples ({len(usable)}/{total_rows})")

    if coverage < 0.5:
        print("  WARNING: under half this session had a usable signal. Conclusions are unsafe.")
    if usable.empty:
        raise SystemExit("No usable samples — nothing to analyse.")

    return usable


def summarise_heart_rate(usable: pd.DataFrame) -> None:
    bpm = usable["bpm"]
    print("\nHeart rate")
    print(f"  mean {bpm.mean():.1f} bpm, sd {bpm.std():.1f}, range {bpm.min():.0f}-{bpm.max():.0f}")


def summarise_motion(usable: pd.DataFrame) -> None:
    """Motion intensity, computed the same way the watch does: SD of accelerometer magnitude."""
    axes = {"accel_x", "accel_y", "accel_z"}
    if not axes.issubset(usable.columns) or usable[list(axes)].isna().all().all():
        print("\nMotion: no accelerometer data in this session.")
        return

    magnitude = np.sqrt(
        usable["accel_x"] ** 2 + usable["accel_y"] ** 2 + usable["accel_z"] ** 2
    )
    # Rolling SD over ~10s of samples, matching the app's window length at its 5 Hz log rate.
    rolling_sd = magnitude.rolling(window=50, min_periods=10).std()

    print("\nMotion (SD of accelerometer magnitude)")
    print(f"  median {rolling_sd.median():.2f} m/s^2, 95th pct {rolling_sd.quantile(0.95):.2f}")
    still_fraction = (rolling_sd < 0.6).mean()
    print(f"  {still_fraction:.0%} of the session was still enough to train the baseline")


def compare_hrv_against_neurokit(usable: pd.DataFrame) -> None:
    """Recompute RMSSD from the same BPM series with NeuroKit2 and compare to the watch's proxy."""
    try:
        import neurokit2 as nk
    except ImportError:
        print("\nHRV: neurokit2 not installed — skipping (pip install -r requirements.txt)")
        return

    bpm = usable["bpm"].to_numpy(dtype=float)
    bpm = bpm[bpm > 0]
    if len(bpm) < 10:
        print("\nHRV: too few samples to compare.")
        return

    # The watch's synthetic interval: one "beat interval" implied by each smoothed BPM reading.
    # This is the same transformation HrvEstimator applies, reproduced here so the comparison is
    # of implementations, not of inputs.
    rr_intervals = 60_000.0 / bpm

    watch_rmssd = np.sqrt(np.mean(np.diff(rr_intervals) ** 2))

    try:
        # NeuroKit2 takes beat *positions*, not intervals. At 1000 Hz one sample is one
        # millisecond, so the cumulative sum of the RR intervals is exactly the peak index series.
        peaks = np.cumsum(rr_intervals).astype(int)
        indices = nk.hrv_time(peaks=peaks, sampling_rate=1000)
        neurokit_rmssd = float(indices["HRV_RMSSD"].iloc[0])
    except Exception as error:  # noqa: BLE001 - library surface varies across versions
        print(f"\nHRV: NeuroKit2 comparison failed ({error}). Reporting the direct value only.")
        print(f"  RMSSD over synthetic intervals: {watch_rmssd:.1f} ms")
        return

    print("\nHRV proxy check (both computed from the same smoothed BPM series)")
    print(f"  Direct RMSSD:    {watch_rmssd:.1f} ms")
    print(f"  NeuroKit2 RMSSD: {neurokit_rmssd:.1f} ms")

    if watch_rmssd > 0:
        difference = abs(neurokit_rmssd - watch_rmssd) / watch_rmssd
        print(f"  Difference:      {difference:.1%}")
    print("  Agreement here only confirms the formula matches. It does NOT validate the signal —")
    print("  that needs a chest-strap recording compared over the same window.")

    if usable["hrv_proxy_ms"].notna().any():
        logged = usable["hrv_proxy_ms"].dropna()
        print(f"  Logged on-watch proxy: mean {logged.mean():.1f} ms, sd {logged.std():.1f}")


def plot_session(usable: pd.DataFrame, output_path: str) -> None:
    try:
        import matplotlib

        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("\nPlot skipped: matplotlib not installed.")
        return

    minutes = usable["elapsed_ms"] / 60000.0
    figure, (hr_axis, hrv_axis) = plt.subplots(2, 1, sharex=True, figsize=(10, 6))

    hr_axis.plot(minutes, usable["bpm"], linewidth=1)
    hr_axis.set_ylabel("BPM")
    hr_axis.set_title("ZenPulse session")

    hrv_axis.plot(minutes, usable["hrv_proxy_ms"], linewidth=1, color="tab:orange")
    hrv_axis.set_ylabel("HRV proxy (ms)")
    hrv_axis.set_xlabel("Minutes")

    figure.tight_layout()
    figure.savefig(output_path, dpi=120)
    print(f"\nPlot written to {output_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("csv", help="Session CSV pulled from the watch")
    parser.add_argument("--plot", metavar="PNG", help="Also write a plot to this path")
    args = parser.parse_args()

    usable = load_session(args.csv)
    summarise_heart_rate(usable)
    summarise_motion(usable)
    compare_hrv_against_neurokit(usable)

    if args.plot:
        plot_session(usable, args.plot)

    return 0


if __name__ == "__main__":
    sys.exit(main())
