"""Reference mastering wrapper (matchering) for Wigout Studio.

Optional dependency: install with `uv sync --group mastering`. Matches the
target mix to a user-supplied reference. Run mix_report.py before and
after for the measured delta - this wrapper stays thin.
"""
import argparse
import json
import sys
from pathlib import Path


def match(target, reference, out):
    for label, p in (("target", target), ("reference", reference)):
        if not Path(p).is_file():
            raise FileNotFoundError(f"no such {label} file: {p}")
    try:
        import matchering as mg
    except ImportError as exc:
        # Distinguish "group not installed" from a broken transitive import
        # (e.g. resampy needs pkg_resources/setuptools) - report the real cause.
        raise RuntimeError(
            f"matchering unavailable ({exc}) - install: cd plugin/scripts && "
            "uv sync --group mastering; then invoke with uv run --group mastering"
        ) from exc
    out_path = Path(out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    mg.process(target=str(target), reference=str(reference),
               results=[mg.pcm24(str(out_path))])
    return {"output": str(out_path)}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Match a mix to a reference master")
    ap.add_argument("--target")
    ap.add_argument("--reference")
    ap.add_argument("--out")
    args = ap.parse_args(argv)
    try:
        if not args.target or not args.reference or not args.out:
            raise ValueError("--target, --reference and --out are all required")
        result = match(args.target, args.reference, args.out)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
