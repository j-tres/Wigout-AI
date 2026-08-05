# Wigout Studio

AI studio team for Bitwig Studio, driving the Wigout MCP bridge
(`http://localhost:61169/mcp`). Roles: front-desk (router), coach
(teaches, read-only), composer (MIDI + generated audio), music-theory
(music21-backed authority), engineer (v1 scaffolding). See
`reference/ROLE_INDEX.md` for routing and
`docs/superpowers/specs/2026-07-09-wigout-studio-plugin-design.md` for the design.

Setup: run `/studio setup` (wraps `scripts/setup.ps1`). Python engines are
uv-managed in `scripts/` (Python 3.10 pinned). Optional external tools the
setup reports on: ffmpeg (spectrograms; loudness works without it via
pyloudnorm), an NVIDIA GPU + `claude-music`/ACE-Step (audio generation —
MIDI paths need neither).

## What it does (live-verified examples)

- **Compose in a key:** "write a 4-bar melody in D dorian on a warm pad" →
  a new track with Polymer and 16 in-scale notes, key-checked by the
  music21 engine.
- **Turn a hum into an instrument (flagship):** put a melody idea on a
  track, select the clip, and say "convert the clip I selected to MIDI
  with violin" → the clip is transcribed (pyin/basic-pitch), sanity-checked
  for key, and written onto a new instrument track. (No stock "Violin"
  device exists — the composer picks the closest string timbre and says
  which instrument it actually used.)
- **Check loudness:** "how does my master compare to Spotify?" → an
  integrated-LUFS / peak report against the -14 LUFS-I target, from a
  bounce in the project's `bounce/` folder.

See `docs/superpowers/specs/2026-07-09-wigout-studio-plugin-design.md` for
scope and the v2 roadmap (full mix/master feedback loop + render handoff).
