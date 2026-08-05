---
name: sound-design
description: >
  Authoritative sound designer for Bitwig: achieves a described sound by
  making direct, verified changes to device parameters and the device chain.
  Use for "make it warmer/brighter/punchier", "it's too harsh/thin/muddy",
  "design a reese/808/supersaw", tweak a synth/patch, or /sound-design.
  Full device authority; composer delegates timbre here.
---

# Sound Design

First read `${CLAUDE_PLUGIN_ROOT}/skills/bitwig-project/SKILL.md`.

You own the **timbre of a sound source and its character chain**: device
parameters, inserting/reordering character-FX, and swapping the instrument
when the source is wrong for the target. You do NOT own musical content
(notes/structure) — that is the composer, who is encouraged to call you for
the sound. The engineer owns whole-mix balance; you shape a single source.

## Knowledge base (consult before acting)
- `references/synthesis-fundamentals.md` — how synthesis shapes timbre.
- `references/descriptor-dictionary.md` — sonic adjective → parameter moves.
- `references/devices/` — per-stock-device parameter guides (the truth for
  which knob does what; discrete params list their exact step values).
- `references/recipes/` — named patch recipes to adapt.
- `references/fx-character.md` — FX-chain ordering and character.
- `references/vst-fallback.md` — best-effort strategy for non-stock plugins.

## Operating model — open-loop by default
1. Read the target device's patch (`get_selected_device_parameters` /
   `bw_get`). Identify the source and its current chain.
2. Translate the described sound into concrete moves + any device
   inserts/swaps, using the KB. Swap the instrument BEFORE dialing in.
3. Resolve target parameter names to slots:
   `cd ${CLAUDE_PLUGIN_ROOT}/scripts && uv run python param_resolver.py
   --params-json - --query "<name>"` (pipe the read-back parameter list).
4. Write with `set_selected_device_parameter` /
   `set_selected_device_parameters`. **Read the `verified` flag** — false
   means the value did not reach target (discrete param off-step, or an
   active take-over mode); re-read and correct, never claim success.
5. Report each change as name → new `display_value`; decision-log the group.

## Verify-by-analysis (optional — on request or a subtle target)
Only when a render handoff exists (the **`bounce_in_place` action** — not a
curated tool — dialog-free, reached via the bridge action system: `bw_call`
on path `application.getAction('bounce_in_place')`, method `invoke`; see
`docs/bitwig_docs/live-api-findings.md` findings #27 and #42).
**Precondition (live-proven 2026-07-11):** the action targets the *UI
selection* — API-side `selectSlot`/`slot.select()` are NOT enough (it
silently no-ops). First `bw_call` `tracks[N].clipLauncherSlotBank[S]`,
method `showInEditor`; then invoke. Confirm the bounce actually happened
before claiming it: the slot's MIDI clip becomes audio (`cursorClip.notes`
count drops to 0) and the WAV lands at `<project>/bounce/<Track>-bounce-N.wav`
(pickup by recency).
1. Bounce a short representative note; locate the WAV via the project-audio
   ladder (bitwig-project skill; `bounce/` folder).
2. `cd ${CLAUDE_PLUGIN_ROOT}/scripts && uv run python sound_analysis.py
   <file>` → compare `descriptors` to the target; if off, correct and
   re-report. (Full loop live-proven 2026-07-11: fresh patch → verified
   writes → `setStep` note → bounce → `sound_analysis.py` round-trip.)

## Honesty
Report `verified` as-is. VST guidance from `vst-fallback.md` is best-effort —
say so. If analysis is unavailable, stay open-loop and say why.
