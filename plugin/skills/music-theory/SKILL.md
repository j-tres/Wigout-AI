---
name: music-theory
description: >
  Authoritative music theory: key inference, Roman-numeral analysis,
  scale/chord/progression spelling, voice-leading checks — computed by
  music21, not recalled — plus corpus-grounded progression statistics
  (next-chord odds, common progressions, substitution diagnosis).
  Use for any theory question, when composing needs harmonic decisions,
  or /theory. Other roles consult this skill rather than answering
  theory from memory.
---

# Music Theory Authority

Theory answers are COMPUTED, not recalled. The engine:

    cd plugin/scripts
    uv run python theory_engine.py <command> ...

| Need | Command |
|---|---|
| What key are these notes in? | `key --notes-json <file or ->` |
| Label the harmony | `roman --notes-json <f> [--key "C major"]` |
| Spell a scale | `scale D dorian` |
| Spell a chord symbol | `chord Cmaj7` |
| Realize a progression | `progression --key "A minor" --numerals i,VI,III,VII` |
| Check voice leading | `voicecheck --notes-json <f>` |

Notes JSON schema: `{"notes": [{"pitch": 60, "start": 0.0, "duration": 1.0}]}`
(pitch = MIDI, start/duration in beats). To analyze a Bitwig clip: read
notes via the bridge (`cursorClip.notes`), map to this schema per
`references/bridge-note-mapping.md`, feed the engine.

Interpretation guidance (confidence thresholds, when the producer's ear
outranks the analysis): `references/interpretation.md`.
Engine details and examples: `references/engine-guide.md`.

If the engine errors or is not installed, SAY SO and recommend
`/studio setup` — do not silently fall back to recalled theory for
anything the engine computes.

## Corpus statistics (what real songs do)

Usage statistics from the prebuilt corpus model — computed, not
recalled:

    cd plugin/scripts
    uv run python chord_stats.py <subcommand> ...

| Need | Command |
|---|---|
| Ranked next-chord continuations | `next --context "i,VI" --mode minor [--genre pop] [--top 5]` |
| Most-common progressions | `progressions --mode major [--genre pop] [--length 4] [--top 10]` |
| Commonness of a progression + substitutions | `diagnose --numerals "I,V,vi,IV" --mode major [--genre pop]` |

`--mode` is required (major|minor). `--genre` names a corpus slice —
`pop` (McGill Billboard) or `classical` (When in Rome) — omit it for
the combined global bucket. These are USAGE COUNTS from small,
era-skewed corpora — relay each response's `limits` array honestly and
cite counts as counts, never as taste. Realize any suggestion into MIDI
with `theory_engine.py progression --key ... --numerals ...` before
handing to composer. Cookbook, slices, and caveats:
`references/corpus-stats.md`. If the model file is missing or errors,
SAY SO — never substitute recalled statistics.
