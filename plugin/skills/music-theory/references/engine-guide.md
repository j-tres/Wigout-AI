<!-- provenance: original distillation by Wigout Studio authors, 2026-07. Engine outputs generated from theory_engine.py at authoring time. -->
# Theory Engine Guide

Every answer below is a **real invocation, run at authoring time, with the
actual pasted output** — not recalled theory. Run the engine yourself for
anything not shown here; never guess.

    cd plugin/scripts
    uv run python theory_engine.py <command> ...

Six subcommands: `key`, `roman`, `scale`, `chord`, `progression`,
`voicecheck`. All I/O is JSON.

## Notes JSON schema

Commands that take `--notes-json` (`key`, `roman`, `voicecheck`) expect:

```json
{"notes": [{"pitch": 60, "start": 0.0, "duration": 1.0}]}
```

- `pitch` — MIDI note number (60 = C4).
- `start` — onset in beats (quarter notes), from the start of the clip.
- `duration` — length in beats.

`--notes-json` takes a file path or `-` for stdin (pipe a JSON string in).
To analyze a Bitwig clip, read notes via the bridge and map them into this
schema first — see `bridge-note-mapping.md`.

## `key` — infer the key from a set of notes

Input (`cadence.json`, a I–IV–V triad cadence in C major, one triad per
beat):

```json
{"notes": [
  {"pitch": 60, "start": 0.0, "duration": 1.0},
  {"pitch": 64, "start": 0.0, "duration": 1.0},
  {"pitch": 67, "start": 0.0, "duration": 1.0},
  {"pitch": 65, "start": 1.0, "duration": 1.0},
  {"pitch": 69, "start": 1.0, "duration": 1.0},
  {"pitch": 72, "start": 1.0, "duration": 1.0},
  {"pitch": 67, "start": 2.0, "duration": 1.0},
  {"pitch": 71, "start": 2.0, "duration": 1.0},
  {"pitch": 74, "start": 2.0, "duration": 1.0}
]}
```

```
$ uv run python theory_engine.py key --notes-json cadence.json
{
  "key": "C major",
  "confidence": 0.901,
  "alternatives": [
    "F major",
    "A minor",
    "G major"
  ]
}
```

A cadence disambiguates. A bare, unordered scale does not — see
`interpretation.md` for the real A-minor-vs-C-major example and the
confidence-threshold rule of thumb.

## `roman` — Roman-numeral analysis of note slices

Same cadence, explicit key:

```
$ uv run python theory_engine.py roman --notes-json cadence.json --key "C major"
{
  "key": "C major",
  "analysis": [
    {
      "start": 0.0,
      "chord": "C-major triad",
      "roman": "I"
    },
    {
      "start": 1.0,
      "chord": "F-major triad",
      "roman": "IV"
    },
    {
      "start": 2.0,
      "chord": "G-major triad",
      "roman": "V"
    }
  ]
}
```

Omit `--key` and the engine infers it first (`notes_to_stream(...).analyze("key")`)
— same result here since the cadence is unambiguous:

```
$ uv run python theory_engine.py roman --notes-json cadence.json
{
  "key": "C major",
  "analysis": [
    {"start": 0.0, "chord": "C-major triad", "roman": "I"},
    {"start": 1.0, "chord": "F-major triad", "roman": "IV"},
    {"start": 2.0, "chord": "G-major triad", "roman": "V"}
  ]
}
```

For an inversion example (figured-bass suffix like `I6`), see
`interpretation.md`.

## `scale` — spell a scale

```
$ uv run python theory_engine.py scale D dorian
{
  "scale": "D dorian",
  "pitches": [
    "D",
    "E",
    "F",
    "G",
    "A",
    "B",
    "C"
  ],
  "midi": [
    50,
    52,
    53,
    55,
    57,
    59,
    60
  ]
}
```

Modes available: `major`, `minor`, `harmonic-minor`, `melodic-minor`,
`dorian`, `phrygian`, `lydian`, `mixolydian`, `locrian`.

## `chord` — spell a chord symbol

```
$ uv run python theory_engine.py chord Cmaj7
{
  "symbol": "Cmaj7",
  "pitches": [
    "C3",
    "E3",
    "G3",
    "B3"
  ],
  "midi": [
    48,
    52,
    55,
    59
  ]
}
```

`symbol` is any string `harmony.ChordSymbol` (music21) understands — e.g.
`Cmaj7`, `Dm7`, `G7`, `F#dim7`, `Bbsus4`.

## `progression` — realize Roman numerals in a key

```
$ uv run python theory_engine.py progression --key "A minor" --numerals i,VI,III,VII
{
  "key": "A minor",
  "progression": [
    {
      "roman": "i",
      "chord": "A-minor triad",
      "midi": [69, 72, 76]
    },
    {
      "roman": "VI",
      "chord": "F-major triad",
      "midi": [77, 81, 84]
    },
    {
      "roman": "III",
      "chord": "C-major triad",
      "midi": [72, 76, 79]
    },
    {
      "roman": "VII",
      "chord": "G-major triad",
      "midi": [79, 83, 86]
    }
  ]
}
```

`--numerals` is a comma-separated figure list (no spaces needed around
commas); each figure is parsed by `roman.RomanNumeral(fig, key)`.

## `voicecheck` — parallel fifths/octaves check

Input: two triads a step apart, both voices moving in the same direction,
interval staying a perfect fifth (C4–G4 → D4–A4):

```json
{"notes": [
  {"pitch": 60, "start": 0.0, "duration": 1.0},
  {"pitch": 67, "start": 0.0, "duration": 1.0},
  {"pitch": 62, "start": 1.0, "duration": 1.0},
  {"pitch": 69, "start": 1.0, "duration": 1.0}
]}
```

```
$ uv run python theory_engine.py voicecheck --notes-json parallel_fifths.json
{
  "issues": [
    {
      "slice": 0,
      "voices": [0, 1],
      "issue": "parallel fifths",
      "from": [60, 67],
      "to": [62, 69]
    }
  ],
  "clean": false
}
```

`clean: true` and an empty `issues` list mean nothing was flagged. See
`interpretation.md` for how advisory this check is, and its blind spot
around crossed voices.

## Error contract

On bad input, every subcommand prints `{"error": "<ExceptionType>: <message>"}`
to stdout and exits **1** — no traceback, no partial JSON. Demonstrated with
the empty-notes case:

```json
{"notes": []}
```

```
$ uv run python theory_engine.py key --notes-json empty_notes.json
{"error": "ValueError: notes JSON must contain a non-empty 'notes' list"}
$ echo $?
1
```

The same contract covers a malformed file (missing `"notes"` key), an
unparseable `--key` string, an unknown chord symbol, etc. — always check
the JSON for an `"error"` key (or the exit code) before trusting a result;
never treat truncated/error output as a theory answer.

`--notes-json -` reads from stdin instead of a file, useful for piping a
freshly-built notes JSON straight in without a temp file:

```
$ cat cadence.json | uv run python theory_engine.py key --notes-json -
{
  "key": "C major",
  "confidence": 0.901,
  "alternatives": ["F major", "A minor", "G major"]
}
```
