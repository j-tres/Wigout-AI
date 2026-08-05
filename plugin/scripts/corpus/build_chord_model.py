"""Build chord_model.json from open chord corpora (author-run, never user-run).

Sources (license evidence in CORPUS_NOTES.md):
- McGill Billboard (CC0) -> slice "pop": Harte-syntax chords, annotated tonics.
- When in Rome open-license sub-corpora (CC-BY-SA/CC0) -> slice "classical":
  RomanText parsed via music21. NC sub-corpora are excluded by allowlist.

Readers produce Roman-numeral runs; a shared counter assembles per-slice,
per-mode n-gram + loop tables. Key/mode resolution happens HERE, at build
time, with low-confidence songs dropped and the drop rate disclosed in the
artifact provenance. The runtime (chord_stats.py) is a thin stdlib reader.
"""
import argparse
import json
import re
import sys

NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}
HARTE_RE = re.compile(r"^([A-G])([#b]{0,2})(?::([^/(]*))?(?:\(([^)]*)\))?(?:/.*)?$")

# Harte shorthand -> vocabulary quality. Vocabulary is exactly
# {maj, min, dom7, maj7, min7, dim, aug} — every entry must be renderable
# as a music21-parseable Roman numeral figure. Mapping decisions (spec):
# extensions 9/11/13 fold to their 7th family; sus/add/6/power fold to the
# parent triad; hdim7/dim7 fold to dim; minmaj7 folds to min. Unknown
# shorthand -> unparseable (drop, counted at build).
HARTE_QUALITY = {
    "maj": "maj", "": "maj", "5": "maj", "1": "maj",
    "maj6": "maj", "6": "maj", "sus": "maj", "sus2": "maj", "sus4": "maj",
    "maj(9)": "maj",
    "min": "min", "min6": "min", "minmaj7": "min",
    "7": "dom7", "9": "dom7", "11": "dom7", "13": "dom7",
    "maj7": "maj7", "maj9": "maj7", "maj11": "maj7", "maj13": "maj7",
    "min7": "min7", "min9": "min7", "min11": "min7", "min13": "min7",
    "dim": "dim", "dim7": "dim", "hdim7": "dim",
    "aug": "aug", "aug7": "aug",
}


def parse_harte(symbol):
    """Harte chord symbol -> (root_pc, quality) or None (incl. N/X/no-chord)."""
    s = (symbol or "").strip()
    m = HARTE_RE.match(s)
    if not m:
        return None
    letter, acc, shorthand, paren = m.groups()
    pc = (NOTE_PC[letter] + acc.count("#") - acc.count("b")) % 12
    shorthand = shorthand if shorthand is not None else ""
    if shorthand == "min7" and paren and "b5" in paren:
        return (pc, "dim")  # min7(b5) is half-diminished
    quality = HARTE_QUALITY.get(shorthand)
    return (pc, quality) if quality else None


# --- part B: key/mode resolution + numeral rendering --------------------

# Krumhansl-Kessler key profiles (probe-tone ratings).
KK_MAJOR = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
KK_MINOR = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]

CHORD_TONES = {
    "maj": (0, 4, 7), "min": (0, 3, 7), "dom7": (0, 4, 7, 10),
    "maj7": (0, 4, 7, 11), "min7": (0, 3, 7, 10),
    "dim": (0, 3, 6), "aug": (0, 4, 8),
}


def _pearson(x, y):
    n = len(x)
    mx, my = sum(x) / n, sum(y) / n
    sxy = sum((a - mx) * (b - my) for a, b in zip(x, y))
    sxx = sum((a - mx) ** 2 for a in x)
    syy = sum((b - my) ** 2 for b in y)
    if sxx == 0 or syy == 0:
        return 0.0
    return sxy / (sxx * syy) ** 0.5


def _histogram(chords):
    hist = [0.0] * 12
    for pc, quality in chords:
        for tone in CHORD_TONES[quality]:
            hist[(pc + tone) % 12] += 1.0
    return hist


