import re
from pathlib import Path

PLUGIN = Path(__file__).resolve().parents[2]
SKILL = PLUGIN / "skills" / "music-theory" / "SKILL.md"
KB = PLUGIN / "skills" / "music-theory" / "references" / "corpus-stats.md"
INDEX = PLUGIN / "reference" / "ROLE_INDEX.md"


def test_skill_names_script_and_all_three_subcommands():
    text = SKILL.read_text(encoding="utf-8")
    assert "chord_stats.py" in text
    for sub in ("next", "progressions", "diagnose"):
        assert re.search(rf"\b{sub}\b", text), sub
    assert "corpus-stats.md" in text


def test_kb_provenance_attribution_and_honesty():
    text = KB.read_text(encoding="utf-8")
    assert text.startswith("<!-- provenance:")
    low = text.lower()
    assert "license" in low and "attribution" in low
    assert "counts, not" in low  # honesty language pinned
    assert "limits" in low
    assert "billboard" in low and "when in rome" in low


def test_kb_relative_links_resolve():
    text = KB.read_text(encoding="utf-8")
    for target in re.findall(r"\]\(([^)#]+\.md)", text):
        assert (KB.parent / target).resolve().is_file(), target


def test_role_index_routes_corpus_questions():
    text = INDEX.read_text(encoding="utf-8")
    assert "comes next" in text
    assert "corpus stats" in text


def test_composer_pointer_present_and_resolves():
    hp = PLUGIN / "skills" / "composer" / "references" / "harmony-progressions.md"
    text = hp.read_text(encoding="utf-8")
    match = re.search(r"\]\((\.\./\.\./music-theory/references/corpus-stats\.md)\)", text)
    assert match
    assert (hp.parent / match.group(1)).resolve().is_file()


def test_theory_command_mentions_corpus():
    text = (PLUGIN / "commands" / "theory.md").read_text(encoding="utf-8")
    assert "corpus" in text.lower()
