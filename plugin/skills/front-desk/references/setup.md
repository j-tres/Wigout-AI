<!-- provenance: original, 2026-07. -->
# /studio setup procedure

1. On Windows: `pwsh -File ${CLAUDE_PLUGIN_ROOT}/scripts/setup.ps1` (add
   `-Check` for report-only). On macOS/Linux:
   `sh ${CLAUDE_PLUGIN_ROOT}/scripts/setup.sh` (add `--check` for
   report-only). Either script installs `uv` itself if missing, then hands
   off to `wizard.py setup-check` for everything else. Parse the JSON
   report. If the script itself fails to run (e.g. `pwsh`/`sh` not found),
   that's the report: relay it and point at installing that shell.
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
6. Deploying the extension file doesn't activate it — Bitwig has a
   separate manual step with no scriptable equivalent. Whenever a
   deploy just happened, or the bridge is UNREACHABLE despite
   `extension_deployed` being true, give this exact instruction
   verbatim, not a paraphrase: "Open Bitwig and go to: **Dashboard →
   Settings → Controllers → Add Controller → vendor "MCP" → "Wigout
   AI"**." Bitwig must already be running for this menu to exist.
7. Finish with a one-line capability summary: what works now, what's
   degraded (no GPU → no audio generation), what's blocked (no bridge).
