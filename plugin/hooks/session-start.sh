#!/bin/bash
# SessionStart hook: auto-deploys the Bitwig extension into Bitwig's
# Extensions folder on first use, silent on every session after that.
# See docs/superpowers/specs/2026-07-31-open-source-distribution-design.md
# section G (in the wigout-internal repo) for the full design.
#
# Never blocks session start: `cd ... || exit 0` and the trailing `|| true`
# both swallow failures (uv missing, offline, no release published yet).
# stderr is discarded so uv's own status noise never leaks into the
# session's context -- only wizard.py's one-line message (if any) does.
cd "${CLAUDE_PLUGIN_ROOT}/scripts" || exit 0
uv run python wizard.py session-start-check 2>/dev/null || true
