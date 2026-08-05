"""Spectral analysis for Wigout Studio sound-design verify-by-analysis.

Bounce a short note, measure it, compare to the described target. Coarse
and advisory — a proxy for subjective descriptors, not an arbiter of taste.
"""
import argparse
import json
import sys

import numpy as np


def _bucket(value, low_hi, med_hi):
    if value < low_hi:
        return "low"
    if value < med_hi:
        return "medium"
    return "high"


def analyze(path):
    import librosa  # imported lazily so --help stays fast
    y, sr = librosa.load(path, sr=None, mono=True)
    if y.size == 0:
        raise ValueError("audio file is empty")

    centroid = float(np.mean(librosa.feature.spectral_centroid(y=y, sr=sr)))
    rolloff = float(np.mean(librosa.feature.spectral_rolloff(y=y, sr=sr, roll_percent=0.85)))
    flatness = float(np.mean(librosa.feature.spectral_flatness(y=y)))
    rms = float(np.mean(librosa.feature.rms(y=y)))
    peak = float(np.max(np.abs(y))) or 1e-9
    crest = peak / (rms or 1e-9)

    # Band energy split (fractions of total): low <250Hz, mid 250-2000Hz, high >2000Hz.
    S = np.abs(librosa.stft(y)) ** 2
    freqs = librosa.fft_frequencies(sr=sr)
    total = float(np.sum(S)) or 1e-9
    low = float(np.sum(S[freqs < 250])) / total
    mid = float(np.sum(S[(freqs >= 250) & (freqs < 2000)])) / total
    high = float(np.sum(S[freqs >= 2000])) / total

    features = {
        "centroid_hz": round(centroid, 1),
        "rolloff_hz": round(rolloff, 1),
        "flatness": round(flatness, 4),
        "rms": round(rms, 4),
        "crest": round(crest, 3),
        "bands": {"low": round(low, 3), "mid": round(mid, 3), "high": round(high, 3)},
    }
    descriptors = {
        "bright": _bucket(centroid, 1500.0, 3500.0),
        "warm": _bucket(low + 0.5 * mid, 0.35, 0.6),
        "full": _bucket(1.0 - high, 0.5, 0.8),
    }
    return {"features": features, "descriptors": descriptors}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Spectral analysis of an audio file")
    ap.add_argument("audio_file")
    args = ap.parse_args(argv)
    try:
        result = analyze(args.audio_file)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
