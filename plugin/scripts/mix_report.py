"""Mix report card for the Wigout Studio engineer.

Loudness, peaks, dynamics, spectral balance, and stereo image of a
rendered mix. Estimates with stated limits - advisory, not arbitration;
the user's ears decide.
"""
import argparse
import json
import math
import sys

import numpy as np

TARGETS = {
    "streaming": {"lufs_i": -14.0, "true_peak_dbtp": -1.0},
    "club": {"lufs_i": -7.0, "true_peak_dbtp": -0.5},
    "none": None,
}

# Same band edges as sound_analysis.py so the two roles' numbers line up.
BAND_EDGES = {"low": (0, 250), "mid": (250, 2000), "high": (2000, None)}

LIMITS = [
    "true peak is a 4x-oversampling estimate, not BS.1770 filter-exact",
    "no LRA measurement (needs ffmpeg ebur128)",
    "channels beyond the first two are excluded from loudness and stereo metrics (true peak scans all channels)",
]


def _db(x):
    return 20.0 * np.log10(max(float(x), 1e-9))


def analyze(path, target="streaming"):
    import librosa  # lazy so --help stays fast
    import pyloudnorm as pyln
    import soundfile as sf

    if target not in TARGETS:
        raise ValueError(f"unknown target '{target}' (use streaming|club|none)")
    data, sr = sf.read(path, always_2d=True)
    if data.shape[0] == 0:
        raise ValueError("audio file is empty")
    if data.shape[0] < int(0.5 * sr):
        raise ValueError("audio too short for loudness measurement (<0.5s)")

    stereo = data.shape[1] >= 2
    mono = data.mean(axis=1)

    meter = pyln.Meter(sr)
    lufs_i = float(meter.integrated_loudness(data[:, :2] if stereo else mono))
    if not math.isfinite(lufs_i):
        raise ValueError("audio is silent or below the loudness gate - nothing to report on")

    sample_peak = float(np.max(np.abs(data)))
    oversampled = librosa.resample(np.ascontiguousarray(data.T), orig_sr=sr, target_sr=sr * 4)
    true_peak = float(np.max(np.abs(oversampled)))

    rms = float(np.sqrt(np.mean(mono ** 2)))
    loudness = {
        "lufs_integrated": round(lufs_i, 2),
        "sample_peak_dbfs": round(_db(sample_peak), 2),
        "true_peak_est_dbtp": round(_db(true_peak), 2),
        "crest_db": round(_db(sample_peak) - _db(rms), 2),
    }

    S = np.abs(librosa.stft(mono)) ** 2
    freqs = librosa.fft_frequencies(sr=sr)
    total = float(np.sum(S)) or 1e-9
    bands = {}
    for name, (lo, hi) in BAND_EDGES.items():
        mask = (freqs >= lo) if hi is None else ((freqs >= lo) & (freqs < hi))
        bands[name] = round(float(np.sum(S[mask])) / total, 3)

    stereo_block = None
    if stereo:
        left, right = data[:, 0], data[:, 1]
        mid = (left + right) / 2.0
        side = (left - right) / 2.0
        mid_energy = float(np.sum(mid ** 2)) or 1e-9
        denom = float(np.sqrt(np.sum(left ** 2) * np.sum(right ** 2))) or 1e-9
        rms_stereo = float(np.sqrt(np.mean((left ** 2 + right ** 2) / 2.0)))
        rms_mid = float(np.sqrt(np.mean(mid ** 2)))
        stereo_block = {
            "width": round(float(np.sum(side ** 2)) / mid_energy, 3),
            "correlation": round(float(np.sum(left * right)) / denom, 3),
            "mono_drop_db": round(_db(rms_stereo) - _db(rms_mid), 2),
        }

    target_block = None
    spec = TARGETS[target]
    if spec is not None:
        lufs_delta = round(lufs_i - spec["lufs_i"], 2)
        tp_margin = round(spec["true_peak_dbtp"] - loudness["true_peak_est_dbtp"], 2)
        notes = []
        if lufs_delta > 1.0:
            notes.append(f"louder than {target} target by {lufs_delta} LU")
        elif lufs_delta < -1.0:
            notes.append(f"quieter than {target} target by {abs(lufs_delta)} LU")
        if tp_margin < 0:
            notes.append(f"true-peak estimate exceeds {spec['true_peak_dbtp']} dBTP ceiling")
        target_block = {
            "name": target,
            "lufs_delta": lufs_delta,
            "true_peak_margin_db": tp_margin,
            "notes": notes,
        }

    return {
        "loudness": loudness,
        "spectrum": {"bands": bands},
        "stereo": stereo_block,
        "target": target_block,
        "limits": LIMITS,
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description="Mix report card for a rendered mix")
    ap.add_argument("--audio")
    ap.add_argument("--target", default="streaming")
    args = ap.parse_args(argv)
    try:
        if not args.audio:
            raise ValueError("--audio is required")
        result = analyze(args.audio, target=args.target)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
