import json
import os
import zipfile

import pytest

import dawproject_read as dr

# Element/attribute names verbatim from the DAWproject 1.0 README example
# (github.com/bitwig/dawproject): Note velocity attr is `vel` (0..1 float),
# group tracks nest child <Track> elements, master is Channel role="master".
PROJECT_XML = """<?xml version="1.0" encoding="UTF-8"?>
<Project version="1.0">
  <Application name="Bitwig Studio" version="5.3.1"/>
  <Transport>
    <Tempo unit="bpm" value="120.000000" id="id0" name="Tempo"/>
    <TimeSignature denominator="4" numerator="4" id="id1"/>
  </Transport>
  <Structure>
    <Track contentType="notes" loaded="true" id="t-bass" name="Bass" color="#a2eabf">
      <Channel audioChannels="2" destination="ch-master" role="regular" solo="false" id="ch-bass">
        <Devices>
          <ClapPlugin deviceID="org.surge-synth-team.surge-xt" deviceName="Surge XT" deviceRole="instrument" loaded="true" id="dev1" name="Surge XT"/>
        </Devices>
        <Mute value="false" id="mute-bass" name="Mute"/>
        <Pan max="1.0" min="0.0" unit="normalized" value="0.5" id="pan-bass" name="Pan"/>
        <Volume max="2.0" min="0.0" unit="linear" value="0.659140" id="vol-bass" name="Volume"/>
      </Channel>
    </Track>
    <Track contentType="audio" loaded="true" id="t-drums" name="Drums" color="#b53bba">
      <Channel audioChannels="2" destination="ch-master" role="regular" solo="false" id="ch-drums">
        <Mute value="false" id="mute-drums" name="Mute"/>
        <Pan max="1.0" min="0.0" unit="normalized" value="0.5" id="pan-drums" name="Pan"/>
        <Volume max="2.0" min="0.0" unit="linear" value="0.8" id="vol-drums" name="Volume"/>
      </Channel>
    </Track>
    <Track contentType="tracks" loaded="true" id="t-synths" name="Synths">
      <Channel audioChannels="2" destination="ch-master" role="regular" solo="false" id="ch-synths">
        <Mute value="false" id="mute-synths" name="Mute"/>
        <Volume max="2.0" min="0.0" unit="linear" value="1.0" id="vol-synths" name="Volume"/>
      </Channel>
      <Track contentType="notes" loaded="true" id="t-lead" name="Lead">
        <Channel audioChannels="2" destination="ch-synths" role="regular" solo="false" id="ch-lead">
          <Volume max="2.0" min="0.0" unit="linear" value="0.9" id="vol-lead" name="Volume"/>
        </Channel>
      </Track>
    </Track>
    <Track contentType="audio notes" loaded="true" id="t-master" name="Master">
      <Channel audioChannels="2" role="master" solo="false" id="ch-master">
        <Volume max="2.0" min="0.0" unit="linear" value="1.0" id="vol-master" name="Volume"/>
      </Channel>
    </Track>
  </Structure>
  <Arrangement id="arr">
    <Lanes timeUnit="beats" id="arr-lanes">
      <Lanes track="t-bass" id="lanes-bass">
        <Clips id="clips-bass">
          <Clip time="0.0" duration="4.0" playStart="0.0" name="Bassline A">
            <Notes id="notes-bass">
              <Note time="0.0" duration="0.5" channel="0" key="36" vel="0.8" rel="0.8"/>
              <Note time="1.0" duration="0.5" channel="0" key="38" vel="0.6" rel="0.6"/>
              <Note time="2.0" duration="1.0" channel="0" key="41" vel="0.7" rel="0.7"/>
            </Notes>
          </Clip>
        </Clips>
        <Points unit="linear" id="pts-vol">
          <Target parameter="vol-bass" expression="channelVolume"/>
          <RealPoint time="0.0" value="0.5" interpolation="linear"/>
          <RealPoint time="4.0" value="0.8" interpolation="linear"/>
        </Points>
      </Lanes>
      <Lanes track="t-drums" id="lanes-drums">
        <Clips id="clips-drums">
          <Clip time="0.0" duration="8.0" playStart="0.0" name="Loop">
            <Clips id="clips-drums-inner">
              <Clip time="0.0" duration="8.0" contentTimeUnit="beats" playStart="0.0">
                <Warps contentTimeUnit="seconds" timeUnit="beats" id="warps1">
                  <Audio algorithm="stretch" channels="2" duration="4.0" sampleRate="44100" id="audio1">
                    <File path="audio/loop.wav"/>
                  </Audio>
                  <Warp time="0.0" contentTime="0.0"/>
                  <Warp time="8.0" contentTime="4.0"/>
                </Warps>
              </Clip>
            </Clips>
          </Clip>
          <Clip time="8.0" duration="4.0" playStart="0.0" name="Fill"/>
        </Clips>
      </Lanes>
      <Points unit="bpm" id="pts-tempo">
        <Target parameter="id0"/>
        <RealPoint time="0.0" value="120.0" interpolation="hold"/>
      </Points>
      <Markers id="markers">
        <Marker time="0.0" name="Intro"/>
        <Marker time="16.0" name="Drop"/>
      </Markers>
    </Lanes>
  </Arrangement>
  <Scenes>
    <Scene name="Verse" id="scene1"/>
  </Scenes>
</Project>
"""


