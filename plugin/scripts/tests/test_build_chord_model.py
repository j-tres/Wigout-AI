import pytest

from corpus import build_chord_model as bm


def test_parse_harte_roots_and_accidentals():
    assert bm.parse_harte("C:maj") == (0, "maj")
    assert bm.parse_harte("F#:min") == (6, "min")
    assert bm.parse_harte("Bb:7") == (10, "dom7")
    assert bm.parse_harte("Db:maj7") == (1, "maj7")
    assert bm.parse_harte("C") == (0, "maj")  # bare root implies maj (Harte)


def test_parse_harte_quality_normalization():
    # spec: 9/11/13 -> 7th family; sus/add/6/power -> parent triad;
    # hdim7/dim7 -> dim; minmaj7 -> min; inversions ignored
    cases = {
        "A:min7": (9, "min7"), "C:dim": (0, "dim"), "C:dim7": (0, "dim"),
        "A:hdim7": (9, "dim"), "C:aug": (0, "aug"),
        "C:9": (0, "dom7"), "C:13": (0, "dom7"), "C:maj9": (0, "maj7"),
        "A:min9": (9, "min7"), "D:sus4": (2, "maj"), "D:sus2": (2, "maj"),
        "E:5": (4, "maj"), "C:maj6": (0, "maj"), "A:min6": (9, "min"),
        "A:minmaj7": (9, "min"), "G:sus4(b7)": (7, "maj"),
    }
    for sym, want in cases.items():
        assert bm.parse_harte(sym) == want, sym


def test_parse_harte_min7b5_paren_alteration_is_dim():
    assert bm.parse_harte("B:min7(b5)") == (11, "dim")


def test_parse_harte_inversion_ignored():
    assert bm.parse_harte("C:maj/3") == (0, "maj")
    assert bm.parse_harte("G:7/b7") == (7, "dom7")


def test_parse_harte_unparseable_is_none():
    for sym in ("", "N", "X", "*", ".", "H:maj", "&pause", "x2", "?junk?"):
        assert bm.parse_harte(sym) is None, sym


def test_parse_harte_roots_agree_with_music21():
    # Cross-validate root pitch class against music21 for a fixed list.
    from music21 import harmony
    pairs = {"C:maj": "C", "A:min": "Am", "G:7": "G7", "F:maj7": "Fmaj7",
             "D:min7": "Dm7", "B:dim": "Bdim", "F#:min": "F#m",
             "Eb:maj7": "D#maj7", "Ab:min": "G#m"}
    for harte, m21sym in pairs.items():
        ours = bm.parse_harte(harte)
        assert ours is not None, harte
        assert ours[0] == harmony.ChordSymbol(m21sym).root().pitchClass, harte


def test_infer_key_clear_major():
    # C G7 Am F — classic C major
    got = bm.infer_key([(0, "maj"), (7, "dom7"), (9, "min"), (5, "maj")])
    assert got is not None
    assert (got["tonic"], got["mode"]) == (0, "major")
    assert got["corr"] > 0.5 and got["margin"] > 0


def test_infer_key_clear_minor():
    # Am Dm E7 Am — A minor (E7 pins the leading tone)
    got = bm.infer_key([(9, "min"), (2, "min"), (4, "dom7"), (9, "min")])
    assert got is not None
    assert (got["tonic"], got["mode"]) == (9, "minor")


def test_infer_key_rejects_low_confidence():
    assert bm.infer_key([(0, "aug")], min_corr=0.99) is None


def test_resolve_mode_with_known_tonic():
    major_chords = [(0, "maj"), (7, "dom7"), (9, "min"), (5, "maj")]
    got = bm.resolve_mode(major_chords, 0)
    assert got is not None and got["mode"] == "major"
    minor_chords = [(9, "min"), (2, "min"), (4, "dom7"), (9, "min")]
    got = bm.resolve_mode(minor_chords, 9)
    assert got is not None and got["mode"] == "minor"
    assert bm.resolve_mode(major_chords, 0, min_corr=0.999) is None


def test_render_numeral_core_figures():
    cases = [
        ((0, "maj", 0, "major"), "I"),
        ((9, "min", 0, "major"), "vi"),
        ((7, "dom7", 0, "major"), "V7"),
        ((0, "maj7", 0, "major"), "Imaj7"),
        ((2, "min7", 0, "major"), "ii7"),
        ((10, "maj", 0, "major"), "bVII"),
        ((8, "maj", 0, "major"), "bVI"),
        ((9, "min", 9, "minor"), "i"),
        ((0, "maj", 9, "minor"), "III"),
        ((5, "maj", 9, "minor"), "VI"),
        ((7, "maj", 9, "minor"), "VII"),
        ((4, "maj", 9, "minor"), "V"),
        ((8, "dim", 9, "minor"), "viio"),  # leading-tone dim in minor
    ]
    for args, want in cases:
        assert bm.render_numeral(*args) == want, args


