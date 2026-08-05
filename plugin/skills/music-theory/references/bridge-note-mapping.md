<!-- provenance: original distillation by Wigout Studio authors, 2026-07. Bridge numbers below were captured live against a running Bitwig instance at authoring time (create clip -> write 4 notes -> read cache -> delete clip). -->
# Bridge <-> Engine Note Mapping

The theory engine speaks one schema (`{"pitch", "start", "duration"}`,
beats). The bridge speaks a different one (a sparse per-grid-step cache,
`{"channel", "x", "y", ...}`). This page is the exact, verified arithmetic
to cross between them, in both directions, plus the one landmine that
makes the arithmetic non-obvious: the clip's step size isn't something you
can read back.

See also `plugin/skills/bitwig-project/references/bridge-landmines.md`
items 5–6 (deep access needs the cursor re-pointed first; `cursorClip.notes`
is the observer cache, `cursorClip.step(ch,x,y)` addresses one step).

## The step size: fixed at 0.25 beats (1/16 note) — not readable live

The bridge's `cursorClip` is created once at extension init as
`cursorTrack.createLauncherCursorClip(128, 128)` — a 128-step x 128-key
window. The comment in the facade names this "8 bars of 16ths per window":
128 steps / 8 bars / 4 beats-per-bar = **4 steps per beat = 0.25 beats per
step**. This is a fixed property of *this bridge's* grid, independent of
whatever grid resolution Bitwig's own clip-editor UI shows for the clip.

**Landmine:** the Bitwig API has no getter for this. `Clip` exposes a
write-only `setStepSize(beats)` with no matching `getStepSize()` — there
is nothing to `bw_get` and read it back live. Treat `step_beats = 0.25` as
a fixed constant of the bridge (confirmed empirically below), not a
per-clip value you look up. If the extension's grid constants
(`BRIDGE_NOTE_GRID_WIDTH`/`HEIGHT`) ever change, this constant changes
with them — 128 steps over 8 bars is the derivation, not magic.

## Bridge -> engine: reading a clip

`cursorClip.notes` returns the sparse observer cache: **one cache entry
per grid cell a note occupies**, not one entry per note. A note lasting
more than one step produces a `NoteOn` entry at its start step plus a
`NoteSustain` entry for every following step it still occupies. Always
filter `state == "NoteOn"` — the `NoteSustain` rows are the same note's
tail, not new notes.

Each entry (`ValueReader.noteStepMap`, `extension` codebase) carries:
`channel`, `x`, `y`, `state`, `velocity` (0..1, already normalized),
`release_velocity`, `duration` (**already in beats** — do not multiply it
by `step_beats` again), plus `pan`/`timbre`/`pressure`/`gain`/`transpose`/
`chance`.

Mapping (`NoteOn` rows only):

```
pitch    = y
start    = x * step_beats            # step_beats = 0.25 here
duration = entry["duration"]         # already beats; independently equals
                                      # (count of NoteOn + trailing NoteSustain
                                      # cells at this y) * step_beats
```

### Worked example (real, 4 notes)

Written live via `bw_call cursorClip.setStep(channel, x, key, velocity, duration_beats)`:

```
setStep(0, 0, 60, 100, 1.0)
setStep(0, 4, 64, 90,  0.5)
setStep(0, 6, 67, 80,  0.25)
setStep(0, 8, 72, 110, 1.0)
```

`bw_get cursorClip.notes` read back (trimmed to the `NoteOn` rows; real
values, `velocity` = MIDI/127):

```json
{"notes": [
  {"channel": 0, "x": 0, "y": 60, "state": "NoteOn", "velocity": 0.787401556968689, "duration": 1.0},
  {"channel": 0, "x": 4, "y": 64, "state": "NoteOn", "velocity": 0.7086614370346069, "duration": 0.5},
  {"channel": 0, "x": 6, "y": 67, "state": "NoteOn", "velocity": 0.6299212574958801, "duration": 0.25},
  {"channel": 0, "x": 8, "y": 72, "state": "NoteOn", "velocity": 0.8661417365074158, "duration": 1.0}
]}
```

