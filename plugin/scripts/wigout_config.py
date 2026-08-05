"""Cross-runtime per-user config for Wigout AI: reads/writes ~/.wigout-ai/config.json.

Mirrors the schema written by the Java extension's FileLocationsPreferences
(extension/src/main/java/org/wigout/mcp/config/), so the Python scripts/
skills and the Java extension agree on one file.
"""
import json
import os
import platform
from pathlib import Path

LOCATION_KEYS = (
    "projects", "library", "soundContent", "music",
    "audioAnalysisCache", "controllerScripts",
)


def config_path():
    return Path.home() / ".wigout-ai" / "config.json"


def load(path=None):
    path = Path(path) if path else config_path()
    if not path.exists():
        return {"version": 1, "locations": {}}
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    data.setdefault("version", 1)
    data.setdefault("locations", {})
    return data


def save(config, path=None):
    path = Path(path) if path else config_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2)


def get_location(key, path=None):
    if key not in LOCATION_KEYS:
        raise ValueError(f"unknown location key: {key}")
    return load(path)["locations"].get(key)


def auto_detect(system=None, home=None, one_drive=None):
    """Best-effort candidate scan, mirroring AutoDetect.java. Windows: OneDrive-
    vs-Documents check (ported from extension/build.gradle.kts's deploy task).
    macOS/Linux: unverified - returns no candidates rather than guessing (see
    docs/superpowers/plans/2026-07-31-cross-platform-user-config.md, Global Constraints).
    """
    system = system or platform.system()
    home = Path(home) if home else Path.home()
    candidates = {}
    if system != "Windows":
        return candidates

    # Only use environment variables if we're using the real home directory.
    # When a test passes a different home (tmp_path), it wants to isolate from the environment.
    if one_drive is None and home == Path.home():
        one_drive = os.environ.get("OneDrive") or os.environ.get("OneDriveConsumer")

    documents_base = None
    if one_drive:
        redirected_docs = Path(one_drive) / "Documents"
        if redirected_docs.is_dir():
            documents_base = redirected_docs
    if documents_base is None:
        documents_base = home / "Documents"

    projects = documents_base / "Bitwig Studio" / "Projects"
    if projects.is_dir():
        candidates["projects"] = str(projects)
    return candidates
