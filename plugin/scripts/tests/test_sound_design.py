import re
from pathlib import Path
import yaml

PLUGIN = Path(__file__).resolve().parents[2]
SD = PLUGIN / "skills" / "sound-design"


def test_skill_exists_with_valid_frontmatter():
    fm = yaml.safe_load((SD / "SKILL.md").read_text(encoding="utf-8").split("---")[1])
    assert fm["name"] == "sound-design"
    assert len(fm["description"]) > 40


def test_command_exists():
    assert (PLUGIN / "commands" / "sound-design.md").is_file()


def test_role_index_lists_sound_design():
    text = (PLUGIN / "reference" / "ROLE_INDEX.md").read_text(encoding="utf-8")
    assert "`sound-design`" in text
    assert "/sound-design" in text


def test_all_reference_files_have_provenance_header():
    for p in (SD / "references").rglob("*.md"):
        first = p.read_text(encoding="utf-8").lstrip().splitlines()[0]
        assert first.startswith("> Provenance:"), f"{p} missing provenance header"


def test_synthesis_fundamentals_covers_core_topics():
    text = (SD / "references" / "synthesis-fundamentals.md").read_text(encoding="utf-8").lower()
    for topic in ("subtractive", "fm", "wavetable", "oscillator", "filter", "envelope", "lfo"):
        assert topic in text, f"synthesis-fundamentals missing '{topic}'"


REQUIRED_DESCRIPTORS = ["warm","bright","dark","punchy","wide","thin","muddy",
    "aggressive","lush","hollow","glassy","gritty","airy","boomy","harsh"]

def test_descriptor_dictionary_covers_required_terms():
    text = (SD / "references" / "descriptor-dictionary.md").read_text(encoding="utf-8").lower()
    missing = [d for d in REQUIRED_DESCRIPTORS if f"## {d}" not in text]
    assert not missing, f"descriptor-dictionary missing entries: {missing}"


SYNTH_GUIDES = ["polymer", "phase-4", "fm-4", "polysynth", "sampler"]

def test_stock_synth_guides_exist_with_provenance():
    for name in SYNTH_GUIDES:
        p = SD / "references" / "devices" / f"{name}.md"
        assert p.is_file(), f"missing device guide {name}.md"
        assert p.read_text(encoding="utf-8").lstrip().startswith("> Provenance:")


FX_GUIDES = ["fx-eq-plus","fx-filter","fx-saturator-distortion","fx-compressor",
    "fx-chorus-plus","fx-delay-plus","fx-reverb"]

def test_fx_guides_and_character_exist():
    for name in FX_GUIDES:
        assert (SD / "references" / "devices" / f"{name}.md").is_file(), name
    text = (SD / "references" / "fx-character.md").read_text(encoding="utf-8").lower()
    for topic in ("chain order", "saturation", "reverb", "eq", "compress"):
        assert topic in text, f"fx-character missing '{topic}'"


REQUIRED_RECIPES = ["808-bass","reese-bass","sub-bass","acid-bass","supersaw-lead",
    "pluck-lead","square-lead","warm-pad","lush-pad","ambient-drone","lofi-keys",
    "electric-piano","synth-pluck","noise-perc","bright-bell"]

def test_recipes_present_with_provenance():
    d = SD / "references" / "recipes"
    for name in REQUIRED_RECIPES:
        p = d / f"{name}.md"
        assert p.is_file(), f"missing recipe {name}.md"
        assert p.read_text(encoding="utf-8").lstrip().startswith("> Provenance:")
    assert len(list(d.glob("*.md"))) >= 15


def test_vst_fallback_exists_and_labels_best_effort():
    text = (SD / "references" / "vst-fallback.md").read_text(encoding="utf-8").lower()
    assert "best-effort" in text or "best effort" in text
    for topic in ("directparameter", "parameter name", "cutoff"):
        assert topic in text, f"vst-fallback missing '{topic}'"


def test_skill_reference_links_all_resolve():
    skill = (SD / "SKILL.md").read_text(encoding="utf-8")
    for rel in re.findall(r"references/[A-Za-z0-9_./-]+\.md", skill):
        assert (SD / rel).is_file(), f"SKILL links missing file: {rel}"
