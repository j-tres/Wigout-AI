"""Stem separation wrapper (python-audio-separator) for Wigout Studio.

Optional dependency: install with `uv sync --group stems`. Model weights
download on first real use - never during tests.
"""
import argparse
import json
import sys
from pathlib import Path


def split(audio, out_dir, model=None):
    src = Path(audio)
    if not src.is_file():
        raise FileNotFoundError(f"no such audio file: {audio}")
    try:
        from audio_separator.separator import Separator
    except ImportError as exc:
        # Distinguish "group not installed" from a broken transitive import -
        # report the real cause.
        raise RuntimeError(
            f"audio-separator unavailable ({exc}) - install: cd plugin/scripts && "
            "uv sync --group stems; then invoke with uv run --group stems"
        ) from exc
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    separator = Separator(output_dir=str(out))
    if model:
        separator.load_model(model_filename=model)
    else:
        separator.load_model()
    written = separator.separate(str(src))
    return {"stems": [{"name": Path(f).stem, "path": str(out / Path(f).name)} for f in written]}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Split an audio file into stems")
    ap.add_argument("--audio")
    ap.add_argument("--out-dir")
    ap.add_argument("--model")
    args = ap.parse_args(argv)
    try:
        if not args.audio or not args.out_dir:
            raise ValueError("--audio and --out-dir are both required")
        result = split(args.audio, args.out_dir, model=args.model)
    except Exception as e:
        print(json.dumps({"error": f"{type(e).__name__}: {e}"}))
        sys.exit(1)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
