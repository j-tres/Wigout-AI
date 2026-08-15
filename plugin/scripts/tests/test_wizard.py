import json
import socket as socket_module

import pytest

import wigout_config as wc
import wizard as wz


def test_scan_reports_system_config_candidates_and_bridge(tmp_path):
    config_path = tmp_path / "config.json"
    wc.save({"version": 1, "locations": {"projects": "C:/Music/Projects"}}, config_path)
    projects_candidate = tmp_path / "Documents" / "Bitwig Studio" / "Projects"
    projects_candidate.mkdir(parents=True)

    result = wz.scan(config_path=config_path, system="Windows", home=tmp_path, bridge_check=lambda: True)

    assert result["system"] == "Windows"
    assert result["config"]["locations"]["projects"] == "C:/Music/Projects"
    assert result["auto_detect_candidates"]["projects"] == str(projects_candidate)
    assert result["bridge_reachable"] is True
    assert result["extension_deployed"] is False


def test_scan_detects_deployed_extension(tmp_path):
    ext_dir = tmp_path / "Documents" / "Bitwig Studio" / "Extensions"
    ext_dir.mkdir(parents=True)
    (ext_dir / wz.EXTENSION_ASSET_NAME).write_bytes(b"fake")

    result = wz.scan(config_path=tmp_path / "config.json", system="Windows", home=tmp_path, bridge_check=lambda: False)

    assert result["extension_deployed"] is True


def test_write_locations_saves_only_provided_keys(tmp_path):
    config_path = tmp_path / "config.json"
    wc.save({"version": 1, "locations": {"projects": "OldPath"}}, config_path)

    result = wz.write_locations({"library": "D:/Samples"}, path=config_path)

    assert result["locations"]["projects"] == "OldPath"
    assert result["locations"]["library"] == "D:/Samples"
    reloaded = wc.load(config_path)
    assert reloaded["locations"]["library"] == "D:/Samples"


def test_write_locations_rejects_unknown_key(tmp_path):
    with pytest.raises(ValueError, match="unknown location key"):
        wz.write_locations({"not_a_real_key": "x"}, path=tmp_path / "config.json")


def test_cli_write_locations_happy_path(tmp_path, capsys, monkeypatch):
    config_path = tmp_path / "config.json"
    monkeypatch.setattr(wc, "config_path", lambda: config_path)

    wz.main(["write-locations", "--projects", "C:/Music/Projects"])

    out = json.loads(capsys.readouterr().out)
    assert out["locations"]["projects"] == "C:/Music/Projects"


def test_cli_write_locations_no_values_is_json_error(capsys):
    with pytest.raises(SystemExit) as exc:
        wz.main(["write-locations"])
    assert exc.value.code == 1
    err = json.loads(capsys.readouterr().out)
    assert "error" in err


class FakeCompletedProcess:
    def __init__(self, returncode=0, stdout=""):
        self.returncode = returncode
        self.stdout = stdout


def test_check_command_available_true_when_which_finds_it():
    assert wz.check_command_available("ffmpeg", which=lambda name: "/usr/bin/ffmpeg") is True


def test_check_command_available_false_when_which_finds_nothing():
    assert wz.check_command_available("ffmpeg", which=lambda name: None) is False


def test_install_hint_windows():
    assert "winget" in wz.install_hint("ffmpeg", system="Windows")


def test_install_hint_macos():
    assert "brew" in wz.install_hint("ffmpeg", system="Darwin")


def test_install_hint_linux():
    assert "apt" in wz.install_hint("ffmpeg", system="Linux")


def test_check_python_imports_true_on_zero_exit():
    result = wz.check_python_imports(["music21"], run=lambda *a, **k: FakeCompletedProcess(returncode=0))
    assert result is True


def test_check_python_imports_false_on_nonzero_exit():
    result = wz.check_python_imports(["basic_pitch"], run=lambda *a, **k: FakeCompletedProcess(returncode=1))
    assert result is False


def test_check_python_imports_builds_comma_joined_import_statement():
    captured = {}

    def fake_run(cmd, **kwargs):
        captured["cmd"] = cmd
        return FakeCompletedProcess(returncode=0)

    wz.check_python_imports(["music21", "librosa"], run=fake_run)

    assert captured["cmd"][-1] == "import music21, librosa"


