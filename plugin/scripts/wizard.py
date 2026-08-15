"""Install/config wizard for Wigout AI.

Non-interactive by design: every subcommand does exactly one thing and
prints JSON. The "confirm with the user" step lives in
plugin/skills/front-desk/references/setup.md, which calls `scan`,
presents candidates conversationally, then calls `write-locations` with
the confirmed values.
"""
import argparse
import json
import os
import platform
import shutil
import socket
import subprocess
import sys
from pathlib import Path
from urllib.request import Request, urlopen

import wigout_config as wc

MCP_PORT_DEFAULT = 61169
GITHUB_RELEASES_API = "https://api.github.com/repos/j-tres/wigout-ai/releases/latest"
EXTENSION_ASSET_NAME = "Wigout.bwextension"


def scan(config_path=None, system=None, home=None, bridge_check=None):
    system = system or platform.system()
    ext_dir = extensions_dir(system=system, home=home)
    bridge_check = bridge_check or _bridge_reachable
    return {
        "system": system,
        "config": wc.load(config_path),
        "auto_detect_candidates": wc.auto_detect(system=system, home=home),
        "bridge_reachable": bridge_check(),
        "extensions_dir": str(ext_dir),
        "extension_deployed": (ext_dir / EXTENSION_ASSET_NAME).is_file(),
    }


def _bridge_reachable(host="localhost", port=MCP_PORT_DEFAULT, timeout=1.0):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def write_locations(updates, path=None):
    config = wc.load(path)
    for key, value in updates.items():
        if key not in wc.LOCATION_KEYS:
            raise ValueError(f"unknown location key: {key}")
        config["locations"][key] = value
    wc.save(config, path)
    return config


def extensions_dir(system=None, home=None, one_drive=None):
    system = system or platform.system()
    home = Path(home) if home else Path.home()
    if system == "Windows":
        # Only use environment variables if we're using the real home directory.
        # When a test passes a different home (tmp_path), it wants to isolate from the environment.
        if one_drive is None and home == Path.home():
            one_drive = os.environ.get("OneDrive") or os.environ.get("OneDriveConsumer")
        base = None
        if one_drive:
            redirected = Path(one_drive) / "Documents"
            if redirected.is_dir():
                base = redirected
        if base is None:
            base = home / "Documents"
        return base / "Bitwig Studio" / "Extensions"
    if system == "Darwin":
        return home / "Documents" / "Bitwig Studio" / "Extensions"
    return home / "Bitwig Studio" / "Extensions"


def deploy_extension(source=None, extensions_dir_override=None, fetch_latest_release=None):
    target_dir = Path(extensions_dir_override) if extensions_dir_override else extensions_dir()
    target_dir.mkdir(parents=True, exist_ok=True)
    target_path = target_dir / EXTENSION_ASSET_NAME

    if source:
        shutil.copyfile(source, target_path)
        return {"deployed": True, "source": "local", "path": str(target_path)}

    fetch_latest_release = fetch_latest_release or _fetch_latest_release_asset
    target_path.write_bytes(fetch_latest_release())
    return {"deployed": True, "source": "github-release", "path": str(target_path)}


def _fetch_latest_release_asset():
    with urlopen(Request(GITHUB_RELEASES_API, headers={"Accept": "application/vnd.github+json"})) as resp:
        release = json.load(resp)
    asset = next((a for a in release["assets"] if a["name"] == EXTENSION_ASSET_NAME), None)
    if asset is None:
        raise RuntimeError(f"latest release has no {EXTENSION_ASSET_NAME} asset")
    with urlopen(asset["browser_download_url"]) as resp:
        return resp.read()


def mcp_snippet(host="localhost", port=MCP_PORT_DEFAULT):
    return {"mcpServers": {"Wigout-MCP": {"type": "http", "url": f"http://{host}:{port}/mcp"}}}


