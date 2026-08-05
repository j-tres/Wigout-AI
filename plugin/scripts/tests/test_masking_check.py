import json
import numpy as np
import pytest
import soundfile as sf

import masking_check as mc

SR = 44100


def _tone(tmp_path, name, freq):
    t = np.linspace(0, 1.0, SR, endpoint=False)
    sig = 0.4 * np.sin(2 * np.pi * freq * t)
    p = tmp_path / name
    sf.write(str(p), sig.astype("float32"), SR)
    return str(p)


def test_same_band_tones_overlap_high(tmp_path):
    # 150/165 Hz sit mid-bass-band, several STFT bins from the sub edge (no leakage flake)
    a = _tone(tmp_path, "a.wav", 150.0)
    b = _tone(tmp_path, "b.wav", 165.0)
    out = mc.analyze(a, b)
    assert out["bands"]["bass"] > 0.9
    assert out["worst"][0] == "bass"


def test_disjoint_tones_overlap_low(tmp_path):
    a = _tone(tmp_path, "a.wav", 150.0)
    b = _tone(tmp_path, "b.wav", 4000.0)
    out = mc.analyze(a, b)
    assert max(out["bands"].values()) < 0.1


def test_output_shape(tmp_path):
    a = _tone(tmp_path, "a.wav", 150.0)
    b = _tone(tmp_path, "b.wav", 4000.0)
    out = mc.analyze(a, b)
    assert set(out["bands"]) <= {"sub", "bass", "lowmid", "mid", "highmid", "high"}
    assert isinstance(out["advice"], str) and out["advice"]
    assert out["limits"]


@pytest.mark.filterwarnings("ignore:PySoundFile failed")
@pytest.mark.filterwarnings("ignore::FutureWarning:librosa")
def test_cli_errors_are_json_contract(capsys, tmp_path):
    a = _tone(tmp_path, "a.wav", 150.0)
    for argv in ([], ["--a", a], ["--a", a, "--b", "/no/such.wav"]):
        with pytest.raises(SystemExit) as exc:
            mc.main(argv)
        assert exc.value.code == 1
        assert "error" in json.loads(capsys.readouterr().out)