def test_render_numeral_unspellable_returns_none():
    # 4 semitones above minor tonic (no clean degree) and non-dim on the
    # minor leading tone both drop.
    assert bm.render_numeral((9 + 4) % 12, "maj", 9, "minor") is None
    assert bm.render_numeral((9 + 11) % 12, "maj", 9, "minor") is None


def test_every_rendered_figure_parses_in_music21_with_right_root():
    from music21 import key as m21key, roman
    keys = {"major": (0, m21key.Key("C", "major")),
            "minor": (9, m21key.Key("A", "minor"))}
    rendered = 0
    for mode, (tonic, k) in keys.items():
        for deg in range(12):
            for quality in bm.CHORD_TONES:
                fig = bm.render_numeral((tonic + deg) % 12, quality, tonic, mode)
                if fig is None:
                    continue
                rendered += 1
                rn = roman.RomanNumeral(fig, k)
                assert rn.root().pitchClass == (tonic + deg) % 12, (mode, fig)
    assert rendered > 60  # the table must not have quietly collapsed


def test_match_quality_templates():
    # exact triads and sevenths
    assert bm.match_quality(0, {0, 4, 7}) == "maj"
    assert bm.match_quality(9, {9, 0, 4}) == "min"
    assert bm.match_quality(7, {7, 11, 2, 5}) == "dom7"
    assert bm.match_quality(11, {11, 2, 5}) == "dim"
    # supersets resolve to the largest embedded template (V9 -> dom7)
    assert bm.match_quality(7, {7, 11, 2, 5, 9}) == "dom7"
    # half-diminished pcs -> dim (min7 template lacks the natural 5th)
    assert bm.match_quality(11, {11, 2, 5, 9}) == "dim"
    # no template fits (augmented sixth shape) -> None
    assert bm.match_quality(8, {8, 0, 6}) is None


def test_render_numeral_transposition_invariant():
    # The reference-key validation must not anchor rendering to C/a.
    for tonic in range(12):
        assert bm.render_numeral(tonic, "maj", tonic, "major") == "I", tonic
        assert bm.render_numeral((tonic + 7) % 12, "dom7", tonic, "major") == "V7", tonic
        assert bm.render_numeral((tonic + 10) % 12, "maj", tonic, "major") == "bVII", tonic
        assert bm.render_numeral(tonic, "min", tonic, "minor") == "i", tonic
        assert bm.render_numeral((tonic + 8) % 12, "maj", tonic, "minor") == "VI", tonic


import json

AXIS_RUN = ["I", "V7", "vi", "IV", "I", "V7", "vi", "IV"]
MINOR_RUN = ["i", "VI", "III", "VII", "i", "VI"]


def _row(slice_="pop", major=(AXIS_RUN,), minor=()):
    return {"slice": slice_, "runs": {"major": [list(r) for r in major],
                                      "minor": [list(r) for r in minor]}}


def _params(**over):
    p = dict(min_corr=0.55, min_margin=0.03, min_count=1, min_bucket=1,
             top_loops=50, seed=7)
    p.update(over)
    return p


def test_build_model_structure_and_counts():
    model = bm.build_model([_row(), _row(), _row(minor=(MINOR_RUN,))],
                           _params())
    assert model["modelSchema"] == 1
    assert set(model["buckets"]) == {"pop", "global"}
    pop = model["buckets"]["pop"]["major"]
    assert pop["unigrams"]["I"] == 6 and pop["unigrams"]["vi"] == 6
    assert pop["bigrams"]["I"]["V7"] == 6
    assert pop["trigrams"]["I,V7"]["vi"] == 6
    loops4 = pop["loops"]["4"]
    assert loops4[0]["progression"] == ["I", "V7", "vi", "IV"]
    assert "scoreHistogram" in pop and sum(pop["scoreHistogram"]["counts"]) > 0
    minor = model["buckets"]["pop"]["minor"]
    assert minor["unigrams"]["i"] == 2  # one row contributed a minor run