def diagnose(scan_result=None, **scan_kwargs):
    """Classify current connectivity state into a status + plain-language message.

    Pure with respect to `scan_result`: pass an already-computed scan() dict
    to classify it directly (used by tests and anywhere a scan was already
    done), or omit it to have diagnose() call scan(**scan_kwargs) itself.
    Used by the reactive tool-call-failure path documented in
    plugin/skills/bitwig-project/references/bridge-landmines.md.
    """
    result = scan_result if scan_result is not None else scan(**scan_kwargs)
    if not result["extension_deployed"]:
        return {
            "status": "not_deployed",
            "message": "The Wigout AI extension isn't installed in Bitwig yet.",
        }
    if not result["bridge_reachable"]:
        return {
            "status": "unreachable",
            "message": "The extension is installed, but Bitwig doesn't seem to be running with a project open.",
        }
    return {
        "status": "ok",
        "message": "Bridge looks healthy — this failure isn't a connectivity issue.",
    }


def session_start_check(**scan_kwargs):
    """Auto-deploy the extension when missing; return a one-line status, or None when healthy.

    Called every session by the SessionStart hook (plugin/hooks/session-start.sh)
    via the `session-start-check` CLI subcommand below. Returns None (meaning:
    print nothing) once the extension is deployed -- this only ever does
    anything on a fresh install, and never on the steady-state path.
    """
    result = scan(**scan_kwargs)
    if result["extension_deployed"]:
        return None
    try:
        deploy_extension()
        return (
            "Installed the Wigout AI extension into Bitwig's Extensions folder "
            "— restart/reload it in Bitwig to activate it."
        )
    except Exception as e:
        return (
            f"Wigout AI extension isn't installed yet and auto-install failed "
            f"({type(e).__name__}: {e}) — run `/studio setup` to install it manually."
        )


PYTHON_ENV_MODULES = ["music21", "librosa", "basic_pitch", "pyloudnorm", "soundfile"]

# Platform-specific install hints. uv itself isn't here -- bootstrapping uv is
# the native setup.ps1/setup.sh script's job (it has to run *before* this
# module is reachable at all), so by the time setup_check() runs, uv already
# exists.
INSTALL_HINTS = {
    "Windows": {"ffmpeg": "winget install Gyan.FFmpeg"},
    "Darwin": {"ffmpeg": "brew install ffmpeg"},
    "Linux": {"ffmpeg": "your package manager, e.g. apt install ffmpeg / dnf install ffmpeg"},
}


def check_command_available(name, which=shutil.which):
    return which(name) is not None


def install_hint(tool, system=None):
    system = system or platform.system()
    hints = INSTALL_HINTS.get(system, INSTALL_HINTS["Linux"])
    return hints.get(tool, f"install {tool} for your platform")


def check_python_imports(modules, run=subprocess.run):
    result = run(["uv", "run", "python", "-c", f"import {', '.join(modules)}"], capture_output=True, text=True)
    return result.returncode == 0


def detect_gpu(run=subprocess.run):
    try:
        result = run(
            ["nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader"],
            capture_output=True,
            text=True,
        )
    except FileNotFoundError:
        return None
    if result.returncode != 0 or not result.stdout.strip():
        return None
    return result.stdout.strip()


def claude_music_installed(home=None):
    home = Path(home) if home else Path.home()
    return (home / ".claude" / "plugins" / "cache" / "claude-music").is_dir()


def sync_dependencies(run=subprocess.run):
    """Pin the interpreter and install all optional feature groups by default
    -- combined into one sync so the second group doesn't uninstall the
    first's shared pin (setuptools, notably)."""
    run(["uv", "python", "pin", "3.10"])
    run(["uv", "sync", "--group", "mastering", "--group", "stems"])


