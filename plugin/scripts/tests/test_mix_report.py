import json
import numpy as np
import pytest
import soundfile as sf

import mix_report as mr

SR = 44100


def _write(tmp_path, name, data):
    p = tmp_path / name
    sf.write(str(p), data.astype("float32"), SR)
    return str(p)


def _sine(amp=0.5, freq=997.0, seconds=2.0):
    t = np.linspace(0, seconds, int(SR * seconds), endpoint=False)
    return amp * np.sin(2 * np.pi * freq * t)


def test_sine_loudness_and_peaks(tmp_path):
    p = _write(tmp_path, "sine.wav", _sine())
    out = mr.analyze(p)
    L = out["loudness"]
    # 997 Hz sine at -6.02 dBFS: LUFS ~= -9.7 (K-weight ~unity at 1 kHz)
    assert -11.0 < L["lufs_integrated"] < -8.5
    assert L["sample_peak_dbfs"] == pytest.approx(-6.02, abs=0.2)
    assert L["true_peak_est_dbtp"] >= L["sample_peak_dbfs"] - 0.1
    assert L["crest_db"] == pytest.approx(3.01, abs=0.4)


def test_band_split_sums_to_one(tmp_path):
    p = _write(tmp_path, "sine.wav", _sine())
    bands = mr.analyze(p)["spectrum"]["bands"]
    assert set(bands) == {"low", "mid", "high"}
    assert sum(bands.values()) == pytest.approx(1.0, abs=0.02)


def test_stereo_metrics_ordering(tmp_path):
    rng = np.random.default_rng(42)
    n = SR
    l = rng.standard_normal(n) * 0.2
    ident = _write(tmp_path, "ident.wav", np.stack([l, l], axis=1))
    r = rng.standard_normal(n) * 0.2
    decor = _write(tmp_path, "decor.wav", np.stack([l, r], axis=1))
    si = mr.analyze(ident)["stereo"]
    sd = mr.analyze(decor)["stereo"]
    assert si["width"] < 0.05 < sd["width"]
    assert si["correlation"] > 0.95
    assert abs(sd["correlation"]) < 0.3
    assert abs(si["mono_drop_db"]) < 0.5
    assert 2.0 < sd["mono_drop_db"] < 4.5


def test_mono_file_has_null_stereo(tmp_path):
    p = _write(tmp_path, "mono.wav", _sine())
    out = mr.analyze(p)
    assert out["stereo"] is None


def test_target_verdict_present_and_optional(tmp_path):
    p = _write(tmp_path, "sine.wav", _sine())
    with_t = mr.analyze(p, target="streaming")
    assert with_t["target"]["name"] == "streaming"
    assert "lufs_delta" in with_t["target"]
    assert mr.analyze(p, target="none")["target"] is None


def test_silent_audio_is_json_error_not_infinity(tmp_path, capsys):
    p = _write(tmp_path, "silent.wav", np.zeros(SR * 2))
    with pytest.raises(SystemExit) as exc:
        mr.main(["--audio", p])
    assert exc.value.code == 1
    out = capsys.readouterr().out
    assert "error" in json.loads(out)
    assert "Infinity" not in out


def test_cli_errors_are_json_contract(capsys):
    for argv in ([], ["--audio", "/no/such/file.wav"], ["--audio", "x.wav", "--target", "bogus"]):
        with pytest.raises(SystemExit) as exc:
            mr.main(argv)
        assert exc.value.code == 1
        assert "error" in json.loads(capsys.readouterr().out)


def test_club_target_verdict(tmp_path):
    p = _write(tmp_path, "sine.wav", _sine())
    t = mr.analyze(p, target="club")["target"]
    assert t["name"] == "club"
    # -9.7 LUFS sine vs club -7 -> quieter than target
    assert t["lufs_delta"] == pytest.approx(-2.7, abs=1.5)
    assert any("quieter" in n for n in t["notes"])


def test_too_short_audio_is_json_error(tmp_path, capsys):
    p = _write(tmp_path, "short.wav", _sine(seconds=0.2))
    with pytest.raises(SystemExit) as exc:
        mr.main(["--audio", p])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)
