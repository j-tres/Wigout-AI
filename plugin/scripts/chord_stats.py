"""Corpus chord statistics for Wigout Studio music-theory (stdlib only).

Answers next-chord / common-progressions / diagnosis queries from the
prebuilt chord_model.json (built by corpus/build_chord_model.py from
McGill Billboard + When in Rome open slices). All numbers are USAGE
COUNTS from the source corpora — every response carries a `limits`
array; relay it honestly. Numerals in and out are theory_engine-
compatible figures; the runtime never infers keys.
"""
import argparse
import gzip
import json
import math
import os
import sys

SCHEMA = 1
MODES = ("major", "minor")


def _default_model_path():
    here = os.path.dirname(os.path.abspath(__file__))
    for name in ("chord_model.json", "chord_model.json.gz"):
        path = os.path.join(here, name)
        if os.path.isfile(path):
            return path
    return os.path.join(here, "chord_model.json")


def load_model(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as f:
        model = json.load(f)
    if model.get("modelSchema") != SCHEMA:
        raise ValueError(
            f"model schema {model.get('modelSchema')!r} unsupported (need {SCHEMA})")
    return model


def quality_of(figure):
    """Decode quality from a rendered figure (inverse of the build renderer)."""
    if figure.endswith("maj7"):
        return "maj7"
    if figure.endswith("o"):
        return "dim"
    if figure.endswith("+"):
        return "aug"
    first_roman = next((c for c in figure if c in "IViv"), "I")
    if figure.endswith("7"):
        return "min7" if first_roman.islower() else "dom7"
    return "min" if first_roman.islower() else "maj"


def _limits(model):
    prov = model["provenance"]
    drop = prov.get("dropRate")
    drop_txt = f"{round(drop * 100)}%" if isinstance(drop, (int, float)) else "unknown %"
    return [
        f"corpus: {prov.get('corpus')} — small, era/style-skewed slices, not all music",
        f"key/mode resolution automated at build; {drop_txt} of source rows dropped",
        "counts are usage statistics, not aesthetic judgments",
    ]


def _bucket(model, genre, mode):
    if mode not in MODES:
        raise ValueError(f"--mode must be one of {'/'.join(MODES)}")
    notes = []
    name = "global"
    if genre:
        g = genre.strip().lower()
        if g in model["buckets"]:
            name = g
        else:
            known = ", ".join(sorted(b for b in model["buckets"] if b != "global"))
            notes.append(f"genre '{genre}' not in model buckets ({known}); using global")
    tables = model["buckets"][name].get(mode)
    if tables is None:
        raise ValueError(f"mode '{mode}' not present in bucket '{name}'")
    return tables, name, notes


def _lookup(tables, context, level):
    if level == "trigram":
        return tables["trigrams"].get(",".join(context), {})
    if level == "bigram":
        return tables["bigrams"].get(context[-1], {})
    return tables["unigrams"]


def cmd_next(model, args):
    if not args.context:
        raise ValueError("--context is required (comma-separated numerals)")
    context = [t.strip() for t in args.context.split(",") if t.strip()]
    if not context:
        raise ValueError("--context must contain at least one numeral")
    tables, bucket_used, notes = _bucket(model, args.genre, args.mode)
    used = context[-2:]
    truncated = len(context) > 2
    if truncated:
        notes.append(f"context truncated to last 2 chords: {','.join(used)}")
    table, backoff = {}, None
    for level in (["trigram"] if len(used) == 2 else []) + ["bigram", "unigram"]:
        table, backoff = _lookup(tables, used, level), level
        if table:
            break
    if backoff == "unigram":
        notes.append("context not found in corpus at any depth; "
                     "showing overall frequencies")
    total = sum(table.values())
    ranked = sorted(table.items(), key=lambda kv: kv[1], reverse=True)[:args.top]
    candidates = []
    for figure, count in ranked:
        global_p = None
        if bucket_used != "global":
            global_table = _lookup(model["buckets"]["global"][args.mode], used, backoff)
            global_total = sum(global_table.values())
            if global_total:
                global_p = round(global_table.get(figure, 0) / global_total, 4)
        candidates.append({
            "numeral": figure, "quality": quality_of(figure),
            "probability": round(count / total, 4), "count": count,
            "globalProbability": global_p,
        })
    return {"genre": args.genre, "mode": args.mode, "bucketUsed": bucket_used,
            "contextUsed": used, "truncated": truncated, "backoff": backoff,
            "candidates": candidates, "notes": notes, "limits": _limits(model)}


def cmd_progressions(model, args):
    if not 3 <= args.length <= 8:
        raise ValueError("--length must be between 3 and 8")
    tables, bucket_used, notes = _bucket(model, args.genre, args.mode)
    stored = tables["loops"].get(str(args.length), [])
    total = sum(e["count"] for e in stored)
    loops = [{"progression": e["progression"], "count": e["count"],
              "shareOfStored": round(e["count"] / total, 4) if total else None}
             for e in stored[:args.top]]
    if not loops:
        notes.append(f"no length-{args.length} progressions stored for this bucket")
    return {"genre": args.genre, "mode": args.mode, "bucketUsed": bucket_used,
            "length": args.length, "loops": loops,
            "notes": notes, "limits": _limits(model)}


def _percentile(histogram, value):
    if not histogram:
        return None
    edges, counts = histogram["binEdges"], histogram["counts"]
    total = sum(counts)
    if not total:
        return None
    if value <= edges[0]:
        return 0.0
    if value >= edges[-1]:
        return 100.0
    cumulative = 0.0
    for i, count in enumerate(counts):
        low, high = edges[i], edges[i + 1]
        if value >= high:
            cumulative += count
        else:
            cumulative += count * (value - low) / (high - low)
            break
    return round(100.0 * cumulative / total, 1)


def _mean_log10(bigrams, progression):
    logs = []
    for a, b in zip(progression, progression[1:]):
        row = bigrams.get(a, {})
        total = sum(row.values())
        count = row.get(b, 0)
        if not count or not total:
            return None
        logs.append(math.log10(count / total))
    return sum(logs) / len(logs)


def _substitutions(bigrams, progression, top=3):
    base = _mean_log10(bigrams, progression)
    out = []
    for i in range(len(progression)):
        for candidate in bigrams:
            if candidate == progression[i]:
                continue
            alt = progression[:i] + [candidate] + progression[i + 1:]
            score = _mean_log10(bigrams, alt)
            if score is None:
                continue
            if base is None or score > base:
                out.append({"position": i, "original": progression[i],
                            "candidate": candidate,
                            "meanLog10Prob": round(score, 3)})
    out.sort(key=lambda e: e["meanLog10Prob"], reverse=True)
    return out[:top]


def cmd_diagnose(model, args):
    if not args.numerals:
        raise ValueError("--numerals is required (comma-separated)")
    progression = [t.strip() for t in args.numerals.split(",") if t.strip()]
    if len(progression) < 2:
        raise ValueError("--numerals needs at least 2 chords")
    tables, bucket_used, notes = _bucket(model, args.genre, args.mode)
    bigrams = tables["bigrams"]
    transitions, unseen = [], 0
    for a, b in zip(progression, progression[1:]):
        row = bigrams.get(a, {})
        total = sum(row.values())
        count = row.get(b, 0)
        if count and total:
            transitions.append({
                "from": a, "to": b,
                "probability": round(count / total, 4),
                "rank": 1 + sum(1 for v in row.values() if v > count),
                "of": len(row)})
        else:
            unseen += 1
            transitions.append({"from": a, "to": b, "probability": 0.0,
                                "rank": None, "of": len(row)})
    if unseen:
        notes.append(f"{unseen} transition(s) unseen in this bucket's corpus slice")
    mean_log = _mean_log10(bigrams, progression)
    mean_log = round(mean_log, 3) if mean_log is not None else None
    percentile = (_percentile(tables.get("scoreHistogram"), mean_log)
                  if mean_log is not None else None)
    loop_rank = None
    for rank, entry in enumerate(tables["loops"].get(str(len(progression)), []), 1):
        if entry["progression"] == progression:
            loop_rank = rank
            break
    return {"genre": args.genre, "mode": args.mode, "bucketUsed": bucket_used,
            "progression": progression, "transitions": transitions,
            "overall": {"meanLog10Prob": mean_log, "percentile": percentile,
                        "loopRank": loop_rank},
            "substitutions": _substitutions(bigrams, progression),
            "notes": notes, "limits": _limits(model)}


def main(argv=None):
    ap = argparse.ArgumentParser(
        prog="chord_stats",
        description="Corpus chord statistics (counts, not taste)")
    sub = ap.add_subparsers(dest="command")
    p = sub.add_parser("next", help="ranked next-chord continuations")
    p.add_argument("--context", default=None)
    p.add_argument("--top", type=int, default=5)
    p.set_defaults(fn=cmd_next)
    p = sub.add_parser("progressions", help="most-common progressions")
    p.add_argument("--length", type=int, default=4)
    p.add_argument("--top", type=int, default=10)
    p.set_defaults(fn=cmd_progressions)
    p = sub.add_parser("diagnose", help="commonness + substitutions")
    p.add_argument("--numerals", default=None)
    p.set_defaults(fn=cmd_diagnose)
    for parser in sub.choices.values():
        parser.add_argument("--mode", default=None)
        parser.add_argument("--genre", default=None)
        parser.add_argument("--model", default=None)
    args = ap.parse_args(argv)
    try:
        if args.command is None:
            raise ValueError("usage: chord_stats.py {next|progressions|diagnose} ...")
        if not args.mode:
            raise ValueError("--mode is required (major|minor)")
        model = load_model(args.model or _default_model_path())
        result = args.fn(model, args)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