The full dump also included the `NoteSustain` tail rows — `x=1,2,3` for
the first note (4 cells total = 1.0 beat / 0.25 = 4 steps, confirming
`step_beats`), `x=5` for the second (2 cells = 0.5 beat), none for the
third (1 cell = 0.25 beat, exactly one step), and `x=9,10,11` for the
fourth (4 cells = 1.0 beat). Every count matches `duration / step_beats`
exactly — the two ways of deriving duration (the field directly, or
counting grid cells) agree.

Applying `pitch = y`, `start = x * 0.25`, `duration = duration` gives the
engine-schema notes JSON:

```json
{"notes": [
  {"pitch": 60, "start": 0.0, "duration": 1.0},
  {"pitch": 64, "start": 1.0, "duration": 0.5},
  {"pitch": 67, "start": 1.5, "duration": 0.25},
  {"pitch": 72, "start": 2.0, "duration": 1.0}
]}
```

Feed this straight to `theory_engine.py key`/`roman`/`voicecheck`
(velocity and the other NoteStep fields aren't part of the engine schema —
theory doesn't care how hard a note was hit).

## Engine/hum2midi -> bridge: writing notes back

Neither `theory_engine.py`'s notes JSON nor `hum2midi.py`'s event schema
(`{"pitch", "start_beats", "duration_beats"}`) carries a velocity field —
`hum2midi`'s pitch trackers don't estimate loudness reliably enough to
report it. Pick a default velocity (e.g. 100) or ask the producer, unless
you're editing an existing note that already has one.

Mapping, per note:

```
x = round(start_beats / step_beats)   # step_beats = 0.25
y = pitch
```

**Writing a brand-new note** (nothing at that cell yet): call
`setStep` directly on `cursorClip` — this is the same call used to build
the worked example above:

```
bw_call { "path": "cursorClip", "method": "setStep",
          "args": [channel, x, y, velocity_0_127, duration_beats] }
```

**Editing an existing note**: navigate to the single-cell path
`cursorClip.step(channel, x, y)` and call a setter on the resolved
`NoteStep` (`setVelocity`, `setDuration`, `setChance`, etc. — all take
values already in the same units `noteStepMap` reports, e.g. velocity
0..1, duration in beats):

```
bw_call { "path": "cursorClip.step(0, 4, 64)", "method": "setDuration",
          "args": [0.75] }
```

`cursorClip.step(ch,x,y)` does not create a note — it resolves to the
cached cell (or `Empty` if there is none) for reading or for chaining a
setter onto an existing one. `setStep` on the clip root is what creates a
note where there wasn't one.

### Worked example, reversed (same 4 notes, round-trip)

Taking the engine-schema notes JSON produced above and converting back to
bridge writes reproduces the exact steps used to create it — the
arithmetic is symmetric:

| pitch (y) | start (beats) | x = start / 0.25 | duration (beats) |
|---|---|---|---|
| 60 | 0.0 | 0 | 1.0 |
| 64 | 1.0 | 4 | 0.5 |
| 67 | 1.5 | 6 | 0.25 |
| 72 | 2.0 | 8 | 1.0 |

```
bw_call cursorClip.setStep(0, 0, 60, 100, 1.0)
bw_call cursorClip.setStep(0, 4, 64, 90,  0.5)
bw_call cursorClip.setStep(0, 6, 67, 80,  0.25)
bw_call cursorClip.setStep(0, 8, 72, 110, 1.0)
```

(Velocities 100/90/80/110 here are the ones chosen for the original
worked example — since neither engine nor hum2midi output carries
velocity, a real round trip from engine output alone would need a chosen
default instead.)

All four `bw_call` mutations report `verified: false` per the project's
honest-mutation convention (Cycle-1 async convention, deliberately kept
for `NoteStep` setters per Decision 3) — confirm with a follow-up
`bw_get cursorClip.notes` or `cursorClip.step(ch,x,y)`, exactly as done
above.
