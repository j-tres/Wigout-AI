import json
import sys

import pytest

import master_match as mm


def test_missing_args_error_contract(capsys):
    for argv in ([], ["--target", "t.wav"], ["--target", "t.wav", "--reference", "r.wav"]):
        with pytest.raises(SystemExit) as exc:
            mm.main(argv)
        assert exc.value.code == 1
        assert "error" in json.loads(capsys.readouterr().out)


def test_missing_files_error_contract(capsys, tmp_path):
    with pytest.raises(SystemExit) as exc:
        mm.main(["--target", "/no/t.wav", "--reference", "/no/r.wav",
                 "--out", str(tmp_path / "o.wav")])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)


def test_missing_dependency_names_install_step(capsys, tmp_path, monkeypatch):
    t = tmp_path / "t.wav"
    r = tmp_path / "r.wav"
    t.write_bytes(b"RIFF")
    r.write_bytes(b"RIFF")
    monkeypatch.setitem(sys.modules, "matchering", None)
    with pytest.raises(SystemExit) as exc:
        mm.main(["--target", str(t), "--reference", str(r),
                 "--out", str(tmp_path / "o.wav")])
    assert exc.value.code == 1
    err = json.loads(capsys.readouterr().out)["error"]
    assert "uv sync --group mastering" in err
