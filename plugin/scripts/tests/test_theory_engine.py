import argparse
import json

import pytest

import theory_engine as te


def notes_file(tmp_path, notes):
    p = tmp_path / "notes.json"
    p.write_text(json.dumps({"notes": notes}), encoding="utf-8")
    return str(p)


# I -> IV -> V triads in C, one per beat. Used for key inference too:
# a bare ascending scale is genuinely ambiguous between C major and its
# relative A minor (music21 picks A minor); a cadence is not.
C_CADENCE = (
    [{"pitch": p, "start": 0.0, "duration": 1.0} for p in (60, 64, 67)]
    + [{"pitch": p, "start": 1.0, "duration": 1.0} for p in (65, 69, 72)]
    + [{"pitch": p, "start": 2.0, "duration": 1.0} for p in (67, 71, 74)]
)


def test_key_detects_c_major(tmp_path):
    args = argparse.Namespace(notes_json=notes_file(tmp_path, C_CADENCE))
    out = te.cmd_key(args)
    assert out["key"] == "C major"
    assert 0.0 < out["confidence"] <= 1.0
    assert isinstance(out["alternatives"], list)


def test_roman_labels_cadence(tmp_path):
    args = argparse.Namespace(
        notes_json=notes_file(tmp_path, C_CADENCE), key="C major"
    )
    out = te.cmd_roman(args)
    assert [step["roman"] for step in out["analysis"]] == ["I", "IV", "V"]
    assert out["key"] == "C major"


def test_roman_infers_key_when_omitted(tmp_path):
    args = argparse.Namespace(notes_json=notes_file(tmp_path, C_CADENCE), key=None)
    out = te.cmd_roman(args)
    assert out["key"] == "C major"


def test_cli_error_on_empty_notes(tmp_path, capsys):
    p = notes_file(tmp_path, [])
    with pytest.raises(SystemExit) as exc:
        te.main(["key", "--notes-json", p])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)


def test_cli_error_on_missing_notes_key(tmp_path, capsys):
    p = tmp_path / "bad.json"
    p.write_text('{"foo": []}', encoding="utf-8")
    with pytest.raises(SystemExit) as exc:
        te.main(["key", "--notes-json", str(p)])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)


def test_scale_d_dorian():
    args = argparse.Namespace(root="D", mode="dorian")
    out = te.cmd_scale(args)
    assert out["pitches"] == ["D", "E", "F", "G", "A", "B", "C"]


def test_chord_cmaj7():
    args = argparse.Namespace(symbol="Cmaj7")
    out = te.cmd_chord(args)
    assert [m % 12 for m in out["midi"]] == [0, 4, 7, 11]


def test_progression_a_minor():
    args = argparse.Namespace(key="A minor", numerals="i,VI,III,VII")
    out = te.cmd_progression(args)
    assert len(out["progression"]) == 4
    assert [m % 12 for m in out["progression"][0]["midi"]] == [9, 0, 4]  # A C E


def test_voicecheck_flags_parallel_fifths(tmp_path):
    notes = [
        {"pitch": 60, "start": 0.0, "duration": 1.0},
        {"pitch": 67, "start": 0.0, "duration": 1.0},  # C-G = P5
        {"pitch": 62, "start": 1.0, "duration": 1.0},
        {"pitch": 69, "start": 1.0, "duration": 1.0},  # D-A = P5, both moved
    ]
    args = argparse.Namespace(notes_json=notes_file(tmp_path, notes))
    out = te.cmd_voicecheck(args)
    assert not out["clean"]
    assert out["issues"][0]["issue"] == "parallel fifths"


def test_voicecheck_clean_when_interval_changes(tmp_path):
    notes = [
        {"pitch": 60, "start": 0.0, "duration": 1.0},
        {"pitch": 67, "start": 0.0, "duration": 1.0},  # C-G
        {"pitch": 59, "start": 1.0, "duration": 1.0},
        {"pitch": 71, "start": 1.0, "duration": 1.0},  # interval changes 7 -> 0, excluded by iv equality
    ]
    args = argparse.Namespace(notes_json=notes_file(tmp_path, notes))
    out = te.cmd_voicecheck(args)
    assert out["clean"] or all(i["issue"] != "parallel fifths" for i in out["issues"])


def test_voicecheck_exempts_fifths_by_contrary_motion(tmp_path):
    notes = [
        {"pitch": 60, "start": 0.0, "duration": 1.0},
        {"pitch": 67, "start": 0.0, "duration": 1.0},  # C4-G4 = P5
        {"pitch": 58, "start": 1.0, "duration": 1.0},
        {"pitch": 77, "start": 1.0, "duration": 1.0},  # Bb3-F5: still P5 mod 12, voices move in OPPOSITE directions
    ]
    args = argparse.Namespace(notes_json=notes_file(tmp_path, notes))
    out = te.cmd_voicecheck(args)
    assert out["clean"]
