import json
import sys

import pytest

import stem_split as ss


def test_missing_args_error_contract(capsys):
    with pytest.raises(SystemExit) as exc:
        ss.main([])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)


def test_missing_file_error_contract(capsys, tmp_path):
    with pytest.raises(SystemExit) as exc:
        ss.main(["--audio", "/no/such.wav", "--out-dir", str(tmp_path)])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)


def test_missing_dependency_names_install_step(capsys, tmp_path, monkeypatch):
    wav = tmp_path / "x.wav"
    wav.write_bytes(b"RIFF")  # existence is checked before the import
    monkeypatch.setitem(sys.modules, "audio_separator", None)
    monkeypatch.setitem(sys.modules, "audio_separator.separator", None)
    with pytest.raises(SystemExit) as exc:
        ss.main(["--audio", str(wav), "--out-dir", str(tmp_path)])
    assert exc.value.code == 1
    err = json.loads(capsys.readouterr().out)["error"]
    assert "uv sync --group stems" in err