def test_build_model_provenance_and_drops():
    drops = bm.new_drops()
    drops["unparseableToken"] = 5  # simulating reader-side drops
    rows = [_row(), {"slice": "pop", "runs": {"major": [], "minor": []}}]
    model = bm.build_model(rows, _params(), drops=drops)
    prov = model["provenance"]
    for field in ("corpus", "buildDate", "inputRows", "keptRows", "dropRate",
                  "drops", "params", "buckets", "slices", "license",
                  "attribution", "vocabularyNote"):
        assert field in prov, field
    assert prov["inputRows"] == 2
    assert prov["keptRows"] == 1  # empty-runs row dropped
    assert prov["dropRate"] == 0.5
    assert prov["drops"]["unparseableToken"] == 5
    assert prov["slices"] == {"pop": 1}


def test_build_model_slice_folding_and_global():
    rows = [_row("pop")] * 5 + [_row("classical")] * 1 + [_row(None)] * 1
    model = bm.build_model(rows, _params(min_bucket=3))
    assert "pop" in model["buckets"]
    assert "classical" not in model["buckets"]  # folded (below min_bucket)
    assert model["provenance"]["buckets"] == ["pop"]
    # global counts include ALL rows (incl. slice=None)
    g = model["buckets"]["global"]["major"]["unigrams"]
    assert g["I"] == 14


def test_build_model_prunes_below_min_count():
    model = bm.build_model([_row()], _params(min_count=100))
    pop = model["buckets"]["pop"]["major"]
    assert pop["bigrams"] == {} and pop["trigrams"] == {}
    assert pop["unigrams"]["I"] > 0  # unigrams never pruned


def test_cli_rows_json_end_to_end(tmp_path, capsys):
    rows_file = tmp_path / "rows.json"
    rows_file.write_text(json.dumps([_row(), _row()]), encoding="utf-8")
    out_file = tmp_path / "model.json"
    bm.main(["--rows-json", str(rows_file), "--out", str(out_file),
             "--min-count", "1", "--min-bucket", "1",
             "--corpus", "TestCorpus", "--license", "CC0",
             "--attribution", "nobody"])
    summary = json.loads(capsys.readouterr().out)
    assert summary["written"].endswith("model.json")
    written = json.loads(out_file.read_text(encoding="utf-8"))
    assert written["modelSchema"] == 1
    assert written["provenance"]["corpus"] == "TestCorpus"


def test_cli_errors_are_json_contract(tmp_path, capsys):
    for argv in ([], ["--rows-json", str(tmp_path / "missing.json")]):
        with pytest.raises(SystemExit) as exc:
            bm.main(argv)
        assert exc.value.code == 1
        assert "error" in json.loads(capsys.readouterr().out)


SALAMI = """# title: Test Song
# artist: Tester
# metre: 4/4
# tonic: C

0.0\tsilence
1.0\tA, intro, | C:maj | G:7 | A:min7 | F:maj |
5.0\tB, verse, | C:maj | C:maj | G:7 . A:min7 F:maj |
9.0\tC, bridge, | N | D:5 | ?junk? |
12.0\tend
"""

SALAMI_MODULATING = """# title: Two Keys
# tonic: C

1.0\tA, verse, | C:maj | G:7 | A:min | F:maj |
# tonic: D
5.0\tB, chorus, | D:maj | A:7 | B:min | G:maj |
"""

SALAMI_NO_TONIC = """# title: No Tonic

1.0\tA, verse, | C:maj | G:7 | A:min | F:maj | C:maj | G:7 |
"""


def test_billboard_reader_basic():
    drops = bm.new_drops()
    row = bm.billboard_song_row(SALAMI, drops)
    assert row["slice"] == "pop"
    assert row["runs"]["major"] == [
        ["I", "V7", "vi7", "IV", "I", "V7", "vi7", "IV"]]
    assert row["runs"]["minor"] == []
    assert drops["unparseableToken"] >= 1   # ?junk?
    assert drops["shortSegment"] >= 1       # lone D:5 after the N boundary


def test_billboard_reader_retonicizes_at_tonic_lines():
    drops = bm.new_drops()
    row = bm.billboard_song_row(SALAMI_MODULATING, drops)
    runs = row["runs"]["major"]
    assert len(runs) == 2
    assert runs[0] == ["I", "V7", "vi", "IV"]
    assert runs[1] == ["I", "V7", "vi", "IV"]  # D-anchored: A:7 is V7 of D


def test_billboard_reader_infers_key_without_tonic():
    drops = bm.new_drops()
    row = bm.billboard_song_row(SALAMI_NO_TONIC, drops)
    assert row["runs"]["major"] == [["I", "V7", "vi", "IV", "I", "V7"]]