def _mode_scores(hist, tonic_pc):
    out = []
    for mode, profile in (("major", KK_MAJOR), ("minor", KK_MINOR)):
        rotated = [profile[(i - tonic_pc) % 12] for i in range(12)]
        out.append((_pearson(hist, rotated), mode))
    return out


def infer_key(chords, min_corr=0.55, min_margin=0.03):
    """Full 24-key Krumhansl over the chord-tone histogram."""
    hist = _histogram(chords)
    scores = []
    for tonic in range(12):
        for corr, mode in _mode_scores(hist, tonic):
            scores.append((corr, tonic, mode))
    scores.sort(key=lambda t: t[0], reverse=True)
    (best_r, tonic, mode), (second_r, _, _) = scores[0], scores[1]
    margin = best_r - second_r
    if best_r < min_corr or margin < min_margin:
        return None
    return {"tonic": tonic, "mode": mode,
            "corr": round(best_r, 4), "margin": round(margin, 4)}


def resolve_mode(chords, tonic_pc, min_corr=0.55):
    """Krumhansl restricted to the two modes of a KNOWN (annotated) tonic."""
    scores = sorted(_mode_scores(_histogram(chords), tonic_pc), reverse=True)
    best_r, mode = scores[0]
    if best_r < min_corr:
        return None
    return {"mode": mode, "corr": round(best_r, 4)}


def match_quality(root_pc, pitch_classes):
    """Largest vocabulary template rooted at root_pc that fits the pcs."""
    pcs = set(pitch_classes)
    if root_pc not in pcs:
        return None
    for quality in ("dom7", "maj7", "min7", "maj", "min", "dim", "aug"):
        template = {(root_pc + t) % 12 for t in CHORD_TONES[quality]}
        if template <= pcs:
            return quality
    return None


# Degree spelling per semitone-above-tonic. Minor: 4 and 9 have no clean
# diatonic spelling (drop); 11 is dim-only ("viio", the leading tone —
# music21 builds minor-key viio on the RAISED 7th).
MAJOR_DEGREE = {0: "I", 1: "bII", 2: "II", 3: "bIII", 4: "III", 5: "IV",
                6: "bV", 7: "V", 8: "bVI", 9: "VI", 10: "bVII", 11: "VII"}
MINOR_DEGREE = {0: "I", 1: "bII", 2: "II", 3: "III", 5: "IV", 6: "#IV",
                7: "V", 8: "VI", 10: "VII"}
UPPER_QUALITIES = {"maj", "dom7", "maj7", "aug"}
FIGURE_SUFFIX = {"maj": "", "min": "", "dom7": "7", "min7": "7",
                 "maj7": "maj7", "dim": "o", "aug": "+"}

_FIGURE_OK_CACHE = {}


def _figure_ok(figure, mode, deg):
    """Memoized: does music21 parse this figure in this mode AND resolve it
    to the intended scale-degree offset? Checked once in a reference key —
    the offset is transposition-invariant."""
    cache_key = (figure, mode, deg)
    if cache_key not in _FIGURE_OK_CACHE:
        from music21 import key as m21key, roman
        if mode == "major":
            k, ref_tonic = m21key.Key("C", "major"), 0
        else:
            k, ref_tonic = m21key.Key("A", "minor"), 9
        try:
            rn = roman.RomanNumeral(figure, k)
            _FIGURE_OK_CACHE[cache_key] = (
                (rn.root().pitchClass - ref_tonic) % 12 == deg)
        except Exception:
            _FIGURE_OK_CACHE[cache_key] = False
    return _FIGURE_OK_CACHE[cache_key]


