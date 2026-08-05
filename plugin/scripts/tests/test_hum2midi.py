import json

import numpy as np
import pytest
import soundfile as sf

import hum2midi


SR = 22050


def synth_melody(tmp_path, freqs_durs):
    """Render a sequence of (freq_hz, dur_s) sine notes to a wav; return path."""
    chunks = []
    for freq, dur in freqs_durs:
        t = np.linspace(0, dur, int(SR * dur), endpoint=False)
        env = np.minimum(1.0, np.minimum(t / 0.02, (dur - t) / 0.02))  # declick
        chunks.append(0.6 * env * np.sin(2 * np.pi * freq * t))
    wav = tmp_path / "melody.wav"
    sf.write(wav, np.concatenate(chunks), SR)
    return str(wav)


def test_pyin_transcribes_three_note_melody(tmp_path):
    # A4, C5, E5 — MIDI 69, 72, 76
    wav = synth_melody(tmp_path, [(440.0, 0.5), (523.25, 0.5), (659.25, 0.5)])
    events = hum2midi.pyin_notes(wav)
    pitches = [e["pitch"] for e in events]
    assert pitches == [69, 72, 76]
    assert events[0]["start_s"] < 0.1
    assert 0.3 < events[0]["duration_s"] <= 0.6


def test_pyin_ignores_silence(tmp_path):
    t = np.zeros(SR)  # 1s silence
    wav = tmp_path / "silence.wav"
    sf.write(wav, t, SR)
    assert hum2midi.pyin_notes(str(wav)) == []


def test_to_beats_at_120bpm():
    events = [{"pitch": 69, "start_s": 0.5, "duration_s": 0.5}]
    out = hum2midi.to_beats(events, bpm=120.0)
    assert out[0]["start_beats"] == 1.0
    assert out[0]["duration_beats"] == 1.0


def test_write_midi_roundtrip(tmp_path):
    events = hum2midi.to_beats(
        [{"pitch": 69, "start_s": 0.0, "duration_s": 0.5}], bpm=120.0
    )
    mid = tmp_path / "out.mid"
    hum2midi.write_midi(events, bpm=120.0, out_path=str(mid))
    from music21 import converter

    notes = list(converter.parse(str(mid)).flatten().notes)
    assert [n.pitch.midi for n in notes] == [69]


# resampy (pulled in lazily by librosa) imports pkg_resources, which warns on
# setuptools 80.x; the pin exists for matchering/audio-separator (pyproject).
@pytest.mark.filterwarnings("ignore:pkg_resources is deprecated")
def test_basic_pitch_transcribes_sine(tmp_path):
    pytest.importorskip("basic_pitch")
    wav = synth_melody(tmp_path, [(440.0, 1.0)])
    events = hum2midi.basic_pitch_notes(wav)
    assert any(e["pitch"] == 69 for e in events)


@pytest.mark.filterwarnings("ignore:PySoundFile failed")
@pytest.mark.filterwarnings("ignore::FutureWarning:librosa")
def test_cli_error_on_missing_file(capsys):
    with pytest.raises(SystemExit) as exc:
        hum2midi.main(["does-not-exist.wav", "--bpm", "120"])
    assert exc.value.code == 1
    assert "error" in json.loads(capsys.readouterr().out)


def test_cli_poly_unavailable_reports_json_error(tmp_path, capsys, monkeypatch):
    wav = synth_melody(tmp_path, [(440.0, 0.3)])
    def raise_import_error(path):
        raise ImportError("basic_pitch missing")
    monkeypatch.setattr(hum2midi, "basic_pitch_notes", raise_import_error)
    with pytest.raises(SystemExit) as exc:
        hum2midi.main([wav, "--mode", "poly", "--bpm", "120"])
    assert exc.value.code == 1
    err = json.loads(capsys.readouterr().out)
    assert "--mode mono" in err["error"]
