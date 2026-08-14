import json
import re
from pathlib import Path

PLUGIN = Path(__file__).resolve().parents[2]


def test_manifest_valid():
    m = json.loads((PLUGIN / ".claude-plugin" / "plugin.json").read_text(encoding="utf-8"))
    assert m["name"] == "wigout-studio"
    assert m["displayName"] == "Wigout Studio"
    assert re.fullmatch(r"\d+\.\d+\.\d+", m["version"])
    assert m["description"]


def test_mcp_config_wires_stdio_bridge():
    # A "type": "http" entry here would route Claude Desktop's plugin
    # install through its account-level, HTTPS-only remote-connector flow --
    # the wrong path for a server that only ever runs on localhost. stdio
    # sidesteps that: Claude spawns mcp_stdio_bridge.py locally instead.
    c = json.loads((PLUGIN / ".mcp.json").read_text(encoding="utf-8"))
    server = c["mcpServers"]["Wigout-MCP"]
    assert server["type"] == "stdio"
    assert server["command"] == "uv"
    assert server["args"] == [
        "run",
        "--project", "${CLAUDE_PLUGIN_ROOT}/scripts",
        "python", "${CLAUDE_PLUGIN_ROOT}/scripts/mcp_stdio_bridge.py",
    ]


def test_expected_directories_exist():
    for d in ("commands", "skills", "reference", "scripts"):
        assert (PLUGIN / d).is_dir(), f"missing plugin/{d}"
