"""Read a .dawproject export (Bitwig's open interchange format).

Offline project context for Wigout Studio roles: digest (track tree,
devices, clips, tempo, markers, scenes), note extraction, automation
extraction. Stdlib only. File-based context is stale by definition -
every output carries fileModified for staleness labeling. The reader
reports what the file holds and never fabricates missing content.
"""
import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone

AUDIO_EXTS = (".wav", ".aif", ".aiff", ".flac", ".ogg", ".mp3")
SUBCOMMANDS = {"digest", "notes", "automation"}


def _load(path):
    if not os.path.isfile(path):
        raise FileNotFoundError(f"no such file: {path}")
    try:
        zf = zipfile.ZipFile(path)
    except zipfile.BadZipFile:
        raise ValueError(f"not a .dawproject (not a ZIP archive): {path}")
    with zf:
        names = zf.namelist()
        if "project.xml" not in names:
            raise ValueError(f"not a .dawproject (no project.xml inside): {path}")
        return ET.fromstring(zf.read("project.xml")), names


def _file_modified(path):
    ts = os.path.getmtime(path)
    return datetime.fromtimestamp(ts, tz=timezone.utc).isoformat(timespec="seconds")


def _attr_float(el, name):
    v = el.get(name) if el is not None else None
    return float(v) if v is not None else None


def _lanes_by_track(root):
    out = {}
    arr = root.find("Arrangement")
    if arr is not None:
        for lanes in arr.iter("Lanes"):
            if lanes.get("track"):
                out[lanes.get("track")] = lanes
    return out


def _channel_summary(track_el):
    ch = track_el.find("Channel")
    if ch is None:
        return None
    mute = ch.find("Mute")
    return {
        "volume": _attr_float(ch.find("Volume"), "value"),
        "pan": _attr_float(ch.find("Pan"), "value"),
        "mute": (mute.get("value") == "true") if mute is not None else None,
    }


def _devices(track_el):
    ch = track_el.find("Channel")
    devs = ch.find("Devices") if ch is not None else None
    if devs is None:
        return []
    return [{"name": d.get("deviceName") or d.get("name") or d.tag, "type": d.tag}
            for d in devs]


def _clip_count(track_lanes):
    # Direct Clip children of direct Clips children only: an audio clip's
    # nested inner Clips (warp container) must not double-count.
    if track_lanes is None:
        return 0
    return sum(len(c.findall("Clip")) for c in track_lanes.findall("Clips"))


def _track_entry(track_el, lanes_by_track):
    return {
        "name": track_el.get("name") or "",
        "type": track_el.get("contentType") or "",
        "color": track_el.get("color"),
        "channel": _channel_summary(track_el),
        "devices": _devices(track_el),
        "arrangementClipCount": _clip_count(lanes_by_track.get(track_el.get("id"))),
        "children": [_track_entry(c, lanes_by_track) for c in track_el.findall("Track")],
    }


def digest(path):
    root, names = _load(path)
    app = root.find("Application")
    transport = root.find("Transport")
    tempo = transport.find("Tempo") if transport is not None else None
    ts = transport.find("TimeSignature") if transport is not None else None
    lanes_by_track = _lanes_by_track(root)
    structure = root.find("Structure")
    tracks = ([_track_entry(t, lanes_by_track) for t in structure.findall("Track")]
              if structure is not None else [])
    arr = root.find("Arrangement")
    markers = [{"time": _attr_float(m, "time"), "name": m.get("name") or ""}
               for m in (arr.iter("Marker") if arr is not None else [])]
    tempo_id = tempo.get("id") if tempo is not None else None
    has_tempo_auto = tempo_id is not None and any(
        t.get("parameter") == tempo_id for t in root.iter("Target"))
    return {
        "file": path,
        "fileModified": _file_modified(path),
        "application": ({"name": app.get("name"), "version": app.get("version")}
                        if app is not None else None),
        "tempo": _attr_float(tempo, "value"),
        "timeSignature": (f"{ts.get('numerator')}/{ts.get('denominator')}"
                          if ts is not None else None),
        "tracks": tracks,
        "markers": markers,
        "scenes": [s.get("name") or "" for s in root.iter("Scene")],
        "embeddedAudio": [n for n in names if n.lower().endswith(AUDIO_EXTS)],
        "hasTempoAutomation": has_tempo_auto,
    }


def _flatten(track_els):
    for t in track_els:
        yield t
        yield from _flatten(t.findall("Track"))