def render_numeral(root_pc, quality, tonic_pc, mode):
    """(chord, key) -> music21-parseable figure, or None (build drops it)."""
    deg = (root_pc - tonic_pc) % 12
    if mode == "minor" and deg == 11:
        return "viio" if quality == "dim" else None
    table = MAJOR_DEGREE if mode == "major" else MINOR_DEGREE
    base = table.get(deg)
    if base is None:
        return None
    if quality not in UPPER_QUALITIES:
        # lowercase the roman letters; a leading accidental (b/#) survives
        prefix = base[0] if base[0] in "b#" else ""
        base = prefix + base[len(prefix):].lower()
    figure = base + FIGURE_SUFFIX[quality]
    return figure if _figure_ok(figure, mode, deg) else None


# --- part C: counting, assembly, CLI -----------------------------------
import math
import random
from datetime import datetime, timezone

LOOP_LENGTHS = range(3, 9)
SCORE_SAMPLE_CAP = 20000
HIST_EDGES = [round(-6.0 + 0.25 * i, 2) for i in range(25)]  # -6.0 .. 0.0


def new_drops():
    return {"unparseableToken": 0, "shortSegment": 0, "shortRow": 0,
            "keyConfidence": 0, "unspellable": 0, "unseenScoreRuns": 0,
            "unreadableFile": 0, "excludedAnalyst": 0}


def _new_tables():
    return {"unigrams": {}, "bigrams": {}, "trigrams": {},
            "loopCounts": {str(L): {} for L in LOOP_LENGTHS},
            "scoreSample": [], "sampleSeen": 0}


def _count_run(run, tables, rng):
    for i, token in enumerate(run):
        tables["unigrams"][token] = tables["unigrams"].get(token, 0) + 1
        if i >= 1:
            row = tables["bigrams"].setdefault(run[i - 1], {})
            row[token] = row.get(token, 0) + 1
        if i >= 2:
            ctx = f"{run[i - 2]},{run[i - 1]}"
            row = tables["trigrams"].setdefault(ctx, {})
            row[token] = row.get(token, 0) + 1
    for L in LOOP_LENGTHS:
        counts = tables["loopCounts"][str(L)]
        for i in range(len(run) - L + 1):
            key = ",".join(run[i:i + L])
            counts[key] = counts.get(key, 0) + 1
    # reservoir sample for the score histogram
    tables["sampleSeen"] += 1
    if len(tables["scoreSample"]) < SCORE_SAMPLE_CAP:
        tables["scoreSample"].append(run)
    else:
        j = rng.randrange(tables["sampleSeen"])
        if j < SCORE_SAMPLE_CAP:
            tables["scoreSample"][j] = run


def _finalize_tables(tables, min_count, top_loops, drops):
    for kind in ("bigrams", "trigrams"):
        pruned = {}
        for ctx, row in tables[kind].items():
            kept = {t: c for t, c in row.items() if c >= min_count}
            if kept:
                pruned[ctx] = kept
        tables[kind] = pruned
    loops = {}
    for L, counts in tables.pop("loopCounts").items():
        top = sorted(counts.items(), key=lambda kv: kv[1], reverse=True)[:top_loops]
        loops[L] = [{"progression": k.split(","), "count": c} for k, c in top]
    tables["loops"] = loops
    # score histogram: mean log10 bigram prob per sampled run
    hist = [0] * (len(HIST_EDGES) - 1)
    bigrams = tables["bigrams"]
    for run in tables.pop("scoreSample"):
        logs = []
        for a, b in zip(run, run[1:]):
            row = bigrams.get(a, {})
            total = sum(row.values())
            count = row.get(b, 0)
            if not count or not total:
                logs = None
                break
            logs.append(math.log10(count / total))
        if logs is None:
            drops["unseenScoreRuns"] += 1
            continue
        value = max(HIST_EDGES[0], min(HIST_EDGES[-1] - 1e-9, sum(logs) / len(logs)))
        for i in range(len(hist)):
            if HIST_EDGES[i] <= value < HIST_EDGES[i + 1]:
                hist[i] += 1
                break
    tables["scoreHistogram"] = {"binEdges": HIST_EDGES, "counts": hist}
    tables.pop("sampleSeen", None)
    return tables


