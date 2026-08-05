"""Theory engine for Wigout Studio. Notes JSON in, JSON verdicts out.

music21 does the math; this module only adapts. Input schema:
{"notes": [{"pitch": 60, "start": 0.0, "duration": 1.0}]}
pitch = MIDI number, start/duration in beats.
"""
import argparse
import json
import sys

from music21 import chord as m21chord
from music21 import harmony
from music21 import key as m21key
from music21 import note as m21note
from music21 import roman
from music21 import scale as m21scale
from music21 import stream


def load_notes(path):
    if path == "-":
        data = json.load(sys.stdin)
    else:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    notes = data.get("notes")
    if not notes:
        raise ValueError("notes JSON must contain a non-empty 'notes' list")
    return notes


def notes_to_stream(notes):
    s = stream.Stream()
    for n in notes:
        m = m21note.Note(int(n["pitch"]))
        m.quarterLength = float(n["duration"])
        s.insert(float(n["start"]), m)
    return s


def _parse_key(text):
    tonic, mode = text.split()
    return m21key.Key(tonic, mode.lower())


def _slices(notes):
    by_start = {}
    for n in notes:
        by_start.setdefault(round(float(n["start"]), 4), []).append(int(n["pitch"]))
    return [(start, sorted(by_start[start])) for start in sorted(by_start)]


def cmd_key(args):
    k = notes_to_stream(load_notes(args.notes_json)).analyze("key")
    return {
        "key": f"{k.tonic.name} {k.mode}",
        "confidence": round(float(k.correlationCoefficient), 3),
        "alternatives": [
            f"{a.tonic.name} {a.mode}" for a in k.alternateInterpretations[:3]
        ],
    }


def cmd_roman(args):
    notes = load_notes(args.notes_json)
    k = _parse_key(args.key) if args.key else notes_to_stream(notes).analyze("key")
    analysis = []
    for start, pitches in _slices(notes):
        c = m21chord.Chord(pitches)
        analysis.append(
            {
                "start": start,
                "chord": c.pitchedCommonName,
                "roman": roman.romanNumeralFromChord(c, k).figure,
            }
        )
    return {"key": f"{k.tonic.name} {k.mode}", "analysis": analysis}


SCALES = {
    "major": m21scale.MajorScale,
    "minor": m21scale.MinorScale,
    "harmonic-minor": m21scale.HarmonicMinorScale,
    "melodic-minor": m21scale.MelodicMinorScale,
    "dorian": m21scale.DorianScale,
    "phrygian": m21scale.PhrygianScale,
    "lydian": m21scale.LydianScale,
    "mixolydian": m21scale.MixolydianScale,
    "locrian": m21scale.LocrianScale,
}


def cmd_scale(args):
    sc = SCALES[args.mode](args.root)
    pitches = sc.getPitches(f"{args.root}3", f"{args.root}4")[:-1]
    return {
        "scale": f"{args.root} {args.mode}",
        "pitches": [p.name for p in pitches],
        "midi": [p.midi for p in pitches],
    }


def cmd_chord(args):
    h = harmony.ChordSymbol(args.symbol)
    return {
        "symbol": args.symbol,
        "pitches": [p.nameWithOctave for p in h.pitches],
        "midi": [p.midi for p in h.pitches],
    }


def cmd_progression(args):
    k = _parse_key(args.key)
    prog = []
    for fig in args.numerals.split(","):
        rn = roman.RomanNumeral(fig.strip(), k)
        prog.append(
            {
                "roman": fig.strip(),
                "chord": rn.pitchedCommonName,
                "midi": [p.midi for p in rn.pitches],
            }
        )
    return {"key": args.key, "progression": prog}


def cmd_voicecheck(args):
    slices = [pitches for _, pitches in _slices(load_notes(args.notes_json))]
    issues = []
    for i in range(len(slices) - 1):
        a, b = slices[i], slices[i + 1]
        if len(a) != len(b) or len(a) < 2:
            continue
        for v1 in range(len(a)):
            for v2 in range(v1 + 1, len(a)):
                iv_a = (a[v2] - a[v1]) % 12
                iv_b = (b[v2] - b[v1]) % 12
                moved = a[v1] != b[v1] and a[v2] != b[v2]
                same_direction = (b[v1] - a[v1]) * (b[v2] - a[v2]) > 0
                if moved and same_direction and iv_a == iv_b and iv_a in (0, 7):
                    issues.append(
                        {
                            "slice": i,
                            "voices": [v1, v2],
                            "issue": "parallel fifths" if iv_a == 7 else "parallel octaves",
                            "from": [a[v1], a[v2]],
                            "to": [b[v1], b[v2]],
                        }
                    )
    return {"issues": issues, "clean": not issues}


def main(argv=None):
    ap = argparse.ArgumentParser(prog="theory_engine")
    sub = ap.add_subparsers(dest="command", required=True)

    p = sub.add_parser("key", help="infer key from notes JSON")
    p.add_argument("--notes-json", required=True, help="path or - for stdin")
    p.set_defaults(fn=cmd_key)

    p = sub.add_parser("roman", help="Roman-numeral analysis of note slices")
    p.add_argument("--notes-json", required=True)
    p.add_argument("--key", default=None, help='e.g. "C major"; inferred if omitted')
    p.set_defaults(fn=cmd_roman)

    p = sub.add_parser("scale", help="spell a scale")
    p.add_argument("root")
    p.add_argument("mode", choices=sorted(SCALES))
    p.set_defaults(fn=cmd_scale)

    p = sub.add_parser("chord", help="spell a chord symbol")
    p.add_argument("symbol")
    p.set_defaults(fn=cmd_chord)

    p = sub.add_parser("progression", help="realize Roman numerals in a key")
    p.add_argument("--key", required=True, help='e.g. "A minor"')
    p.add_argument("--numerals", required=True, help="comma-separated, e.g. i,VI,III,VII")
    p.set_defaults(fn=cmd_progression)

    p = sub.add_parser("voicecheck", help="parallel fifths/octaves check")
    p.add_argument("--notes-json", required=True)
    p.set_defaults(fn=cmd_voicecheck)

    args = ap.parse_args(argv)
    try:
        result = args.fn(args)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
