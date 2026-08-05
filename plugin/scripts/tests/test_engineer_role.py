import re
from pathlib import Path

import yaml

PLUGIN = Path(__file__).resolve().parents[2]
ENG = PLUGIN / "skills" / "engineer"


def test_skill_frontmatter_is_full_role():
    text = (ENG / "SKILL.md").read_text(encoding="utf-8")
    fm = yaml.safe_load(text.split("---")[1])
    assert fm["name"] == "engineer"
    assert len(fm["description"]) > 40
    assert "stub" not in text.lower()
    assert "read-only" not in fm["description"].lower()


def test_skill_names_scripts_and_ladder():
    text = (ENG / "SKILL.md").read_text(encoding="utf-8")
    for needle in ("mix_report.py", "masking_check.py", "stem_split.py",
                   "master_match.py", "project-audio-access.md", "verified"):
        assert needle in text, f"SKILL.md missing '{needle}'"


def test_skill_reference_links_resolve():
    text = (ENG / "SKILL.md").read_text(encoding="utf-8")
    links = re.findall(r"(?<![\w/])references/[A-Za-z0-9_./-]+\.md", text)
    assert links, "SKILL.md links no references"
    for rel in links:
        assert (ENG / rel).is_file(), f"SKILL links missing file: {rel}"


def test_references_exist_with_provenance():
    for name in ("mix-workflow", "loudness-targets", "masking-and-space"):
        p = ENG / "references" / f"{name}.md"
        assert p.is_file(), f"missing {name}.md"
        assert p.read_text(encoding="utf-8").lstrip().startswith("> Provenance:")


def test_reference_cross_links_resolve():
    for p in (ENG / "references").glob("*.md"):
        for rel in re.findall(r"\]\(((?:\.\./)+[A-Za-z0-9_./-]+\.md)", p.read_text(encoding="utf-8")):
            assert (p.parent / rel).resolve().is_file(), f"{p.name}: broken {rel}"


def test_mix_workflow_covers_stages():
    text = (ENG / "references" / "mix-workflow.md").read_text(encoding="utf-8").lower()
    for stage in ("gain staging", "balance", "eq", "compress", "send", "master"):
        assert stage in text, f"mix-workflow missing '{stage}'"


def test_loudness_targets_covers_essentials():
    text = (ENG / "references" / "loudness-targets.md").read_text(encoding="utf-8").lower()
    for t in ("lufs", "true peak", "-14", "estimate"):
        assert t in text, f"loudness-targets missing '{t}'"


def test_masking_reference_ties_to_tooling():
    text = (ENG / "references" / "masking-and-space.md").read_text(encoding="utf-8").lower()
    for t in ("masking_check", "sidechain", "eq"):
        assert t in text, f"masking-and-space missing '{t}'"


def test_commands_are_full_role():
    for cmd in ("mix.md", "master.md"):
        text = (PLUGIN / "commands" / cmd).read_text(encoding="utf-8").lower()
        assert "v1" not in text and "read-only" not in text, f"{cmd} still stub-flavored"


def test_role_index_upgraded():
    text = (PLUGIN / "reference" / "ROLE_INDEX.md").read_text(encoding="utf-8")
    assert "stub" not in text.lower()
    assert "/mix" in text and "/master" in text
    assert "sound-design" in text


def test_render_ladder_documented():
    p = PLUGIN / "skills" / "bitwig-project" / "references" / "project-audio-access.md"
    text = p.read_text(encoding="utf-8").lower()
    # durable needles only - Gate R later replaces the "probe pending" statuses
    for needle in ("render-handoff", "export audio", "pre-fx", "stale file"):
        assert needle in text, f"project-audio-access missing '{needle}'"
