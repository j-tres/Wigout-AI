"""Validates the COMMITTED real chord_model artifact (not the fixture)."""
from pathlib import Path

import pytest

import chord_stats as cs

SCRIPTS = Path(__file__).resolve().parents[1]
ARTIFACT = next((p for p in (SCRIPTS / "chord_model.json",
                             SCRIPTS / "chord_model.json.gz") if p.is_file()), None)

pytestmark = pytest.mark.skipif(ARTIFACT is None,
                                reason="chord_model artifact not built yet")


@pytest.fixture(scope="module")
def model():
    return cs.load_model(str(ARTIFACT))


def test_schema_and_provenance_complete(model):
    assert model["modelSchema"] == 1
    prov = model["provenance"]
    for field in ("corpus", "corpusVersion", "license", "attribution",
                  "buildDate", "inputRows", "keptRows", "dropRate", "drops",
                  "slices", "buckets", "params", "vocabularyNote"):
        assert field in prov, field
    assert prov["license"] not in ("unknown", "", None)
    assert "CC" in prov["license"]
    assert 0 <= prov["dropRate"] < 0.5


def test_buckets_are_slices_and_global_present(model):
    assert "global" in model["buckets"]
    prov = model["provenance"]
    assert prov["buckets"], "no slice survived min_bucket"
    assert set(prov["buckets"]) <= {"pop", "classical"}
    assert "pop" in prov["buckets"]  # Billboard must survive
    for name, modes in model["buckets"].items():
        for mode, tables in modes.items():
            assert tables["unigrams"], (name, mode)


def test_counts_are_positive_ints_and_loops_sorted(model):
    for name, modes in model["buckets"].items():
        for mode, tables in modes.items():
            for ctx, row in list(tables["bigrams"].items())[:50]:
                assert all(isinstance(c, int) and c > 0 for c in row.values())
            for length, entries in tables["loops"].items():
                counts = [e["count"] for e in entries]
                assert counts == sorted(counts, reverse=True), (name, mode, length)
                assert all(len(e["progression"]) == int(length) for e in entries)


def test_every_unigram_token_spells_through_music21(model):
    from music21 import key as m21key, roman
    keys = {"major": m21key.Key("C", "major"), "minor": m21key.Key("A", "minor")}
    seen = set()
    for modes in model["buckets"].values():
        for mode, tables in modes.items():
            for figure in tables["unigrams"]:
                if (figure, mode) in seen:
                    continue
                seen.add((figure, mode))
                roman.RomanNumeral(figure, keys[mode])  # must not raise
    assert seen


def test_runtime_answers_from_real_model(model):
    import contextlib
    import io
    import json
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        cs.main(["next", "--context", "I,V", "--mode", "major",
                 "--model", str(ARTIFACT)])
    out = json.loads(buf.getvalue())
    assert out["candidates"]
