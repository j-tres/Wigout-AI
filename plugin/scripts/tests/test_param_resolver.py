import json
import pytest
import param_resolver as pr

POLYMER = [
    {"index": 0, "name": "Waveform"},
    {"index": 1, "name": "High-pass"},
    {"index": 2, "name": "Low-pass"},
    {"index": 3, "name": "Resonance"},
]

def test_exact_normalized_match():
    out = pr.resolve(POLYMER, "Low-pass")
    assert out["matched"] is True
    assert out["index"] == 2
    assert out["score"] == pytest.approx(1.0)

def test_case_and_punctuation_insensitive():
    out = pr.resolve(POLYMER, "lowpass")
    assert out["matched"] is True
    assert out["index"] == 2

def test_near_miss_still_matches():
    out = pr.resolve(POLYMER, "resonanse")
    assert out["matched"] is True
    assert out["index"] == 3

def test_absent_returns_unmatched_with_candidates():
    out = pr.resolve(POLYMER, "reverb mix")
    assert out["matched"] is False
    assert out["index"] is None
    assert len(out["candidates"]) > 0

def test_page_is_carried_through():
    params = [{"index": 4, "name": "Drive", "page": 2}]
    out = pr.resolve(params, "drive")
    assert out["page"] == 2 and out["index"] == 4

def test_cli_error_on_missing_query(tmp_path, capsys):
    p = tmp_path / "p.json"
    p.write_text(json.dumps(POLYMER), encoding="utf-8")
    with pytest.raises(SystemExit) as exc:
        pr.main(["--params-json", str(p)])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)

def test_cli_error_on_missing_params_json(capsys):
    with pytest.raises(SystemExit) as exc:
        pr.main(["--query", "cutoff"])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)
