# Wigout Studio environment setup. Idempotent. -Check = report only.
param([switch]$Check)
$ErrorActionPreference = "Stop"
$report = [ordered]@{}

# 1. uv
$uv = Get-Command uv -ErrorAction SilentlyContinue
$report.uv = if ($uv) { "ok ($((uv --version)))" } else { "MISSING - install: winget install astral-sh.uv" }

# 2. Python env
if ($uv -and -not $Check) {
    Push-Location $PSScriptRoot
    uv python pin 3.10 | Out-Null
    uv sync | Out-Null
    Pop-Location
}
if ($uv) {
    Push-Location $PSScriptRoot
    uv run python -c "import music21, librosa, basic_pitch, pyloudnorm, soundfile" 2>$null | Out-Null
    # Native commands never throw in PowerShell - only $LASTEXITCODE detects a broken env
    $report.python_env = if ($LASTEXITCODE -eq 0) { "ok (3.10, deps importable)" } else { "BROKEN - run setup without -Check" }
    Pop-Location
} else { $report.python_env = "blocked on uv" }

# 3. ffmpeg
$ff = Get-Command ffmpeg -ErrorAction SilentlyContinue
$report.ffmpeg = if ($ff) { "ok" } else { "MISSING - install: winget install Gyan.FFmpeg" }

# 3b. Optional engineer-v2 audio groups
if ($uv) {
    Push-Location $PSScriptRoot
    uv run python -c "import matchering" 2>$null | Out-Null
    $report.matchering = if ($LASTEXITCODE -eq 0) { "ok" } else { "not installed - reference mastering unavailable; install: uv sync --group mastering" }
    uv run python -c "import audio_separator" 2>$null | Out-Null
    $report.audio_separator = if ($LASTEXITCODE -eq 0) { "ok" } else { "not installed - stem separation unavailable; install: uv sync --group stems" }
    Pop-Location
} else {
    $report.matchering = "blocked on uv"
    $report.audio_separator = "blocked on uv"
}

# 4. Bridge
$probe = Test-NetConnection -ComputerName localhost -Port 61169 -WarningAction SilentlyContinue
$report.bridge = if ($probe.TcpTestSucceeded) { "reachable on :61169" } else { "UNREACHABLE - is Bitwig running with the Wigout extension?" }

# 5. GPU (optional capability)
try { $gpu = (& nvidia-smi --query-gpu=name,memory.total --format=csv,noheader 2>$null) }
catch { $gpu = $null }
$report.gpu = if ($gpu) { "ok ($gpu)" } else { "none detected - audio generation unavailable; MIDI paths unaffected" }

# 6. claude-music plugin (optional)
$cm = Test-Path (Join-Path $env:USERPROFILE ".claude\plugins\cache\claude-music")
$report.claude_music = if ($cm) { "installed" } else { "not installed - composer will offer acestep-api or MIDI-only" }

$report | ConvertTo-Json