def test_detect_gpu_returns_stripped_output_on_success():
    result = wz.detect_gpu(run=lambda *a, **k: FakeCompletedProcess(returncode=0, stdout="RTX 4070, 8188 MiB\n"))
    assert result == "RTX 4070, 8188 MiB"


def test_detect_gpu_returns_none_on_nonzero_exit():
    result = wz.detect_gpu(run=lambda *a, **k: FakeCompletedProcess(returncode=1, stdout=""))
    assert result is None


def test_detect_gpu_returns_none_when_nvidia_smi_missing():
    def fake_run(*a, **k):
        raise FileNotFoundError("no nvidia-smi")

    assert wz.detect_gpu(run=fake_run) is None


def test_claude_music_installed_true_when_dir_exists(tmp_path):
    cache = tmp_path / ".claude" / "plugins" / "cache" / "claude-music"
    cache.mkdir(parents=True)
    assert wz.claude_music_installed(home=tmp_path) is True


def test_claude_music_installed_false_when_dir_missing(tmp_path):
    assert wz.claude_music_installed(home=tmp_path) is False


def test_sync_dependencies_pins_python_then_syncs_both_groups_together():
    calls = []

    def fake_run(cmd, **kwargs):
        calls.append(cmd)
        return FakeCompletedProcess(returncode=0)

    wz.sync_dependencies(run=fake_run)

    assert calls[0] == ["uv", "python", "pin", "3.10"]
    assert calls[1] == ["uv", "sync", "--group", "mastering", "--group", "stems"]


def test_setup_check_skips_sync_when_check_only(tmp_path):
    calls = []

    def fake_run(cmd, **kwargs):
        calls.append(cmd)
        return FakeCompletedProcess(returncode=0, stdout="uv 0.9.5")

    wz.setup_check(
        check_only=True,
        run=fake_run,
        which=lambda name: "/usr/bin/" + name,
        home=tmp_path,
        system="Windows",
        bridge_check=lambda: True,
    )

    assert not any(cmd[:2] == ["uv", "sync"] for cmd in calls)


def test_setup_check_reports_all_fields(tmp_path):
    def fake_run(cmd, **kwargs):
        return FakeCompletedProcess(returncode=0, stdout="uv 0.9.5")

    report = wz.setup_check(
        check_only=True,
        run=fake_run,
        which=lambda name: "/usr/bin/" + name,
        home=tmp_path,
        system="Windows",
        bridge_check=lambda: True,
    )

    assert report["ffmpeg"] == "ok"
    assert report["python_env"] == "ok (3.10, deps importable)"
    assert report["matchering"] == "ok"
    assert report["audio_separator"] == "ok"
    assert report["bridge"] == "reachable on :61169"
    assert "RTX" not in report["gpu"]  # fake_run's stdout is "uv 0.9.5", passed straight through
    assert report["claude_music"] == "not installed - composer will offer acestep-api or MIDI-only"


def test_setup_check_reports_missing_ffmpeg_with_os_specific_hint(tmp_path):
    def fake_run(cmd, **kwargs):
        return FakeCompletedProcess(returncode=0, stdout="")

    report = wz.setup_check(
        check_only=True,
        run=fake_run,
        which=lambda name: None,
        home=tmp_path,
        system="Darwin",
        bridge_check=lambda: False,
    )

    assert "MISSING" in report["ffmpeg"]
    assert "brew" in report["ffmpeg"]
    assert report["bridge"] == "UNREACHABLE - is Bitwig running with the Wigout extension?"


def test_bridge_reachable_true_when_port_open():
    server = socket_module.socket(socket_module.AF_INET, socket_module.SOCK_STREAM)
    server.bind(("localhost", 0))
    server.listen(1)
    port = server.getsockname()[1]
    try:
        assert wz._bridge_reachable(port=port, timeout=1.0) is True
    finally:
        server.close()


def test_bridge_reachable_false_when_port_closed():
    server = socket_module.socket(socket_module.AF_INET, socket_module.SOCK_STREAM)
    server.bind(("localhost", 0))
    port = server.getsockname()[1]
    server.close()

    assert wz._bridge_reachable(port=port, timeout=1.0) is False