def build_model(rows, params, drops=None):
    rng = random.Random(params["seed"])
    drops = drops if drops is not None else new_drops()
    buckets, kept_per_slice = {}, {}
    input_rows = kept_rows = 0
    for row in rows:
        input_rows += 1
        runs_by_mode = row.get("runs") or {}
        if not any(runs_by_mode.get(m) for m in ("major", "minor")):
            continue
        kept_rows += 1
        slice_name = row.get("slice")
        if slice_name:
            kept_per_slice[slice_name] = kept_per_slice.get(slice_name, 0) + 1
        targets = ["global"] + ([slice_name] if slice_name else [])
        for mode in ("major", "minor"):
            for run in runs_by_mode.get(mode) or []:
                for name in targets:
                    tables = buckets.setdefault(name, {}).setdefault(
                        mode, _new_tables())
                    _count_run(run, tables, rng)
    # fold under-supported slices (their counts are already in global)
    for name, kept in list(kept_per_slice.items()):
        if kept < params["min_bucket"]:
            buckets.pop(name, None)
            kept_per_slice.pop(name)
    for name, mode_tables in buckets.items():
        for mode, tables in mode_tables.items():
            _finalize_tables(tables, params["min_count"], params["top_loops"], drops)
    return {
        "modelSchema": 1,
        "provenance": {
            "corpus": params.get("corpus", "unknown"),
            "corpusVersion": params.get("revision", "unknown"),
            "license": params.get("license", "unknown"),
            "attribution": params.get("attribution", "unknown"),
            "buildDate": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "inputRows": input_rows, "keptRows": kept_rows,
            "dropRate": round(1 - kept_rows / input_rows, 4) if input_rows else None,
            "drops": drops,
            "slices": kept_per_slice,
            "buckets": sorted(b for b in buckets if b != "global"),
            "params": {k: v for k, v in params.items()
                       if k in ("min_corr", "min_margin", "min_count",
                                "min_bucket", "top_loops", "seed")},
            "vocabularyNote": "qualities {maj,min,dom7,maj7,min7,dim,aug}; "
                              "9/11/13->7th family, sus/add/6/5->triad, "
                              "hdim7/dim7->dim, minmaj7->min; inversions "
                              "and slash basses folded to parent chords",
        },
        "buckets": buckets,
    }


# --- readers: McGill Billboard (salami/Harte) + When in Rome (RomanText) --
import os

TONIC_LINE = re.compile(r"^#\s*tonic:\s*([A-G][#b]{0,2})\s*$")