def test_billboard_rows_walks_directory(tmp_path):
    d = tmp_path / "0003"
    d.mkdir()
    (d / "salami_chords.txt").write_text(SALAMI, encoding="utf-8")
    drops = bm.new_drops()
    rows = list(bm.billboard_rows(str(tmp_path), drops))
    assert len(rows) == 1 and rows[0]["slice"] == "pop"


RNTXT = """Composer: Test
Title: Tiny
Analyst: t
Time Signature: 4/4

m1 C: I b3 V7
m2 vi b3 IV
m3 I b3 V7
m4 d: i b3 iv
m5 V b3 i
m6 i b3 iv
"""


def test_wir_reader_splits_runs_at_key_changes(tmp_path):
    p = tmp_path / "analysis.rntxt"
    p.write_text(RNTXT, encoding="utf-8")
    drops = bm.new_drops()
    row = bm.wir_row(str(p), drops)
    assert row["slice"] == "classical"
    assert row["runs"]["major"] == [["I", "V7", "vi", "IV", "I", "V7"]]
    # m6 opens with i repeating m5's i -> collapsed
    assert row["runs"]["minor"] == [["i", "iv", "V", "i", "iv"]]


def test_wir_rows_respects_allowlist(tmp_path):
    allowed = tmp_path / bm.WIR_OPEN_SUBCORPORA[0]
    allowed.mkdir(parents=True)
    (allowed / "analysis.rntxt").write_text(RNTXT, encoding="utf-8")
    excluded = tmp_path / "Definitely_Not_Allowlisted_DCML"
    excluded.mkdir()
    (excluded / "analysis.rntxt").write_text(RNTXT, encoding="utf-8")
    drops = bm.new_drops()
    rows = list(bm.wir_rows(str(tmp_path), drops))
    assert len(rows) == 1


def test_wir_reader_txt_extension_and_malformed_file(tmp_path):
    # Real corpus files are analysis.txt (not .rntxt) — format must be hinted.
    good = tmp_path / "analysis.txt"
    good.write_text(RNTXT, encoding="utf-8")
    drops = bm.new_drops()
    row = bm.wir_row(str(good), drops)
    assert row["runs"]["major"], "txt-extension RomanText must parse via format hint"
    # A malformed file degrades to an empty row + counted drop, not a crash.
    bad = tmp_path / "analysis_bad.txt"
    bad.write_text("this is not roman text {{{", encoding="utf-8")
    row = bm.wir_row(str(bad), drops)
    assert row["runs"]["major"] == [] and row["runs"]["minor"] == []
    assert drops["unreadableFile"] >= 1


def test_wir_rows_excludes_automatic_and_fugue_analyses(tmp_path):
    base = tmp_path / bm.WIR_OPEN_SUBCORPORA[0]
    prelude = base / "01_prelude"
    prelude.mkdir(parents=True)
    (prelude / "analysis.txt").write_text(RNTXT, encoding="utf-8")
    (prelude / "analysis_automatic.rntxt").write_text(RNTXT, encoding="utf-8")
    fugue = base / "19_fugue"
    fugue.mkdir()
    (fugue / "analysis.txt").write_text(RNTXT, encoding="utf-8")
    drops = bm.new_drops()
    rows = list(bm.wir_rows(str(tmp_path), drops))
    assert len(rows) == 1  # only the human prelude analysis survives


def test_wir_row_excludes_tymoczko_attributed_analyses(tmp_path):
    p = tmp_path / "analysis.txt"
    p.write_text(RNTXT.replace("Analyst: t",
                               "Analyst: Dmitri Tymoczko and his computer"),
                 encoding="utf-8")
    drops = bm.new_drops()
    row = bm.wir_row(str(p), drops)
    assert row["runs"]["major"] == [] and row["runs"]["minor"] == []
    assert drops["excludedAnalyst"] == 1


def test_cli_reader_flags_end_to_end(tmp_path, capsys):
    bb = tmp_path / "billboard" / "0003"
    bb.mkdir(parents=True)
    (bb / "salami_chords.txt").write_text(SALAMI, encoding="utf-8")
    out_file = tmp_path / "model.json"
    bm.main(["--billboard-dir", str(tmp_path / "billboard"),
             "--out", str(out_file), "--min-count", "1", "--min-bucket", "1"])
    summary = json.loads(capsys.readouterr().out)
    assert summary["provenance"]["slices"] == {"pop": 1}
