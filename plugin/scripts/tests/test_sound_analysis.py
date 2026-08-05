import json
import numpy as np
import soundfile as sf
import pytest
import sound_analysis as sa

SR = 22050

def _write_tone(tmp_path, name, freqs):
    t = np.linspace(0, 1.0, SR, endpoint=False)
    sig = sum(np.sin(2 * np.pi * f * t) for f in freqs)
    sig = 0.3 * sig / np.max(np.abs(sig))
    p = tmp_path / name
    sf.write(str(p), sig.astype("float32"), SR)
    return str(p)

def test_bright_signal_scores_brighter_than_dark(tmp_path):
    dark = _write_tone(tmp_path, "dark.wav", [220.0])
    bright = _write_tone(tmp_path, "bright.wav", [220.0, 4000.0, 8000.0])
    d = sa.analyze(dark)
    b = sa.analyze(bright)
    assert b["features"]["centroid_hz"] > d["features"]["centroid_hz"]
    order = {"low": 0, "medium": 1, "high": 2}
    assert order[b["descriptors"]["bright"]] >= order[d["descriptors"]["bright"]]

def test_output_shape(tmp_path):
    f = _write_tone(tmp_path, "t.wav", [440.0])
    out = sa.analyze(f)
    for k in ("centroid_hz", "rolloff_hz", "flatness", "rms", "crest"):
        assert k in out["features"]
    assert set(out["features"]["bands"]) == {"low", "mid", "high"}
    for d in ("bright", "warm", "full"):
        assert out["descriptors"][d] in ("low", "medium", "high")

@pytest.mark.filterwarnings("ignore:PySoundFile failed")
@pytest.mark.filterwarnings("ignore::FutureWarning:librosa")
def test_cli_error_on_missing_file(capsys):
    with pytest.raises(SystemExit) as exc:
        sa.main(["/no/such/file.wav"])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)
