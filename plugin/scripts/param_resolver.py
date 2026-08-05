"""Fuzzy parameter-name resolver for Wigout Studio sound design.

Given a device's parameter listing (as returned by the bridge's
get_selected_device_parameters / bw_get) and a target name, return the
best-matching parameter slot. String matching only — semantic synonyms
(e.g. "cutoff" -> "Low-pass") are the KB/model's job; supply the real
parameter name from the device guide.
"""
import argparse
import difflib
import json
import re
import sys


def _norm(text):
    return re.sub(r"[^a-z0-9]", "", (text or "").lower())


def _score(query_norm, name_norm):
    if not query_norm or not name_norm:
        return 0.0
    if query_norm == name_norm:
        return 1.0
    ratio = difflib.SequenceMatcher(None, query_norm, name_norm).ratio()
    # Substring containment is a strong signal difflib underweights.
    if query_norm in name_norm or name_norm in query_norm:
        ratio = max(ratio, 0.85)
    return ratio


def resolve(params, query, threshold=0.6):
    q = _norm(query)
    ranked = []
    for p in params:
        s = _score(q, _norm(p.get("name")))
        ranked.append({
            "index": p.get("index"),
            "page": p.get("page"),
            "name": p.get("name"),
            "score": round(s, 4),
        })
    ranked.sort(key=lambda r: r["score"], reverse=True)
    best = ranked[0] if ranked else None
    matched = bool(best) and best["score"] >= threshold
    return {
        "matched": matched,
        "index": best["index"] if matched else None,
        "page": best["page"] if matched else None,
        "name": best["name"] if matched else None,
        "score": best["score"] if best else 0.0,
        "candidates": ranked[:5],
    }


def main(argv=None):
    ap = argparse.ArgumentParser(description="Resolve a parameter name to a slot")
    ap.add_argument("--params-json", default=None, help="file path or '-' for stdin")
    ap.add_argument("--query", default=None, help="target parameter name")
    ap.add_argument("--threshold", type=float, default=0.6)
    args = ap.parse_args(argv)
    try:
        if not args.params_json:
            raise ValueError("--params-json is required")
        if not args.query:
            raise ValueError("--query is required")
        if args.params_json == "-":
            params = json.load(sys.stdin)
        else:
            with open(args.params_json, encoding="utf-8") as f:
                params = json.load(f)
        if not isinstance(params, list):
            raise ValueError("params JSON must be a list of parameter objects")
        result = resolve(params, args.query, args.threshold)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