# Open-license When in Rome sub-corpora ONLY (CC-BY-SA/CC0 — see
# CORPUS_NOTES.md, "Alternative corpus spike"). The repo also carries
# DCML-sourced (CC BY-NC-SA) and TAVERN/Tymoczko-sourced (unverified)
# sub-corpora which MUST NOT be read; both live as SIBLING composer folders
# inside the same top-level directories used below (e.g. Keyboard_Other also
# holds Chopin/Debussy/etc; Variations_and_Grounds also holds unverified
# TAVERN-sourced Beethoven/Mozart entries), so each path here is scoped to
# the specific composer (and, for Bach keyboard, specific collection)
# CORPUS_NOTES.md actually verified as open — never a whole shared parent.
# Verified live against github.com/MarkGotham/When-in-Rome (master) during
# this task:
#   - Corpus/OpenScore-LiederCorpus: the "19th-c. lieder sample" — CC0 via
#     OpenScore/Lieder (CORPUS_NOTES.md line ~267).
#   - Corpus/Keyboard_Other/Bach,_Johann_Sebastian/The_Well-Tempered_Clavier_I:
#     exactly the "Bach Preludes 24, WTC I" (CORPUS_NOTES.md line ~357), now
#     that WIR_EXCLUDE_DIR_SUFFIXES below prunes the two Tymoczko-analyzed
#     "*_fugue" dirs (19_fugue, 22_fugue — "Analyst: Dmitri Tymoczko and his
#     computer", the UNVERIFIED category per CORPUS_NOTES.md's spike table)
#     that otherwise sit alongside the 24 prelude dirs in this same folder;
#     confirmed 24 YES entries in Keyboard_Other.tsv, all WTC I. WTC II
#     (fugues only, not preludes) and the other composer subfolders
#     (Chopin/Debussy/Dvorak/Grieg/Liszt/Medtner/Schumann/Tchaikovsky) sitting
#     alongside Bach in Keyboard_Other are NOT part of the verified slice and
#     are excluded by not walking the parent Keyboard_Other/Bach.../ level.
#   - Corpus/Variations_and_Grounds/Bach,_Johann_Sebastian and
#     .../Purcell,_Henry: the "Ground bass works (Bach & Purcell)"
#     (CORPUS_NOTES.md line ~357). Confirmed via Variations_and_Grounds_
#     contents.tsv (YES rows) and the live directory tree; the Beethoven/
#     Mozart subfolders in the same parent are TAVERN-sourced and marked
#     UNVERIFIED in CORPUS_NOTES.md's spike table, so they are excluded by
#     scoping to the Bach/Purcell composer subfolders specifically.
WIR_OPEN_SUBCORPORA = [
    "Corpus/OpenScore-LiederCorpus",
    "Corpus/Keyboard_Other/Bach,_Johann_Sebastian/The_Well-Tempered_Clavier_I",
    "Corpus/Variations_and_Grounds/Bach,_Johann_Sebastian",
    "Corpus/Variations_and_Grounds/Purcell,_Henry",
]

# Human analyses only: the corpus also carries AugmentedNet machine analyses
# (analysis_automatic.rntxt) which must not be counted (undisclosed quality,
# double-counts works). WTC I fugue dirs are Tymoczko-analyzed — UNVERIFIED
# license category per CORPUS_NOTES.md — and must not be read.
WIR_EXCLUDE_FILE_SUBSTRINGS = ("automatic",)
WIR_EXCLUDE_DIR_SUFFIXES = ("_fugue",)

# Source rule, not location rule: any analysis attributed to Tymoczko is
# UNVERIFIED-license category per CORPUS_NOTES.md, wherever it lives.
# (e.g. Brahms Marienlieder Op.22 under OpenScore-LiederCorpus carries
# "Analyst: Dmitri Tymoczko and his computer".) Kept narrowly "Tymoczko":
# "Mark Gotham after AugmentedNet" analyst lines are human-reviewed and
# acceptable — do NOT broaden this to "AugmentedNet".
WIR_EXCLUDE_ANALYST_SUBSTRINGS = ("Tymoczko",)


def _note_pc(name):
    return (NOTE_PC[name[0]] + name.count("#") - name.count("b")) % 12


def _sections_from_salami(text, drops):
    """Split a salami_chords.txt into (tonic_pc|None, [(pc,q)|None ...])
    sections; a new '# tonic:' line starts a new section."""
    sections, tonic, tokens = [], None, []
    for line in text.splitlines():
        m = TONIC_LINE.match(line.strip())
        if m:
            if tokens:
                sections.append((tonic, tokens))
                tokens = []
            tonic = _note_pc(m.group(1))
            continue
        if "|" not in line:
            continue
        bars = line.split("|")[1:-1]  # text outside the first/last | is labels
        for bar in bars:
            for token in bar.split():
                if token == ".":
                    continue  # "repeat previous chord" — collapse handles it
                parsed = parse_harte(token)
                if parsed is None:
                    drops["unparseableToken"] += 1
                    tokens.append(None)  # run boundary
                else:
                    tokens.append(parsed)
    if tokens:
        sections.append((tonic, tokens))
    return sections


