<!-- provenance: original distillation by Wigout Studio authors, 2026-07. Engine outputs generated from theory_engine.py at authoring time. -->
# Reading the Engine's Answers

The engine computes; it does not decide. This page is about turning its
numbers into a call a producer can act on — and knowing when to stop
trusting the number and ask instead.

## Confidence thresholds for `key`

`key`'s `correlationCoefficient` (music21's key-fit score) becomes
`confidence`, 0–1. Rule of thumb:

| Confidence | Action |
|---|---|
| **> 0.85** | Trust it. State the key, move on. |
| **0.7 – 0.85** | State the key, but mention the top `alternatives` entry — it's plausible enough to be worth a second look. |
| **< 0.7** | Do not present one answer. Show 2–3 candidates from `alternatives` and ask the producer which one matches their intent. |

Three real runs, spanning all three bands:

**> 0.85 — trust it.** A I–IV–V cadence in C major:
```
{"key": "C major", "confidence": 0.901, "alternatives": ["F major", "A minor", "G major"]}
```
0.901 is unambiguous — say "C major" and continue.

**0.7–0.85 — mention the alternative.** An unordered, ascending C-major
scale (C D E F G A B C, one note per beat, no chords):
```
{"key": "A minor", "confidence": 0.824, "alternatives": ["C major", "F major", "G major"]}
```
This is the **relative-key ambiguity lesson** confirmed by direct testing:
a bare melodic pitch-set with no harmonic context is genuinely ambiguous
between a major key and its relative minor (same seven pitch classes,
different tonic). music21 picked **A minor** for a plain C-major scale
here, at 0.824 confidence, with C major as its own top alternative. Report
it as "A minor (or its relative C major — the notes alone don't say
which)," not as a flat, singular fact. A cadence removes the ambiguity (see
above): give the engine a chord progression or a melody over a bassline,
not a bare scale, whenever you need a confident key call.

**< 0.7 — present candidates, ask.** A full ascending chromatic run (all
twelve pitch classes, one per beat):
```
{"key": "B minor", "confidence": 0.0, "alternatives": ["B major", "B- minor", "B- major"]}
```
Confidence 0.0 means the note set carries no meaningful tonal center —
correctly so, a chromatic run isn't "in" a key. Don't report "B minor."
Say the material doesn't commit to a key and ask the producer what they
intend (or whether it's meant to be atonal/chromatic on purpose).

**Always surface `alternatives`**, even above 0.85 — it costs nothing to
mention and the field exists precisely so a low-stakes second opinion is
never hidden.

## Roman numerals and inversions

`roman`'s figure comes straight from `roman.romanNumeralFromChord`, so it
includes **figured-bass inversion suffixes**, not just the scale-degree
numeral:

| Figure | Meaning |
|---|---|
| `I`, `IV`, `V`, `i`, `vi`, ... | Root position — the numeral alone. |
| `I6` | **First inversion** — the chord's 3rd is in the bass. `6` is shorthand figured bass for a root-position chord voiced with its third on the bottom (a 6th above the bass instead of a root-position 5-3). |
| `I64` | **Second inversion** — the chord's 5th is in the bass (six-four chord). |
| `V7` | A seventh chord in root position (no inversion figure needed — `7` marks the chord as a seventh, not an inversion). |
| `V65`, `V43`, `V42` | Seventh chord inversions (1st/2nd/3rd), by the standard figured-bass digits. |

Confirmed live: voicing a C-major triad with **E in the bass** (E4-G4-C5)
against a following F-major triad:
```
{"key": "C major", "analysis": [
  {"start": 0.0, "chord": "C-major triad", "roman": "I6"},
  {"start": 1.0, "chord": "F-major triad", "roman": "IV"}
]}
```
Same chord (C major) as the root-position case in `engine-guide.md`, but
because the bass note is E, not C, the engine correctly reports `I6`
instead of `I`. Read the figure as "which chord tone is in the bass," not
just "which chord" — it changes how the progression sounds and moves.

## `voicecheck` is advisory, not a gate

The check flags parallel perfect fifths and octaves between two adjacent
note slices — a classical part-writing rule. In most modern genres
(pop, EDM, hip-hop, rock power chords, synth stacks) parallel fifths and
octaves are a **stylistic choice, not an error** — power chords *are*
parallel fifths. Treat every `voicecheck` hit as a flag to look at, never
a reason to auto-reject a part. Ask "does this sound right for the
genre?" before touching anything.

**Blind spot: it pairs voices by position, not identity.** The algorithm
compares chord tone *N* in one slice against chord tone *N* in the next
slice (same sorted-pitch index), for every pair of same-size slices. If
two voices **cross** between slices — the alto goes above what was the
soprano, say — the position-based pairing is now comparing the wrong
pair of physical voices to each other. It can silently miss a real
parallel fifth hiding behind the crossing, or flag a "parallel" that was
actually two different voices happening to land on that interval. Treat a
clean `voicecheck` result on a passage with voice crossing as
inconclusive, not confirmed-clean — re-check by ear.

## The producer's ear always outranks the engine

Every rule above is a heuristic for making a fast, defensible first call.
None of them override what the person making the music actually wants.
State findings plainly — "the engine reads this as A minor at 0.82
confidence, C major is the close second" or "this progression has
parallel fifths at bar 3, which is normal for this genre" — and let the
producer decide. The engine's job is to replace **recalled** theory with
**computed** theory, never to replace the producer's judgment.
