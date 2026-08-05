import gzip
import json
from pathlib import Path

import pytest

import chord_stats as cs

FIXTURE = str(Path(__file__).parent / "fixtures" / "chord_model_fixture.json")


def run(argv, capsys):
    cs.main(argv + ["--model", FIXTURE])
    return json.loads(capsys.readouterr().out)


def test_next_trigram_hit(capsys):
    out = run(["next", "--context", "I,V", "--mode", "major", "--genre", "pop"], capsys)
    assert out["backoff"] == "trigram"
    assert out["bucketUsed"] == "pop"
    assert out["truncated"] is False
    top = out["candidates"][0]
    assert top["numeral"] == "vi" and top["quality"] == "min"
    assert top["probability"] == 0.75 and top["count"] == 30
    assert top["globalProbability"] == 0.5  # global trigram I,V -> vi 20/40
    second = out["candidates"][1]
    assert second["numeral"] == "I" and second["globalProbability"] == 0.0


def test_next_backoff_to_bigram_then_unigram(capsys):
    out = run(["next", "--context", "IV,I", "--mode", "major", "--genre", "pop"], capsys)
    assert out["backoff"] == "bigram"
    assert out["candidates"][0]["numeral"] == "V"
    assert out["candidates"][0]["probability"] == 0.5  # 50/100
    out = run(["next", "--context", "viio", "--mode", "major", "--genre", "pop"], capsys)
    assert out["backoff"] == "unigram"
    assert any("context not found" in n for n in out["notes"])


def test_next_context_truncation(capsys):
    out = run(["next", "--context", "vi,I,V", "--mode", "major", "--genre", "pop"], capsys)
    assert out["truncated"] is True
    assert out["contextUsed"] == ["I", "V"]
    assert out["backoff"] == "trigram"


def test_next_unknown_genre_falls_back_to_global(capsys):
    out = run(["next", "--context", "I", "--mode", "major", "--genre", "zydeco"], capsys)
    assert out["bucketUsed"] == "global"
    assert any("zydeco" in n for n in out["notes"])
    assert out["candidates"][0]["globalProbability"] is None  # already global


def test_next_omitted_genre_is_global(capsys):
    out = run(["next", "--context", "I", "--mode", "major"], capsys)
    assert out["bucketUsed"] == "global"
    assert out["notes"] == []


def test_limits_present_on_every_response(capsys):
    for argv in (["next", "--context", "I", "--mode", "major"],
                 ["progressions", "--mode", "major"],
                 ["diagnose", "--numerals", "I,V", "--mode", "major"]):
        out = run(argv, capsys)
        assert len(out["limits"]) == 3
        assert any("not aesthetic" in l for l in out["limits"])


def test_progressions_ranked_with_share(capsys):
    out = run(["progressions", "--mode", "major", "--genre", "pop"], capsys)
    top = out["loops"][0]
    assert top["progression"] == ["I", "V", "vi", "IV"]
    assert top["count"] == 42
    assert top["shareOfStored"] == round(42 / 59, 4)


def test_progressions_empty_length_is_honest(capsys):
    out = run(["progressions", "--mode", "major", "--genre", "pop",
               "--length", "6"], capsys)
    assert out["loops"] == []
    assert any("no length-6" in n for n in out["notes"])


def test_diagnose_known_loop(capsys):
    out = run(["diagnose", "--numerals", "I,V,vi,IV", "--mode", "major",
               "--genre", "pop"], capsys)
    t0 = out["transitions"][0]
    assert (t0["from"], t0["to"]) == ("I", "V")
    assert t0["probability"] == 0.5 and t0["rank"] == 1 and t0["of"] == 3
    assert out["overall"]["loopRank"] == 1
    assert out["overall"]["meanLog10Prob"] == round(
        (-0.30103 - 0.30103 + 0.0) / 3, 3)
    assert out["overall"]["percentile"] == 94.0
    assert out["substitutions"] == []  # already the best-scoring shape


def test_diagnose_unseen_transition_and_substitutions(capsys):
    out = run(["diagnose", "--numerals", "I,V,V", "--mode", "major",
               "--genre", "pop"], capsys)
    assert out["transitions"][1]["probability"] == 0.0
    assert out["transitions"][1]["rank"] is None
    assert out["overall"]["meanLog10Prob"] is None
    assert out["overall"]["percentile"] is None
    assert any("unseen" in n for n in out["notes"])
    subs = out["substitutions"]
    assert subs and subs[0]["meanLog10Prob"] == -0.301
    assert all(s["position"] in (1, 2) for s in subs)


def test_gzip_model_loads(tmp_path, capsys):
    gz = tmp_path / "model.json.gz"
    with gzip.open(gz, "wt", encoding="utf-8") as f:
        f.write(Path(FIXTURE).read_text(encoding="utf-8"))
    cs.main(["next", "--context", "I", "--mode", "major", "--model", str(gz)])
    out = json.loads(capsys.readouterr().out)
    assert out["candidates"]


def test_error_contracts(tmp_path, capsys):
    bad_schema = tmp_path / "bad.json"
    bad_schema.write_text('{"modelSchema": 99}', encoding="utf-8")
    cases = [
        ["next", "--mode", "major", "--model", FIXTURE],                      # no context
        ["next", "--context", "I", "--model", FIXTURE],                       # no mode
        ["next", "--context", "I", "--mode", "phrygian", "--model", FIXTURE], # bad mode
        ["diagnose", "--numerals", "I", "--mode", "major", "--model", FIXTURE],
        ["progressions", "--mode", "major", "--length", "9", "--model", FIXTURE],
        ["next", "--context", "I", "--mode", "major", "--model", str(bad_schema)],
        ["next", "--context", "I", "--mode", "major", "--model", str(tmp_path / "nope.json")],
        [],
    ]
    for argv in cases:
        with pytest.raises(SystemExit) as exc:
            cs.main(argv)
        assert exc.value.code == 1, argv
        assert "error" in json.loads(capsys.readouterr().out), argv
