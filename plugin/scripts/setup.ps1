# Wigout Studio environment setup (Windows). Idempotent. -Check = report only.
#
# uv itself is the one piece that has to be OS-native: without it there's no
# way to run wizard.py, which is where everything else actually lives
# (ffmpeg/GPU/bridge checks, optional dependency groups). This script's only
# job is "make sure uv exists", then it hands off. See setup.sh for the
# macOS/Linux equivalent -- same split, different bootstrap.
param([switch]$Check)
$ErrorActionPreference = "Stop"

function Get-Uv { Get-Command uv -ErrorAction SilentlyContinue }

$uv = Get-Uv
if (-not $uv) {
    powershell -ExecutionPolicy ByPass -Command "irm https://astral.sh/uv/install.ps1 | iex" | Out-Null
    # The installer updates the User/Machine PATH for *new* shells, not this
    # one -- refresh from the registry so the freshly-installed uv is found
    # without requiring the user to restart their terminal.
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path", "User")
    $uv = Get-Uv
}

if (-not $uv) {
    @{
        error = "uv install failed - install manually (winget install astral-sh.uv, or see " +
                 "https://docs.astral.sh/uv/getting-started/installation/), then re-run setup"
    } | ConvertTo-Json
    exit 1
}

Push-Location $PSScriptRoot
if ($Check) {
    uv run python wizard.py setup-check --check
} else {
    uv run python wizard.py setup-check
}
Pop-Location