def make_dawproject(tmp_path, xml=PROJECT_XML, name="test.dawproject", extra=None):
    """Build a .dawproject ZIP fixture. extra=None gets the default embedded
    audio entry; pass extra={} for a bare project."""
    if extra is None:
        extra = {"audio/loop.wav": b"RIFF0000fake"}
    p = tmp_path / name
    with zipfile.ZipFile(p, "w") as zf:
        zf.writestr("project.xml", xml)
        zf.writestr("metadata.xml", "<MetaData/>")
        for n, data in extra.items():
            zf.writestr(n, data)
    return str(p)


def _track(digest_out, name):
    def walk(tracks):
        for t in tracks:
            if t["name"] == name:
                return t
            hit = walk(t["children"])
            if hit:
                return hit
    return walk(digest_out["tracks"])


def test_digest_track_tree_and_transport(tmp_path):
    out = dr.digest(make_dawproject(tmp_path))
    assert [t["name"] for t in out["tracks"]] == ["Bass", "Drums", "Synths", "Master"]
    assert out["tempo"] == 120.0
    assert out["timeSignature"] == "4/4"
    assert out["application"] == {"name": "Bitwig Studio", "version": "5.3.1"}
    synths = _track(out, "Synths")
    assert synths["type"] == "tracks"
    assert [c["name"] for c in synths["children"]] == ["Lead"]


def test_digest_channel_devices_clips(tmp_path):
    out = dr.digest(make_dawproject(tmp_path))
    bass = _track(out, "Bass")
    assert bass["devices"] == [{"name": "Surge XT", "type": "ClapPlugin"}]
    assert bass["channel"]["volume"] == pytest.approx(0.659140)
    assert bass["channel"]["mute"] is False
    assert bass["color"] == "#a2eabf"
    assert bass["arrangementClipCount"] == 1
    assert _track(out, "Drums")["arrangementClipCount"] == 2  # inner warp clip NOT counted
    assert _track(out, "Master")["arrangementClipCount"] == 0


def test_digest_markers_scenes_audio_tempo_automation(tmp_path):
    out = dr.digest(make_dawproject(tmp_path))
    assert out["markers"] == [{"time": 0.0, "name": "Intro"}, {"time": 16.0, "name": "Drop"}]
    assert out["scenes"] == ["Verse"]
    assert out["embeddedAudio"] == ["audio/loop.wav"]
    assert out["hasTempoAutomation"] is True
    assert out["fileModified"]  # ISO string present


def test_digest_default_subcommand_cli(tmp_path, capsys):
    dr.main([make_dawproject(tmp_path)])  # bare file -> digest
    out = json.loads(capsys.readouterr().out)
    assert out["tempo"] == 120.0


