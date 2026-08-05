import json
import re
from pathlib import Path

PLUGIN = Path(__file__).resolve().parents[2]


def test_manifest_valid():
    m = json.loads((PLUGIN / ".claude-plugin" / "plugin.json").read_text(encoding="utf-8"))
    assert m["name"] == "wigout-studio"
    assert re.fullmatch(r"\d+\.\d+\.\d+", m["version"])
    assert m["description"]


def test_mcp_config_points_at_bridge():
    c = json.loads((PLUGIN / ".mcp.json").read_text(encoding="utf-8"))
    assert c["mcpServers"]["bitwig"]["url"] == "http://localhost:61169/mcp"
    assert c["mcpServers"]["bitwig"]["type"] == "http"


def test_expected_directories_exist():
    for d in ("commands", "skills", "reference", "scripts"):
        assert (PLUGIN / d).is_dir(), f"missing plugin/{d}"