def _find_track(root, name):
    structure = root.find("Structure")
    all_tracks = (list(_flatten(structure.findall("Track")))
                  if structure is not None else [])
    names = ", ".join(sorted({t.get("name") or "" for t in all_tracks})) or "(none)"
    exact = [t for t in all_tracks if (t.get("name") or "") == name]
    if len(exact) == 1:
        return exact[0]
    if len(exact) > 1:
        raise ValueError(
            f"track name '{name}' is ambiguous ({len(exact)} exact matches); "
            f"tracks in file: {names}")
    ci = [t for t in all_tracks if (t.get("name") or "").lower() == name.lower()]
    if len(ci) == 1:
        return ci[0]
    kind = "ambiguous (case-insensitive)" if len(ci) > 1 else "not found"
    raise ValueError(f"track '{name}' {kind}; tracks in file: {names}")


def notes(path, track_name, clip_name=None):
    root, _ = _load(path)
    track = _find_track(root, track_name)
    lanes = _lanes_by_track(root).get(track.get("id"))
    clips_out = []
    for clip in (lanes.iter("Clip") if lanes is not None else []):
        notes_el = clip.find("Notes")
        if notes_el is None:
            continue
        cname = clip.get("name") or ""
        if clip_name is not None and cname != clip_name:
            continue
        clips_out.append({
            "clip": cname,
            "clipStart_beats": _attr_float(clip, "time"),
            "notes": [{
                "key": int(n.get("key")),
                "start_beats": float(n.get("time")),
                "duration_beats": float(n.get("duration")),
                "velocity": _attr_float(n, "vel"),
                "channel": int(n.get("channel") or 0),
            } for n in notes_el.findall("Note")],
        })
    if clip_name is not None and not clips_out:
        raise ValueError(f"no clip named '{clip_name}' with notes on track '{track_name}'")
    return {"file": path, "fileModified": _file_modified(path),
            "track": track.get("name"), "clips": clips_out}


def automation(path, track_name, parameter=None):
    root, _ = _load(path)
    track = _find_track(root, track_name)
    id_names = {el.get("id"): el.get("name")
                for el in root.iter() if el.get("id") and el.get("name")}
    lanes = _lanes_by_track(root).get(track.get("id"))
    lanes_out, points_out = [], None
    for pts in (lanes.iter("Points") if lanes is not None else []):
        target = pts.find("Target")
        param_id = target.get("parameter") if target is not None else None
        pname = ((target.get("expression") if target is not None else None)
                 or id_names.get(param_id) or param_id or "unknown")
        point_els = [p for p in pts if p.tag.endswith("Point")]
        lanes_out.append({"parameter": pname, "target": param_id,
                          "pointCount": len(point_els)})
        if parameter is not None and pname == parameter:
            points_out = [{
                "time": _attr_float(p, "time"),
                "value": _attr_float(p, "value"),
                "interpolation": p.get("interpolation"),
            } for p in point_els]
    base = {"file": path, "fileModified": _file_modified(path),
            "track": track.get("name")}
    if parameter is None:
        return {**base, "lanes": lanes_out}
    if points_out is None:
        avail = ", ".join(l["parameter"] for l in lanes_out) or "(none)"
        raise ValueError(
            f"no automation lane '{parameter}' on track '{track_name}'; lanes: {avail}")
    return {**base, "parameter": parameter, "points": points_out}


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if argv and argv[0] not in SUBCOMMANDS and not argv[0].startswith("-"):
        argv.insert(0, "digest")  # bare file -> digest
    ap = argparse.ArgumentParser(
        description="Read a .dawproject export (offline project context)")
    sub = ap.add_subparsers(dest="cmd")
    d = sub.add_parser("digest")
    d.add_argument("file", nargs="?")
    n = sub.add_parser("notes")
    n.add_argument("file", nargs="?")
    n.add_argument("--track")
    n.add_argument("--clip")
    a = sub.add_parser("automation")
    a.add_argument("file", nargs="?")
    a.add_argument("--track")
    a.add_argument("--parameter")
    args = ap.parse_args(argv)
    try:
        if args.cmd is None:
            raise ValueError("usage: dawproject_read.py [digest|notes|automation] <file> ...")
        if not args.file:
            raise ValueError("a .dawproject file path is required")
        if args.cmd == "digest":
            result = digest(args.file)
        elif args.cmd == "notes":
            if not args.track:
                raise ValueError("--track is required for notes")
            result = notes(args.file, args.track, args.clip)
        else:
            if not args.track:
                raise ValueError("--track is required for automation")
            result = automation(args.file, args.track, args.parameter)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