def _segments(tokens, drops):
    """Split parsed-token stream at None boundaries; collapse repeats;
    keep segments of length >= 3."""
    segments, current = [], []
    for parsed in tokens + [None]:
        if parsed is None:
            if len(current) >= 3:
                segments.append(current)
            elif current:
                drops["shortSegment"] += 1
            current = []
        elif not current or current[-1] != parsed:
            current.append(parsed)
    return segments


def _empty_runs():
    return {"major": [], "minor": []}


def _append_run(runs, figures, drops):
    if len(figures) >= 3:
        runs.append(figures)
    elif figures:
        drops["shortSegment"] += 1


def _render_segment(segment, tonic, mode, drops):
    """Render a chord segment to numeral runs; unspellable chords split."""
    out, current = [], []
    for pc, quality in segment:
        figure = render_numeral(pc, quality, tonic, mode)
        if figure is None:
            drops["unspellable"] += 1
            _append_run(out, current, drops)
            current = []
        elif not current or current[-1] != figure:
            current.append(figure)
    _append_run(out, current, drops)
    return out


def billboard_song_row(text, drops):
    runs = _empty_runs()
    for tonic, tokens in _sections_from_salami(text, drops):
        chords = [t for t in tokens if t is not None]
        if len(chords) < 3:
            drops["shortRow"] += 1
            continue
        if tonic is not None:
            resolved = resolve_mode(chords, tonic)
            key = {"tonic": tonic, "mode": resolved["mode"]} if resolved else None
        else:
            key = infer_key(chords)
        if key is None:
            drops["keyConfidence"] += 1
            continue
        for segment in _segments(tokens, drops):
            for run in _render_segment(segment, key["tonic"], key["mode"], drops):
                runs[key["mode"]].append(run)
    return {"slice": "pop", "runs": runs}


def billboard_rows(root_dir, drops):
    for dirpath, _dirnames, filenames in os.walk(root_dir):
        for name in filenames:
            if name == "salami_chords.txt":
                with open(os.path.join(dirpath, name), encoding="utf-8") as f:
                    yield billboard_song_row(f.read(), drops)


def wir_row(path, drops):
    from music21 import converter, key as m21key
    # Source-rule gate BEFORE parsing: an "Analyst:" header naming an
    # excluded analyst (WIR_EXCLUDE_ANALYST_SUBSTRINGS) means the analysis
    # is UNVERIFIED-license category — never read it, wherever it lives.
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            header = f.read(2000)
    except OSError:
        drops["unreadableFile"] += 1
        return {"slice": "classical", "runs": _empty_runs()}
    for line in header.splitlines():
        if line.startswith("Analyst:") and any(
                sub in line for sub in WIR_EXCLUDE_ANALYST_SUBSTRINGS):
            drops["excludedAnalyst"] += 1
            return {"slice": "classical", "runs": _empty_runs()}
    # format must be explicit: the real corpus's analysis.txt files are not
    # auto-detected as RomanText (music21 raises ConverterFileException).
    # One malformed file must not kill a whole-corpus build: degrade to an
    # empty row and count it.
    try:
        score = converter.parse(path, format="romanText")
    except Exception:
        drops["unreadableFile"] += 1
        return {"slice": "classical", "runs": _empty_runs()}
    runs = _empty_runs()
    current_key, figures = None, []
    for rn in score.recurse().getElementsByClass("RomanNumeral"):
        k = getattr(rn, "key", None)
        if not isinstance(k, m21key.Key) or k.mode not in ("major", "minor"):
            # Non-Key or non-major/minor mode: this numeral is unusable, but
            # do NOT glue the figures on either side of it into one run.
            drops["keyConfidence"] += 1
            if current_key is not None:
                _append_run(runs[current_key[1]], figures, drops)
                figures = []
            continue
        key_id = (k.tonic.pitchClass, k.mode)
        if key_id != current_key:
            if current_key is not None:
                _append_run(runs[current_key[1]], figures, drops)
            current_key, figures = key_id, []
        quality = match_quality(rn.root().pitchClass,
                                {p.pitchClass for p in rn.pitches})
        figure = (render_numeral(rn.root().pitchClass, quality,
                                 key_id[0], key_id[1])
                  if quality else None)
        if figure is None:
            drops["unspellable"] += 1
            _append_run(runs[current_key[1]], figures, drops)
            figures = []
        elif not figures or figures[-1] != figure:
            figures.append(figure)
    if current_key is not None:
        _append_run(runs[current_key[1]], figures, drops)
    return {"slice": "classical", "runs": runs}


