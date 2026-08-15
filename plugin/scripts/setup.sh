#!/bin/sh
# Wigout Studio environment setup (macOS/Linux). Idempotent. --check = report only.
#
# uv itself is the one piece that has to be OS-native: without it there's no
# way to run wizard.py, which is where everything else actually lives
# (ffmpeg/GPU/bridge checks, optional dependency groups). This script's only
# job is "make sure uv exists", then it hands off. See setup.ps1 for the
# Windows equivalent -- same split, different bootstrap.
#
# Reviewed, not live-verified: this project's only live-test machine is
# Windows (see docs/superpowers/specs in the internal planning repo).
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

CHECK_FLAG=""
if [ "$1" = "--check" ]; then
    CHECK_FLAG="--check"
fi

if ! command -v uv >/dev/null 2>&1; then
    curl -LsSf https://astral.sh/uv/install.sh | sh
    export PATH="$HOME/.local/bin:$PATH"
fi

if ! command -v uv >/dev/null 2>&1; then
    echo '{"error": "uv install failed - install manually: curl -LsSf https://astral.sh/uv/install.sh | sh, then re-run setup"}'
    exit 1
fi

cd "$SCRIPT_DIR"
uv run python wizard.py setup-check $CHECK_FLAG
