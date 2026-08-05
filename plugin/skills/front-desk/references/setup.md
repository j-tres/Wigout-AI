<!-- provenance: original, 2026-07. -->
# /studio setup procedure

1. Run: `pwsh -File ${CLAUDE_PLUGIN_ROOT}/scripts/setup.ps1` (add `-Check`
   for report-only). Parse the JSON report.
2. Relay the report honestly — MISSING items with their install commands;
   don't soften "UNREACHABLE".
3. File locations: run
   `uv run python ${CLAUDE_PLUGIN_ROOT}/scripts/wizard.py scan` (from
   `${CLAUDE_PLUGIN_ROOT}/scripts`) and parse its JSON. Present
   `auto_detect_candidates` (if any) and the current `config.locations`
   values to the user; confirm the right paths with them — they may keep
   projects elsewhere, and auto-detect is Windows-only and best-effort.
   At minimum resolve `projects`; ask about the other location keys
   (`library`, `soundContent`, `music`, `audioAnalysisCache`,
   `controllerScripts`) only if the user's request needs them.
4. Once confirmed, persist with `wizard.py write-locations --projects
   "<path>" [--library "<path>" ...]` — this writes
   `~/.wigout-ai/config.json`, shared with the Java extension and usable
   outside Claude Code entirely. If a location is already set and
   unchanged, don't re-write it.
5. `scan`'s `extension_deployed` field reports whether
   `Wigout.bwextension` is present in the Bitwig Extensions folder. If
   false, mention it and offer `wizard.py deploy-extension --source
   <path-to-built-.bwextension>` (only if the user already has one
   built) — don't push a download; the GitHub-release download path
   lands with the public-repo release pipeline.
6. Finish with a one-line capability summary: what works now, what's
   degraded (no GPU → no audio generation), what's blocked (no bridge).