def wir_rows(root_dir, drops):
    for sub in WIR_OPEN_SUBCORPORA:
        base = os.path.join(root_dir, sub)
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames
                            if not d.endswith(WIR_EXCLUDE_DIR_SUFFIXES)]
            for name in filenames:
                if any(sub_ in name for sub_ in WIR_EXCLUDE_FILE_SUBSTRINGS):
                    continue
                if (name.endswith(".rntxt")
                        or (name.startswith("analysis") and name.endswith(".txt"))):
                    yield wir_row(os.path.join(dirpath, name), drops)


def _iter_rows(args, drops):
    any_source = False
    if args.rows_json:
        any_source = True
        with open(args.rows_json, encoding="utf-8") as f:
            yield from json.load(f)
    if args.billboard_dir:
        any_source = True
        yield from billboard_rows(args.billboard_dir, drops)
    if args.wir_dir:
        any_source = True
        yield from wir_rows(args.wir_dir, drops)
    if not any_source:
        raise ValueError("provide --rows-json, --billboard-dir, and/or --wir-dir")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Build chord_model.json from open corpora")
    ap.add_argument("--rows-json", default=None)
    ap.add_argument("--billboard-dir", default=None)
    ap.add_argument("--wir-dir", default=None)
    ap.add_argument("--out", default="chord_model.json")
    ap.add_argument("--min-corr", type=float, default=0.55)
    ap.add_argument("--min-margin", type=float, default=0.03)
    ap.add_argument("--min-count", type=int, default=2)
    ap.add_argument("--min-bucket", type=int, default=50)
    ap.add_argument("--top-loops", type=int, default=50)
    ap.add_argument("--seed", type=int, default=20260711)
    ap.add_argument("--gzip", default="auto", choices=["auto", "always", "never"])
    ap.add_argument("--corpus", default="unknown")
    ap.add_argument("--revision", default=None)
    ap.add_argument("--license", default="unknown")
    ap.add_argument("--attribution", default="unknown")
    args = ap.parse_args(argv)
    try:
        drops = new_drops()
        params = {"min_corr": args.min_corr, "min_margin": args.min_margin,
                  "min_count": args.min_count, "min_bucket": args.min_bucket,
                  "top_loops": args.top_loops, "seed": args.seed,
                  "corpus": args.corpus, "revision": args.revision,
                  "license": args.license, "attribution": args.attribution}
        model = build_model(_iter_rows(args, drops), params, drops=drops)
        raw = json.dumps(model, separators=(",", ":"), sort_keys=True)
        out_path = args.out
        use_gzip = args.gzip == "always" or (
            args.gzip == "auto" and len(raw.encode()) > 3_000_000)
        if use_gzip:
            import gzip as _gzip
            out_path = args.out if args.out.endswith(".gz") else args.out + ".gz"
            with _gzip.open(out_path, "wt", encoding="utf-8") as f:
                f.write(raw)
        else:
            with open(out_path, "w", encoding="utf-8") as f:
                f.write(raw)
        result = {"written": out_path, "bytes": len(raw.encode()),
                  "gzipped": use_gzip, "provenance": model["provenance"]}
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
