"""Transcribe a hum/melody recording to note events.

Monophonic path: librosa pyin (clean for humming — no phantom harmonics).
Polyphonic path (Task 5): Spotify basic-pitch.
Events: {"pitch": midi int, "start_s": float, "duration_s": float}.
"""
import argparse
import json
import sys

import librosa
import numpy as np


def pyin_notes(path, min_note_s=0.08):
    y, sr = librosa.load(path, sr=None, mono=True)
    f0, voiced, _ = librosa.pyin(
        y,
        fmin=float(librosa.note_to_hz("C2")),
        fmax=float(librosa.note_to_hz("C6")),
        sr=sr,
    )
    hop_s = 512 / sr
    events = []
    start, pitches = None, []

    def flush(end_t):
        nonlocal start, pitches
        if start is not None and (end_t - start) >= min_note_s and pitches:
            events.append(
                {
                    "pitch": int(round(float(np.median(pitches)))),
                    "start_s": round(start, 4),
                    "duration_s": round(end_t - start, 4),
                }
            )
        start, pitches = None, []

    for i, (v, f) in enumerate(zip(voiced, f0)):
        t = i * hop_s
        if v and not np.isnan(f):
            midi = float(librosa.hz_to_midi(f))
            if start is None:
                start, pitches = t, [midi]
            elif abs(midi - float(np.median(pitches))) > 0.6:
                flush(t)
                start, pitches = t, [midi]
            else:
                pitches.append(midi)
        else:
            flush(t)
    flush(len(y) / sr)
    return events


def basic_pitch_notes(path):
    from basic_pitch import ICASSP_2022_MODEL_PATH
    from basic_pitch.inference import predict

    _, _, note_events = predict(path, ICASSP_2022_MODEL_PATH)
    return [
        {
            "pitch": int(pitch),
            "start_s": round(float(start), 4),
            "duration_s": round(float(end - start), 4),
        }
        for start, end, pitch, _amp, _bends in sorted(note_events)
    ]


def to_beats(events, bpm):
    out = []
    for ev in events:
        ev = dict(ev)
        ev["start_beats"] = round(ev["start_s"] * bpm / 60.0, 4)
        ev["duration_beats"] = round(ev["duration_s"] * bpm / 60.0, 4)
        out.append(ev)
    return out


def write_midi(events, bpm, out_path):
    from music21 import note as m21note
    from music21 import stream, tempo

    s = stream.Stream()
    s.insert(0, tempo.MetronomeMark(number=bpm))
    for ev in events:
        n = m21note.Note(ev["pitch"])
        n.quarterLength = max(ev["duration_beats"], 0.125)
        s.insert(ev["start_beats"], n)
    s.write("midi", fp=out_path)


def estimate_bpm(path):
    y, sr = librosa.load(path, sr=None, mono=True)
    tempo_val, _ = librosa.beat.beat_track(y=y, sr=sr)
    t = float(np.atleast_1d(tempo_val)[0])
    return round(t, 1) if t > 0 else 120.0


def main(argv=None):
    ap = argparse.ArgumentParser(prog="hum2midi")
    ap.add_argument("input", help="audio file (wav/flac/mp3 — anything librosa reads)")
    ap.add_argument("--mode", choices=["mono", "poly"], default="mono",
                    help="mono=pyin (best for humming), poly=basic-pitch")
    ap.add_argument("--bpm", type=float, default=None, help="beat grid; estimated if omitted")
    ap.add_argument("--out-json", default=None)
    ap.add_argument("--out-midi", default=None)
    args = ap.parse_args(argv)
    try:
        bpm = args.bpm if args.bpm is not None else estimate_bpm(args.input)
        if args.mode == "poly":
            try:
                events = basic_pitch_notes(args.input)
            except ImportError:
                raise RuntimeError(
                    "basic-pitch not installed; run /studio setup or use --mode mono"
                )
        else:
            events = pyin_notes(args.input)
        events = to_beats(events, bpm)
        result = {"bpm": bpm, "mode": args.mode, "events": events}
        if args.out_json:
            with open(args.out_json, "w", encoding="utf-8") as f:
                json.dump(result, f, indent=2)
        if args.out_midi:
            write_midi(events, bpm, args.out_midi)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