def setup_check(check_only=False, run=subprocess.run, which=shutil.which, home=None, system=None, bridge_check=None):
    """Everything setup.ps1/setup.sh delegate once uv is confirmed present:
    install (unless check_only), then report on the environment.

    uv itself is deliberately not part of this report -- getting here at all
    means uv already ran this script, so there's nothing left to check.
    """
    system = system or platform.system()
    bridge_check = bridge_check or _bridge_reachable

    if not check_only:
        sync_dependencies(run=run)

    ffmpeg_ok = check_command_available("ffmpeg", which=which)
    python_env_ok = check_python_imports(PYTHON_ENV_MODULES, run=run)
    matchering_ok = check_python_imports(["matchering"], run=run)
    audio_separator_ok = check_python_imports(["audio_separator"], run=run)
    gpu = detect_gpu(run=run)

    return {
        "python_env": "ok (3.10, deps importable)" if python_env_ok else "BROKEN - run setup without -Check",
        "ffmpeg": "ok" if ffmpeg_ok else f"MISSING - install: {install_hint('ffmpeg', system=system)}",
        "matchering": "ok" if matchering_ok else (
            "installed but won't import - reference mastering unavailable; run without -Check to "
            "retry, or 'uv run python -c import matchering' for the underlying error"
        ),
        "audio_separator": "ok" if audio_separator_ok else (
            "installed but won't import - stem separation unavailable; run without -Check to "
            "retry, or 'uv run python -c import audio_separator' for the underlying error"
        ),
        "bridge": "reachable on :61169" if bridge_check() else "UNREACHABLE - is Bitwig running with the Wigout extension?",
        "gpu": f"ok ({gpu})" if gpu else "none detected - audio generation unavailable; MIDI paths unaffected",
        "claude_music": "installed" if claude_music_installed(home=home)
            else "not installed - composer will offer acestep-api or MIDI-only",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description="Wigout AI install/config wizard")
    sub = parser.add_subparsers(dest="command", required=True)

    scan_parser = sub.add_parser("scan")
    scan_parser.add_argument("--host", default="localhost")
    scan_parser.add_argument("--port", type=int, default=MCP_PORT_DEFAULT)

    write_parser = sub.add_parser("write-locations")
    for key in wc.LOCATION_KEYS:
        write_parser.add_argument(f"--{key}")

    deploy_parser = sub.add_parser("deploy-extension")
    deploy_parser.add_argument("--source")
    deploy_parser.add_argument("--extensions-dir")

    snippet_parser = sub.add_parser("mcp-snippet")
    snippet_parser.add_argument("--host", default="localhost")
    snippet_parser.add_argument("--port", type=int, default=MCP_PORT_DEFAULT)

    diagnose_parser = sub.add_parser("diagnose")
    diagnose_parser.add_argument("--host", default="localhost")
    diagnose_parser.add_argument("--port", type=int, default=MCP_PORT_DEFAULT)

    sub.add_parser("session-start-check")

    setup_check_parser = sub.add_parser("setup-check")
    setup_check_parser.add_argument("--check", action="store_true", help="report only, don't install")

    args = parser.parse_args(argv)
    try:
        if args.command == "scan":
            result = scan(bridge_check=lambda: _bridge_reachable(host=args.host, port=args.port))
        elif args.command == "write-locations":
            updates = {key: value for key in wc.LOCATION_KEYS if (value := getattr(args, key)) is not None}
            if not updates:
                raise ValueError("no location values provided")
            result = write_locations(updates)
        elif args.command == "deploy-extension":
            result = deploy_extension(source=args.source, extensions_dir_override=args.extensions_dir)
        elif args.command == "mcp-snippet":
            result = mcp_snippet(host=args.host, port=args.port)
        elif args.command == "diagnose":
            result = diagnose(bridge_check=lambda: _bridge_reachable(host=args.host, port=args.port))
        elif args.command == "session-start-check":
            message = session_start_check()
            if message:
                print(message)
            return
        elif args.command == "setup-check":
            result = setup_check(check_only=args.check)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
