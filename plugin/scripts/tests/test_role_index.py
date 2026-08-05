import re
from pathlib import Path

import yaml

PLUGIN = Path(__file__).resolve().parents[2]
INDEX = PLUGIN / "reference" / "ROLE_INDEX.md"


def skills_on_disk():
    return {p.parent.name for p in (PLUGIN / "skills").glob("*/SKILL.md")}


def commands_on_disk():
    return {p.stem for p in (PLUGIN / "commands").glob("*.md")}


def test_index_exists():
    assert INDEX.is_file()


def test_catalog_rows_match_skills_exactly():
    rows = set(re.findall(r"^\|\s*`([a-z0-9-]+)`\s*\|", INDEX.read_text(encoding="utf-8"), re.M))
    assert rows == skills_on_disk()


def test_every_command_appears_in_index():
    text = INDEX.read_text(encoding="utf-8")
    for cmd in commands_on_disk():
        assert f"/{cmd}" in text, f"/{cmd} missing from ROLE_INDEX"


def test_all_skill_frontmatter_parses():
    for p in (PLUGIN / "skills").glob("*/SKILL.md"):
        fm = yaml.safe_load(p.read_text(encoding="utf-8").split("---")[1])
        assert fm["name"] == p.parent.name
        assert len(fm["description"]) > 40


def test_all_command_frontmatter_parses():
    for p in (PLUGIN / "commands").glob("*.md"):
        fm = yaml.safe_load(p.read_text(encoding="utf-8").split("---")[1])
        assert fm["description"]
