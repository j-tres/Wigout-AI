---
name: bitwig-project
description: >
  Shared Bitwig/bridge literacy for all Wigout Studio roles: how to read
  project state, address the bridge, resolve project audio to files, and
  write decision-log entries. Load before any role acts on a project.
---

# Bitwig Project Literacy

Prerequisite skill for every Wigout Studio role. Rules here are normative.

## Bridge basics
- Tools: `bw_describe` / `bw_get` / `bw_set` / `bw_call` / `bw_snapshot`;
  resources `bitwig://api/index`, `bitwig://api/roots`, `bitwig://config/locations`.
- ALWAYS read `application.projectName` in the same batch as any mutation
  target identity (e.g. `tracks[0].name`) — the controller follows the
  FOCUSED project and focus can change between calls.
- Mutations return an honest `verified` flag. Report it as-is. Never claim
  success the flag does not support.
- Deep domains (drum pads, layers, nested chains, browser) are
  cursor-anchored: select first, then operate.
- Address tracks/groups by NAME, not a remembered bank index (the
  `tracks[]` window shifts; resolve name→index in the same batch, or use
  curated tools' `track_name`). The master is the `masterTrack` root, not
  `tracks[N]`. This is also how the user refers to tracks — mirror it.
- Read `references/bridge-landmines.md` before first bridge use in a
  session; it links the full evidence ledger.

## Project-audio access
Read `references/project-audio-access.md` when a task needs an audio file
from the project (clip analysis, transcription, loudness reports).

## Offline project files
Read `references/project-files-offline.md` when Bitwig is closed, when a
task needs arrangement-level detail (timeline clips, automation curves,
full track tree in one read), or when cataloging projects on disk.
File-derived context is stale — follow that reference's staleness rule.

## Decision log
Every role that ACTS (mutates the project or produces artifacts) appends
an entry to `.studio/decision-log.md` per `references/decision-log.md`.
The coach teaches from this log.
