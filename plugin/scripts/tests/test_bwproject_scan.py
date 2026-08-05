import json
import os

import pytest

import bwproject_scan as bs


def make_bwproject(tmp_path, name="test.bwproject"):
    data = (
        b"BtWg00010200" + b"\x00" * 16
        + b"application_version_name\x00\x055.3.2\x00"
        + b"\x01\x02" + "C:/Samples/Kick 01.wav".encode() + b"\x00"
        + "audio/loop.flac".encode() + b"\x00"
        + b"org.surge-synth-team.surge-xt\x00\x00"
        + "Bass Track".encode("utf-16-le") + b"\x00\x00"
        + bytes(range(256))
    )
    p = tmp_path / name
    p.write_bytes(data)
    return str(p)


def test_scan_buckets_and_version(tmp_path):
    out = bs.scan(make_bwproject(tmp_path))
    assert out["fidelity"] == "heuristic"
    assert out["bitwigVersionHint"] == "5.3.2"
    assert "C:/Samples/Kick 01.wav" in out["samplePaths"]
    assert "audio/loop.flac" in out["samplePaths"]
    assert "org.surge-synth-team.surge-xt" in out["pluginHints"]
    assert "Bass Track" in out["other"]  # recovered from UTF-16LE
    assert out["fileModified"]


def test_scan_dedup_and_no_cross_bucket(tmp_path):
    out = bs.scan(make_bwproject(tmp_path))
    everything = out["samplePaths"] + out["pluginHints"] + out["other"]
    assert len(everything) == len(set(everything))  # dedup, no double-bucketing
    assert "C:/Samples/Kick 01.wav" not in out["other"]


def test_bad_magic_is_error(tmp_path):
    p = tmp_path / "fake.bwproject"
    p.write_bytes(b"NOPE" + b"\x00" * 64)
    with pytest.raises(ValueError, match="BtWg"):
        bs.scan(str(p))


def test_cli_errors_are_json_contract(tmp_path, capsys):
    for argv in ([], [str(tmp_path / "missing.bwproject")]):
        with pytest.raises(SystemExit) as exc:
            bs.main(argv)
        assert exc.value.code == 1
        err = json.loads(capsys.readouterr().out)
        assert "error" in err


def test_cli_happy_path(tmp_path, capsys):
    bs.main([make_bwproject(tmp_path)])
    out = json.loads(capsys.readouterr().out)
    assert out["fidelity"] == "heuristic"


def test_utf16_run_not_absorbing_preceding_ascii(tmp_path):
    # ASCII id terminated by a SINGLE null, immediately followed by UTF-16LE:
    # the recovered string must be "Bass Track", not "tBass Track".
    data = (b"BtWg0001"
            + b"org.surge-synth-team.surge-xt\x00"
            + "Bass Track".encode("utf-16-le") + b"\x00\x00")
    p = tmp_path / "adj.bwproject"
    p.write_bytes(data)
    out = bs.scan(str(p))
    assert "Bass Track" in out["other"]
    for bucket in (out["samplePaths"], out["pluginHints"], out["other"]):
        assert "tBass Track" not in bucket


def test_utf16_run_preserves_first_char_after_stray_printable(tmp_path):
    # A single stray printable byte before a genuine UTF-16LE run must not
    # cost the run its first character ("ass Track" regression).
    data = b"BtWg0001" + b"\x00\x41" + "Bass Track".encode("utf-16-le") + b"\x00\x00"
    p = tmp_path / "stray.bwproject"
    p.write_bytes(data)
    out = bs.scan(str(p))
    assert "Bass Track" in out["other"]
    for bucket in (out["samplePaths"], out["pluginHints"], out["other"]):
        assert "ass Track" not in bucket


def test_version_fallback_and_absent(tmp_path):
    p = tmp_path / "fb.bwproject"
    p.write_bytes(b"BtWg0001" + b"Bitwig Studio 5.2\x00")
    assert bs.scan(str(p))["bitwigVersionHint"] == "5.2"
    p2 = tmp_path / "none.bwproject"
    p2.write_bytes(b"BtWg0001" + b"\x00\x01\x02nothing here")
    assert bs.scan(str(p2))["bitwigVersionHint"] is None


REAL_BWPROJECT = os.environ.get("WIGOUT_TEST_BWPROJECT")


@pytest.mark.skipif(not REAL_BWPROJECT, reason="WIGOUT_TEST_BWPROJECT not set")
def test_real_bwproject_smoke():
    out = bs.scan(REAL_BWPROJECT)
    assert out["fidelity"] == "heuristic"
    assert out["samplePaths"] or out["pluginHints"] or out["other"]
