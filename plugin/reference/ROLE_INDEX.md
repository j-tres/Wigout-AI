# Wigout Studio — Role Index

Routing authority for the front desk, slash commands, and inter-role
delegation. Keep in lockstep with `skills/` and `commands/`
(enforced by `scripts/tests/test_role_index.py`).

## Decision tree — "I need to..."

| I need to... | Route to |
|---|---|
| learn how/why something works in Bitwig or production | /coach (`coach`) |
| learn WHILE work happens ("teach me as you go") | `coach` ride-along + the working role |
| write/add a melody, bassline, chords, drums | /compose (`composer`) |
| convert a clip/hum to MIDI with an instrument | /compose (`composer`, clip→MIDI flow) |
| generate audio from a description (ACE-Step) | /compose (`composer`, audio path) |
| shape/design a sound; "warmer/brighter/punchier"; "too harsh/thin" | /sound-design (`sound-design`) |
| tweak a synth patch / device parameters to taste | /sound-design (`sound-design`) |
| answer a theory question; analyze key/harmony | /theory (`music-theory`) |
| what chord usually comes next; progression odds | /theory (`music-theory` corpus stats) |
| how common is this progression; corpus-typical loops | /theory (`music-theory` corpus stats) → `composer` writes |
| review levels, routing, gain staging; balance/fix the mix | /mix (`engineer`) |
| "bass masks the kick"; carve space; stem masking | /mix (`engineer`) |
| check loudness; master toward a target; match a reference | /master (`engineer`) |
| set up or verify the environment | /studio setup |
| anything else musical | /studio (`front-desk` routes) |

## Role catalog

| Skill | What it does | Primary use case |
|---|---|---|
| `front-desk` | Routes intent to roles via this index | Natural-language entry, /studio |
| `coach` | Teaches against the real project; READ-ONLY | Learning; ride-along commentary |
| `composer` | MIDI, instruments, clip→MIDI, generated audio | Making musical material (delegates timbre to `sound-design`) |
| `sound-design` | Verified device-parameter + chain changes for timbre | Achieving a described sound |
| `music-theory` | music21-computed theory authority + corpus progression stats | Key/harmony/voice-leading decisions; progression evidence |
| `engineer` | Full mix/master authority: report cards, verified moves, render-verify loop | Balancing, masking fixes, loudness/mastering |
| `bitwig-project` | Shared bridge literacy + conventions | Prerequisite for every role above |

## Prerequisites

| Role | Before using |
|---|---|
| all roles | `bitwig-project` read; Bitwig running with project open |
| `composer` audio path | claude-music plugin or acestep-api (optional; degrades to MIDI) |
| `composer` clip→MIDI | `/studio setup` done (Python env); clip resolvable per audio ladder |
| `engineer` render-verify | a render per the render-handoff ladder (dialog-free if probed, else one-click Export Audio) |
| `engineer` stems/matchering | `/studio setup` groups: `uv sync --group stems` / `--group mastering` (optional; degrades to advice) |
| `sound-design` verify-by-analysis | `/studio setup` (Python env); a render (bounce) available |

## Common sequences
- **Hum to instrument:** `composer` (resolve → transcribe → `music-theory`
  sanity check → track + instrument + notes) — with `coach` ride-along if
  learning.
- **Compose with understanding:** `composer` acts → `coach` teaches from
  the decision log after each action group.
- **Master check:** `engineer` state review → (if needed) a render via the
  render-handoff ladder → loudness report card.
- **Compose then voice:** `composer` inserts the instrument and writes notes
  → `sound-design` dials in the patch/chain to the requested timbre.
- **Mix pass:** `engineer` report card → verified moves → render →
  re-measure (delta stated) — iterate until the card is clean.
- **Sits vs is:** `engineer` finds a source-timbre problem →
  `sound-design` reshapes the patch → `engineer` re-balances.

## Works together / avoid combining
- `coach` pairs with any acting role (it reads the decision log).
- `music-theory` is consultative — any role may call it inline.
- `composer` delegates timbre to `sound-design` rather than shaping devices itself.
- `engineer` owns how sounds SIT (balance/space/glue); `sound-design`
  owns what sounds ARE (timbre) — delegate across the boundary, both
  directions.
- AVOID: two acting roles mutating in the same exchange (composer +
  engineer); sequence them instead.
- AVOID: `coach` performing mutations "to demonstrate" — hard rule, hand
  off or instruct the user.
