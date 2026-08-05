import pytest

import wigout_config as wc


def test_load_returns_defaults_when_file_missing(tmp_path):
    config = wc.load(tmp_path / "config.json")
    assert config == {"version": 1, "locations": {}}


def test_save_then_load_round_trips_locations(tmp_path):
    config_path = tmp_path / "nested" / "config.json"
    wc.save({"version": 1, "locations": {"projects": "C:/Music/Projects"}}, config_path)

    assert config_path.exists()
    reloaded = wc.load(config_path)
    assert reloaded["locations"]["projects"] == "C:/Music/Projects"


def test_get_location_returns_none_when_key_absent(tmp_path):
    config_path = tmp_path / "config.json"
    wc.save({"version": 1, "locations": {}}, config_path)

    assert wc.get_location("projects", config_path) is None


def test_get_location_rejects_unknown_key(tmp_path):
    with pytest.raises(ValueError, match="unknown location key"):
        wc.get_location("not_a_real_key", tmp_path / "config.json")


def test_auto_detect_non_windows_returns_no_candidates(tmp_path):
    candidates = wc.auto_detect(system="Darwin", home=tmp_path)
    assert candidates == {}


def test_auto_detect_windows_finds_projects_folder(tmp_path):
    projects = tmp_path / "Documents" / "Bitwig Studio" / "Projects"
    projects.mkdir(parents=True)

    candidates = wc.auto_detect(system="Windows", home=tmp_path)

    assert candidates["projects"] == str(projects)


def test_auto_detect_windows_prefers_onedrive_redirect(tmp_path):
    one_drive = tmp_path / "OneDrive"
    expected = one_drive / "Documents" / "Bitwig Studio" / "Projects"
    expected.mkdir(parents=True)
    (tmp_path / "Documents" / "Bitwig Studio" / "Projects").mkdir(parents=True)  # plain fallback also exists

    candidates = wc.auto_detect(system="Windows", home=tmp_path, one_drive=str(one_drive))

    assert candidates["projects"] == str(expected)


def test_auto_detect_windows_no_projects_folder_returns_no_candidate(tmp_path):
    candidates = wc.auto_detect(system="Windows", home=tmp_path)
    assert candidates == {}