def test_extensions_dir_windows_prefers_onedrive(tmp_path):
    one_drive = tmp_path / "OneDrive"
    (one_drive / "Documents").mkdir(parents=True)
    (tmp_path / "Documents").mkdir()

    result = wz.extensions_dir(system="Windows", home=tmp_path, one_drive=str(one_drive))

    assert result == one_drive / "Documents" / "Bitwig Studio" / "Extensions"


def test_extensions_dir_windows_without_onedrive_uses_plain_documents(tmp_path):
    result = wz.extensions_dir(system="Windows", home=tmp_path, one_drive=None)
    assert result == tmp_path / "Documents" / "Bitwig Studio" / "Extensions"


def test_extensions_dir_mac(tmp_path):
    result = wz.extensions_dir(system="Darwin", home=tmp_path, one_drive=None)
    assert result == tmp_path / "Documents" / "Bitwig Studio" / "Extensions"


def test_extensions_dir_linux(tmp_path):
    result = wz.extensions_dir(system="Linux", home=tmp_path, one_drive=None)
    assert result == tmp_path / "Bitwig Studio" / "Extensions"


def test_deploy_extension_copies_local_source(tmp_path):
    source = tmp_path / "src" / wz.EXTENSION_ASSET_NAME
    source.parent.mkdir(parents=True)
    source.write_bytes(b"extension-bytes")
    ext_dir = tmp_path / "Extensions"

    result = wz.deploy_extension(source=str(source), extensions_dir_override=str(ext_dir))

    assert result == {"deployed": True, "source": "local", "path": str(ext_dir / wz.EXTENSION_ASSET_NAME)}
    assert (ext_dir / wz.EXTENSION_ASSET_NAME).read_bytes() == b"extension-bytes"


def test_deploy_extension_downloads_latest_release_when_no_source(tmp_path):
    ext_dir = tmp_path / "Extensions"

    result = wz.deploy_extension(extensions_dir_override=str(ext_dir), fetch_latest_release=lambda: b"downloaded-bytes")

    assert result["deployed"] is True
    assert result["source"] == "github-release"
    assert (ext_dir / wz.EXTENSION_ASSET_NAME).read_bytes() == b"downloaded-bytes"


def test_cli_deploy_extension_with_source(tmp_path, capsys):
    source = tmp_path / wz.EXTENSION_ASSET_NAME
    source.write_bytes(b"bytes")
    ext_dir = tmp_path / "Extensions"

    wz.main(["deploy-extension", "--source", str(source), "--extensions-dir", str(ext_dir)])

    out = json.loads(capsys.readouterr().out)
    assert out["deployed"] is True
    assert out["source"] == "local"


def test_mcp_snippet_is_a_plain_http_entry_for_generic_clients():
    # Deliberately NOT the plugin's own .mcp.json shape (that one's stdio,
    # to dodge Claude Desktop's HTTPS-only remote-connector flow). This is
    # the manual-paste snippet for CLI `claude mcp add` / claude_desktop_config.json,
    # where a plain local http entry is the correct, working choice.
    snippet = wz.mcp_snippet()
    assert snippet == {"mcpServers": {"Wigout-MCP": {"type": "http", "url": "http://localhost:61169/mcp"}}}


def test_mcp_snippet_respects_custom_host_and_port():
    snippet = wz.mcp_snippet(host="192.168.1.5", port=9999)
    assert snippet["mcpServers"]["Wigout-MCP"]["url"] == "http://192.168.1.5:9999/mcp"


def test_cli_mcp_snippet(capsys):
    wz.main(["mcp-snippet"])
    out = json.loads(capsys.readouterr().out)
    assert out["mcpServers"]["Wigout-MCP"]["url"] == "http://localhost:61169/mcp"


def test_cli_mcp_snippet_respects_custom_host_and_port(capsys):
    wz.main(["mcp-snippet", "--host", "192.168.1.5", "--port", "9999"])
    out = json.loads(capsys.readouterr().out)
    assert out == {"mcpServers": {"Wigout-MCP": {"type": "http", "url": "http://192.168.1.5:9999/mcp"}}}


