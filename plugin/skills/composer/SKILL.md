---
name: composer
description: >
  Composer for Bitwig: builds tracks with MIDI notes, instruments, and
  generated audio. Use for "write/compose/add a melody/bassline/chords",
  "convert this clip to MIDI", "make a track that...", or /compose.
  MIDI-first; audio generation via ACE-Step when available.
---

# Composer

First read `${CLAUDE_PLUGIN_ROOT}/skills/bitwig-project/SKILL.md`.
For genre/structure/arrangement decisions consult `references/`
(composition-craft KB). For key/harmony decisions consult the
`music-theory` skill — compute, don't recall.

## MIDI-first workflow (the default)
1. Read project context (`bw_snapshot` of tracks + transport: tempo, key
   hints from existing clips).
2. Create/select the target track; insert an instrument via a popup
   browser session (`bw_call` browser flow) matching the requested
   timbre — check
   `${CLAUDE_PLUGIN_ROOT}/skills/coach/references/devices-instruments.md`
   for stock-device picks by timbre (single source of truth; authored in
   the Bitwig-mastery KB task).
3. Write notes via `cursorClip` step calls. Beats→steps: read the clip's
   step size; step x = round(start_beats / step_beats).
4. Verify (`verified` flags + read-back), then decision-log the action
   group.

## Clip→MIDI (flagship flow)
User has reference audio on a track (their hum, a melody idea) and asks
e.g. "analyze the clip I have selected and convert to MIDI with violin":
1. Resolve the clip to its audio file via the project-audio ladder
   (bitwig-project skill). On miss, ask — never guess a path.
2. Transcribe:
   `cd plugin/scripts && uv run python hum2midi.py <file> --mode mono
   --bpm <project tempo> --out-json notes.json`
   (mono/pyin for hummed melodies; `--mode poly` for chords/piano).
3. Sanity-check the result with `music-theory` (`key` command). NOTE the
   two tools use DIFFERENT JSON shapes — `hum2midi` emits
   `{"bpm","mode","events":[{"pitch","start_beats","duration_beats",...}]}`
   but `theory_engine` consumes `{"notes":[{"pitch","start","duration"}]}`.
   Map each event → `{"pitch": e.pitch, "start": e.start_beats,
   "duration": e.duration_beats}` before piping to the engine. Show the
   user: N notes, key, span. Fix octave errors before writing.
4. Create the target track, insert the requested instrument. There is NO
   stock "Violin" device — resolve the timbre via
   `${CLAUDE_PLUGIN_ROOT}/skills/coach/references/devices-instruments.md`
   (violin/strings → Sampler + a string multisample if available, else
   Polymer as the honest fallback; browse for a string preset when a
   specific patch matters). Then write the notes — beat→step is
   `x = round(start_beats / step_beats)` (default step 0.25 beat → ×4);
   write with `cursorClip.setStep(channel, x, y, velocity_0_127,
   duration_beats)`. Verify by reading `cursorClip.notes` back
   (NoteOn cells), then decision-log. Never oversell the fallback as a
   real violin — say what instrument actually landed.

## Audio generation path
For crafting the caption/lyrics/tags and choosing generate vs cover vs
repaint, consult `references/acestep-prompting.md` first.
Preference order — state which one is active and why:
1. Installed `claude-music` plugin (ACE-Step front-end): delegate to
   `/music generate`, `/music cover`, `/music repaint`.
2. ACE-Step 1.5 REST API at `http://localhost:8001` if the user runs
   `acestep-api` (check with a GET; don't assume).
3. Neither available → say so; offer the MIDI-first path (needs no GPU).
Generated audio lands as a file; getting it INTO Bitwig: try
`InsertionPoint.insertFile` per the live-verified recipe in
`references/audio-import.md` (written after the Task 16 probe); until
verified, ask the user to drag the file in — say why.

## Honesty
Report `verified` flags as-is. If an instrument/browser step lands on an
unexpected device, say what actually happened before continuing.
