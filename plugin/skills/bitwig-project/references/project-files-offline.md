<!-- provenance: original, per spec 2026-07-11-offline-project-readers-design.md §Docs. -->
# Project Files Offline (DAWproject + .bwproject)

File-based project context for when the bridge can't help: Bitwig is
closed, the question is arrangement-level (timeline clips, automation
curves, the full nested track tree in one read), or you are cataloging
a project library on disk.

## Staleness rule (normative)
File context is a snapshot "as of `fileModified`" (every script output
carries it). ALWAYS label file-derived facts with that timestamp, and
NEVER silently mix them with live bridge reads — when both sources
appear in one answer, attribute each fact to its source. On any
conflict, the live bridge wins.

## DAWproject exports (.dawproject) — the robust path
Bitwig 5.0.9+ exports its open interchange format via
**File → Export DAWproject…** (see "Agent-triggered export" below for
whether you can invoke it yourself). The file is a ZIP with
`project.xml`; `dawproject_read.py` (stdlib-only) reads it.

From `plugin/scripts/`:
- Digest (track tree, devices, clip counts, tempo, markers, scenes):
  `uv run python dawproject_read.py digest <file.dawproject>`
- Notes of a track (optionally one clip):
  `uv run python dawproject_read.py notes <file> --track "Bass" [--clip "Bassline A"]`
- Automation lanes / points of a track:
  `uv run python dawproject_read.py automation <file> --track "Bass" [--parameter channelVolume]`

Tracks are addressed BY NAME (same rule as the bridge; ambiguous or
unknown names error out listing what exists). Times are in the file's
native unit (beats); the digest carries tempo for conversion.
Caveat: exports have gaps — e.g. automation of missing plugins is
dropped (upstream dawproject issue #82). The reader reports what is
present, never more.

## Unexported .bwproject — heuristic fallback only
`.bwproject` is proprietary binary; no reliable parser exists anywhere.
When no DAWproject export is available:
  `uv run python bwproject_scan.py <file.bwproject>`
returns string-evidence buckets (`samplePaths`, `pluginHints`, `other`)
plus a Bitwig version hint. Output carries `fidelity: "heuristic"` —
treat every entry as a hint, present it as such, and prefer an export
or the live bridge for anything that matters. Locate the file via the
project-folder layout in `references/project-audio-access.md`.

## Agent-triggered export
Live-verified (finding #36): `application.getAction('export_project')`
resolves ("Export DAWproject…"). It opens a MODAL save dialog — never
invoke it without the user present and consenting (finding #27 rule).
Flow, per the finding-#33 pattern: take a pre-listing of the target
folder, invoke the action over `bw_call`, the user completes the dialog
once, find the new `.dawproject` by recency, then read it with
`dawproject_read.py`. Bitwig writes the export to
`<projects_root>/<Project>/<Project>/<Project>.dawproject` by default.
Note the digest reads STATIC channel values: a track can show
`channel.volume 0.0` while riding a Volume automation lane up from zero
(finding #37) — read the `automation` lane when the static value looks
wrong. Group tracks return only their own arrangement content, not their
children's (finding #38).
