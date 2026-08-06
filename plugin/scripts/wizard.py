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
    return {"mcpServers": {"bitwig": {"type": "http", "url": f"http://{host}:{port}/mcp"}}}


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
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