def test_cli_errors_are_json_contract(tmp_path, capsys):
    not_zip = tmp_path / "fake.dawproject"
    not_zip.write_bytes(b"not a zip at all")
    for argv in ([], ["digest"], ["digest", str(tmp_path / "missing.dawproject")], ["digest", str(not_zip)]):
        with pytest.raises(SystemExit) as exc:
            dr.main(argv)
        assert exc.value.code == 1
        err = json.loads(capsys.readouterr().out)
        assert "error" in err


# Two tracks whose names differ only by case, to pin the matching rules.
AMBIG_XML = """<?xml version="1.0" encoding="UTF-8"?>
<Project version="1.0">
  <Application name="Bitwig Studio" version="5.3.1"/>
  <Transport>
    <Tempo unit="bpm" value="120.0" id="id0" name="Tempo"/>
  </Transport>
  <Structure>
    <Track contentType="notes" id="t1" name="Lead"/>
    <Track contentType="notes" id="t2" name="lead"/>
  </Structure>
</Project>
"""


def test_notes_extraction(tmp_path):
    out = dr.notes(make_dawproject(tmp_path), "Bass")
    assert out["track"] == "Bass"
    assert len(out["clips"]) == 1
    clip = out["clips"][0]
    assert clip["clip"] == "Bassline A"
    assert clip["clipStart_beats"] == 0.0
    assert clip["notes"][0] == {"key": 36, "start_beats": 0.0, "duration_beats": 0.5,
                                "velocity": 0.8, "channel": 0}
    assert len(clip["notes"]) == 3


def test_notes_clip_filter_and_miss(tmp_path):
    path = make_dawproject(tmp_path)
    assert len(dr.notes(path, "Bass", "Bassline A")["clips"]) == 1
    with pytest.raises(ValueError, match="no clip named"):
        dr.notes(path, "Bass", "Nope")


def test_notes_nested_track_found_by_name(tmp_path):
    out = dr.notes(make_dawproject(tmp_path), "Lead")  # nested under Synths group
    assert out["track"] == "Lead"
    assert out["clips"] == []  # no arrangement clips on Lead in the fixture


def test_track_matching_rules(tmp_path):
    path = make_dawproject(tmp_path, xml=AMBIG_XML, extra={})
    with pytest.raises(ValueError, match="ambiguous"):
        dr.notes(path, "LEAD")  # case-insensitive hits both
    assert dr.notes(path, "Lead")["track"] == "Lead"  # exact match wins
    with pytest.raises(ValueError, match="not found.*Bass|Bass.*not found|tracks in file"):
        dr.notes(path, "Bass")


def test_automation_lane_listing(tmp_path):
    out = dr.automation(make_dawproject(tmp_path), "Bass")
    assert out["lanes"] == [{"parameter": "channelVolume", "target": "vol-bass",
                             "pointCount": 2}]


def test_automation_points(tmp_path):
    out = dr.automation(make_dawproject(tmp_path), "Bass", "channelVolume")
    assert out["parameter"] == "channelVolume"
    assert out["points"] == [
        {"time": 0.0, "value": 0.5, "interpolation": "linear"},
        {"time": 4.0, "value": 0.8, "interpolation": "linear"},
    ]


def test_automation_unknown_lane_lists_available(tmp_path):
    with pytest.raises(ValueError, match="channelVolume"):
        dr.automation(make_dawproject(tmp_path), "Bass", "channelPan")


def test_digest_track_without_channel(tmp_path):
    out = dr.digest(make_dawproject(tmp_path, xml=AMBIG_XML, extra={}))
    lead = out["tracks"][0]
    assert lead["channel"] is None
    assert lead["devices"] == []


REAL_DAWPROJECT = os.environ.get("WIGOUT_TEST_DAWPROJECT")


@pytest.mark.skipif(not REAL_DAWPROJECT, reason="WIGOUT_TEST_DAWPROJECT not set")
def test_real_export_digest_smoke():
    out = dr.digest(REAL_DAWPROJECT)
    assert out["tracks"]
    assert out["application"]["name"]
    assert out["fileModified"]
