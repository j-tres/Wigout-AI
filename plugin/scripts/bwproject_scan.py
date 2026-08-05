"""Heuristic strings scan of an unexported .bwproject binary.

.bwproject is proprietary and version-shifting; full parsing is
rejected by design (spec 2026-07-11). This extracts printable-string
EVIDENCE only - sample paths, plugin hints - and labels everything
fidelity: "heuristic". The DAWproject export path is the robust one.
"""
import argparse
import json
import os
import re
import sys
from datetime import datetime, timezone

MAGIC = b"BtWg"
ASCII_RUN = re.compile(rb"[\x20-\x7e]{4,}")
UTF16_RUN = re.compile(rb"(?<![\x20-\x7e][\x20-\x7e])(?:[\x20-\x7e]\x00){4,}")
VERSION_NEAR = re.compile(rb"application_version_name\W{0,8}([0-9]+\.[0-9]+(?:\.[0-9]+)*)")
VERSION_FALLBACK = re.compile(rb"Bitwig Studio\W{0,20}([0-9]+\.[0-9]+(?:\.[0-9]+)*)")
AUDIO_PATH = re.compile(r"\.(wav|aiff?|flac|ogg|mp3|bwsample|multisample|wt)$", re.IGNORECASE)
PLUGIN_HINT = re.compile(
    r"(\.vst3$|\.clap$|\.component$)|(^[a-z][a-z0-9_-]*(\.[a-z0-9_-]+){2,}$)",
    re.IGNORECASE)
OTHER_CAP = 200


def scan(path):
    if not os.path.isfile(path):
        raise FileNotFoundError(f"no such file: {path}")
    with open(path, "rb") as f:
        data = f.read()
    if not data.startswith(MAGIC):
        raise ValueError(f"not a .bwproject (missing BtWg magic header): {path}")
    m = VERSION_NEAR.search(data) or VERSION_FALLBACK.search(data)
    strings = [s.decode("ascii") for s in ASCII_RUN.findall(data)]
    strings += [s.decode("utf-16-le") for s in UTF16_RUN.findall(data)]
    seen, sample_paths, plugin_hints, other = set(), [], [], []
    for s in strings:
        s = s.strip()
        if len(s) < 4 or s in seen:
            continue
        seen.add(s)
        if AUDIO_PATH.search(s):
            sample_paths.append(s)
        elif PLUGIN_HINT.search(s):
            plugin_hints.append(s)
        elif len(other) < OTHER_CAP:
            other.append(s)
    return {
        "file": path,
        "fileModified": datetime.fromtimestamp(
            os.path.getmtime(path), tz=timezone.utc).isoformat(timespec="seconds"),
        "bitwigVersionHint": m.group(1).decode("ascii") if m else None,
        "fidelity": "heuristic",
        "samplePaths": sample_paths,
        "pluginHints": plugin_hints,
        "other": other,
    }


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="Heuristic strings scan of a .bwproject (advisory only)")
    ap.add_argument("file", nargs="?")
    args = ap.parse_args(argv)
    try:
        if not args.file:
            raise ValueError("a .bwproject file path is required")
        result = scan(args.file)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
