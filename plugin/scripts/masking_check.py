"""Stem-pair masking check for the Wigout Studio engineer.

Band-wise simultaneous-energy overlap between two stems. A proxy for
perceptual masking, not a psychoacoustic model - advisory only.
"""
import argparse
import json
import sys

import numpy as np

BANDS = [
    ("sub", 20, 60),
    ("bass", 60, 250),
    ("lowmid", 250, 500),
    ("mid", 500, 2000),
    ("highmid", 2000, 6000),
    ("high", 6000, 16000),
]
ACTIVE_WINDOW_DB = 40.0  # frame is active if within 40 dB of the stem's loudest band frame
EDGE_PAD_FRAMES = 2  # (n_fft/2)/hop_length = 1024/512 - frames polluted by reflect-padding

LIMITS = [
    "band-energy overlap proxy, not a psychoacoustic masking model",
    "advisory - use ears and solo/mute to confirm before cutting",
    "first/last STFT frames are trimmed (reflect-padding artifacts); files of <=4 STFT frames (~46 ms at 44.1 kHz) are judged untrimmed",
]


def _band_frame_db(y, sr):
    import librosa  # lazy so --help stays fast
    S = np.abs(librosa.stft(y, n_fft=2048, hop_length=512)) ** 2
    # librosa.stft center=True reflect-pads the signal, so the first/last
    # ~ (n_fft/2)/hop frames see a mirrored synthetic transient, not signal.
    # Trim them; keep everything for very short files.
    if S.shape[1] > 2 * EDGE_PAD_FRAMES:
        S = S[:, EDGE_PAD_FRAMES:-EDGE_PAD_FRAMES]
    freqs = librosa.fft_frequencies(sr=sr, n_fft=2048)
    out = {}
    for name, lo, hi in BANDS:
        mask = (freqs >= lo) & (freqs < hi)
        if not np.any(mask):
            continue  # sample rate too low for this band
        out[name] = 10.0 * np.log10(np.maximum(S[mask].mean(axis=0), 1e-12))
    return out


def analyze(path_a, path_b):
    import librosa
    a, sr_a = librosa.load(path_a, sr=None, mono=True)
    b, sr_b = librosa.load(path_b, sr=None, mono=True)
    if a.size == 0 or b.size == 0:
        raise ValueError("audio file is empty")
    if sr_b != sr_a:
        b = librosa.resample(b, orig_sr=sr_b, target_sr=sr_a)

    fa = _band_frame_db(a, sr_a)
    fb = _band_frame_db(b, sr_a)
    ref_a = max(float(v.max()) for v in fa.values())
    ref_b = max(float(v.max()) for v in fb.values())

    overlap = {}
    for name in fa:
        if name not in fb:
            continue
        n = min(fa[name].size, fb[name].size)
        active_a = fa[name][:n] > (ref_a - ACTIVE_WINDOW_DB)
        active_b = fb[name][:n] > (ref_b - ACTIVE_WINDOW_DB)
        both = int(np.sum(active_a & active_b))
        either = int(np.sum(active_a | active_b))
        overlap[name] = round(both / either, 3) if either else 0.0

    worst = sorted(overlap, key=overlap.get, reverse=True)
    top = worst[0] if worst else None
    if top and overlap[top] > 0.6:
        advice = (
            f"strong simultaneous energy in '{top}': carve 2-4 dB from the "
            "supporting stem where the lead stem needs the band (EQ+), or "
            "duck it with sidechain compression (Compressor+)"
        )
    elif top and overlap[top] > 0.3:
        advice = f"moderate overlap in '{top}': check with solo/mute; a small cut or pan separation may help"
    else:
        advice = "no significant band overlap detected between these stems"

    return {"bands": overlap, "worst": worst[:2], "advice": advice, "limits": LIMITS}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Stem-pair masking (band-overlap) check")
    ap.add_argument("--a")
    ap.add_argument("--b")
    args = ap.parse_args(argv)
    try:
        if not args.a or not args.b:
            raise ValueError("--a and --b are both required")
        result = analyze(args.a, args.b)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