def test_cli_scan_respects_custom_port_for_bridge_check(capsys):
    server = socket_module.socket(socket_module.AF_INET, socket_module.SOCK_STREAM)
    server.bind(("localhost", 0))
    port = server.getsockname()[1]
    server.close()

    wz.main(["scan", "--port", str(port)])

    out = json.loads(capsys.readouterr().out)
    assert out["bridge_reachable"] is False


def test_cli_scan_default_args_unchanged(capsys):
    wz.main(["scan"])
    out = json.loads(capsys.readouterr().out)
    assert "bridge_reachable" in out
    assert "auto_detect_candidates" in out
    assert "extension_deployed" in out


def test_diagnose_not_deployed_from_precomputed_scan():
    result = wz.diagnose(scan_result={"extension_deployed": False, "bridge_reachable": False})
    assert result["status"] == "not_deployed"
    assert "isn't installed" in result["message"]


def test_diagnose_unreachable_from_precomputed_scan():
    result = wz.diagnose(scan_result={"extension_deployed": True, "bridge_reachable": False})
    assert result["status"] == "unreachable"
    assert "running" in result["message"]


def test_diagnose_ok_from_precomputed_scan():
    result = wz.diagnose(scan_result={"extension_deployed": True, "bridge_reachable": True})
    assert result["status"] == "ok"


def test_diagnose_computes_its_own_scan_when_no_result_given(tmp_path):
    result = wz.diagnose(config_path=tmp_path / "config.json", system="Windows", home=tmp_path, bridge_check=lambda: True)
    assert result["status"] == "not_deployed"  # nothing deployed under a fresh tmp_path


def test_cli_diagnose_prints_status_and_message(capsys):
    wz.main(["diagnose", "--port", "1"])  # port 1 is reserved; never has a listener
    out = json.loads(capsys.readouterr().out)
    assert "status" in out
    assert "message" in out


def test_session_start_check_silent_when_already_deployed(tmp_path):
    ext_dir = tmp_path / "Documents" / "Bitwig Studio" / "Extensions"
    ext_dir.mkdir(parents=True)
    (ext_dir / wz.EXTENSION_ASSET_NAME).write_bytes(b"already-here")

    message = wz.session_start_check(
        config_path=tmp_path / "config.json", system="Windows", home=tmp_path, bridge_check=lambda: True
    )

    assert message is None


def test_session_start_check_auto_deploys_when_missing(tmp_path, monkeypatch):
    monkeypatch.setattr(wz, "extensions_dir", lambda **kwargs: tmp_path / "Extensions")
    monkeypatch.setattr(wz, "_fetch_latest_release_asset", lambda: b"downloaded-bytes")

    message = wz.session_start_check(
        config_path=tmp_path / "config.json", system="Windows", home=tmp_path, bridge_check=lambda: False
    )

    assert message is not None
    assert "Installed the Wigout AI extension" in message
    assert (tmp_path / "Extensions" / wz.EXTENSION_ASSET_NAME).read_bytes() == b"downloaded-bytes"


def test_session_start_check_reports_soft_failure_when_auto_deploy_fails(tmp_path, monkeypatch):
    monkeypatch.setattr(wz, "extensions_dir", lambda **kwargs: tmp_path / "Extensions")

    def _boom():
        raise RuntimeError("offline")

    monkeypatch.setattr(wz, "_fetch_latest_release_asset", _boom)

    message = wz.session_start_check(
        config_path=tmp_path / "config.json", system="Windows", home=tmp_path, bridge_check=lambda: False
    )

    assert "auto-install failed" in message
    assert "/studio setup" in message


def test_cli_session_start_check_silent_when_deployed(tmp_path, monkeypatch, capsys):
    ext_dir = tmp_path / "Documents" / "Bitwig Studio" / "Extensions"
    ext_dir.mkdir(parents=True)
    (ext_dir / wz.EXTENSION_ASSET_NAME).write_bytes(b"here")
    monkeypatch.setattr(wz, "extensions_dir", lambda **kwargs: ext_dir)

    wz.main(["session-start-check"])

    assert capsys.readouterr().out == ""
