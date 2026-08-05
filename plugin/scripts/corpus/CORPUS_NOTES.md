# Corpus spike — Chordonomicon license + schema verification

Spike performed 2026-07-11 against the live HuggingFace Hub. Dataset id confirmed unchanged
from the brief: **`ailsntua/Chordonomicon`** (no 404, no redirect to a different id).

## Dataset

- **HF dataset id:** `ailsntua/Chordonomicon`
- **Repo HEAD revision sha (main branch, at time of spike):** `f3a8b678bd65639c01caa342921733f1db01be23`
  (commit "Update README.md", 2025-05-15T18:32:51Z — `GET https://huggingface.co/api/datasets/ailsntua/Chordonomicon/refs`)
- **Data-file revision sha (last commit that touched `chordonomicon_v2.csv`):** `2ad317cdf347277d2ce38c1ff47ddfffdb5dbfea`
  (commit "Updated csv to contain Spotify track and artist IDs. ... Added new metadata regarding
  main genres and rock subgenres.", 2024-12-10T13:42:22Z). Every commit after this one on `main`
  only touches `README.md`, so the CSV bytes at the HEAD sha above are identical to this commit's.
  Both shas resolve to the same table content.
- **Row count:** 679,807 (`num_examples` from `datasets-server /info` and `/statistics`, and
  `num_rows_total` from `/rows`; all three agree)
- **Size:** 264,198,044 bytes download (`chordonomicon_v2.csv`, ~264 MB), 284,209,592 bytes in-memory
  dataset size (`dataset_info.default.splits.train`, from `datasets-server /info`)
- **File:** single CSV, `chordonomicon_v2.csv` (repo siblings: `.gitattributes`, `README.md`,
  `chordonomicon_v2.csv` — `GET https://huggingface.co/api/datasets/ailsntua/Chordonomicon`)
- **Split:** `train` only (single split)
- **Paper:** Kantarelis et al. (2024), "CHORDONOMICON: A Dataset of 666,000 Songs and their Chord
  Progressions", arXiv:2410.22046. Not fetched separately — the dataset card and machine-readable
  schema were sufficient to document every column; abstract fetch was skipped per the brief's
  "only if thin" clause.

## License verdict

### VERDICT: PAUSE

The license is NC-flavored (non-commercial). Per the task's decision rule this is **not** a GO —
it requires the user's explicit sign-off before Task 2 proceeds. Do not soften this to GO.

**Quoted evidence** (three independent sources, all agree):

1. Raw README.md YAML frontmatter (`GET https://huggingface.co/datasets/ailsntua/Chordonomicon/raw/main/README.md`):
   ```
   ---
   license: cc-by-nc-4.0
   ---
   ```
2. HF Hub API `cardData` (`GET https://huggingface.co/api/datasets/ailsntua/Chordonomicon`):
   ```json
   "cardData":{"license":"cc-by-nc-4.0"}
   ```
3. HF Hub API repo tags (same endpoint):
   ```json
   "tags":["license:cc-by-nc-4.0", ...]
   ```

This is **Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)**. It
permits sharing and adapting the material with attribution, but explicitly **prohibits use for
commercial purposes**. There is no separate "license prose" section on the dataset card beyond the
SPDX-style tag — the card body (README.md, quoted in full further down) contains no additional
license text, just the citation request.

**Attribution string to embed in any `chord_model.json` provenance block, if the user green-lights
proceeding under the NC restriction:**

```
Derived from Chordonomicon (Kantarelis, S., Thomas, K., Lyberatos, V., Dervakos, E., & Stamou, G.,
2024, "CHORDONOMICON: A Dataset of 666,000 Songs and their Chord Progressions", arXiv:2410.22046),
https://huggingface.co/datasets/ailsntua/Chordonomicon, licensed CC BY-NC 4.0
(https://creativecommons.org/licenses/by-nc/4.0/). This derived work is likewise restricted to
non-commercial use.
```

**Why this matters for a Claude Code plugin:** the plugin (`plugin/`) in this repo is published
inside a public repository (`Bitwig-mcp`, remote `Bitwig-mcp`). If any artifact built from this
corpus (e.g. `chord_model.json`, aggregate chord-transition statistics) ships inside the plugin,
that redistribution — even of *derived aggregate statistics*, not the raw corpus — sits inside the
license's "commercial purposes" gray zone if the plugin or repo is ever used commercially, and is
unambiguously restricted if it is. The brief's PAUSE branch applies: "the user decides (public
repo, personal non-commercial project)."

**This spike does not evaluate** whether *aggregate statistics* derived from an NC corpus (e.g.
bigram transition frequencies with no verbatim corpus rows) would themselves be judged a
"derivative work" under CC BY-NC 4.0 — that is a legal/product judgment call for the user, not
this spike.

## Field map

Schema fetched from `GET https://datasets-server.huggingface.co/info?dataset=ailsntua%2FChordonomicon`
(10 columns, all in a single `default` config / `train` split):

| Column | dtype | Meaning | Nullability (from `/statistics`) |
|---|---|---|---|
| `id` | int64 | Row/song identifier, 1..679807, no nulls | 0% nan |
| `chords` | string | Space-separated token sequence: chord symbols interleaved with `<section_n>` structural markers (see below) | 0% nan |
| `release_date` | string | ISO-ish date string, e.g. `"2003-01-01"` | 37.9% nan |
| `genres` | string | Raw subgenre list scraped from source metadata, single-quoted tokens space-separated, e.g. `"'alternative metal' 'alternative rock' 'nu metal'..."` (not the same as `main_genre`) | 36.8% nan |
| `decade` | float64 | Decade bucket, e.g. `2000.0`, `2020.0` | 37.9% nan |
| `rock_genre` | string | Single fine-grained rock subgenre label when the song is rock-adjacent, e.g. `"pop rock"`, `"canadian rock"`; 179 distinct values observed | 78.6% nan |
| `artist_id` | string | Synthetic per-dataset artist id, e.g. `"artist_1"` | 24.8% nan |
| `main_genre` | string | Coarse genre bucket, 12 distinct values (full inventory below) — **this is the field Task 4's `GENRE_MAP` should key on** | 48.2% nan |
| `spotify_song_id` | string | Spotify track id (22-char), e.g. `"2ffJZ2r8HxI5DHcmf3BO6c"` | 35.2% nan |
| `spotify_artist_id` | string | Spotify artist id (22-char) | 24.8% nan |

### `chords` format — verbatim samples

Confirmed: `chords` is a single space-separated string. Chord symbols use absolute root letters
(`C D E F G A B`, sharps spelled with a trailing `s`, e.g. `Fs` = F#, `Cs` = C#, `Gs` = G#, `As` =
A#, `Ds`/`Ds` = D#/Eb — no flat spellings observed) plus quality/extension suffixes (`min`, `7`,
`maj7`, `min7`, `no3d` for "no third") and slash-chord bass notation (e.g. `D/Fs`, `A/Cs`).
Structural markers appear **inline in the same space-separated stream** as `<label_n>` tokens
(underscore-numbered per repetition, e.g. `<verse_1>`, `<verse_2>`, `<chorus_1>`), immediately
before the chords belonging to that section, with no other delimiter.

Row `id=1` (`row_idx=0`, `main_genre="pop"`):
```
<intro_1> C <verse_1> F C E7 Amin C F C G7 C F C E7 Amin C F G7 C <verse_2> F C E7 Amin C F C G7 C F C E7 Amin C F G7 C <chorus_1> F C F C G C F C E7 Amin C F G7 C <solo_1> D <chorus_2> G D G D A D G D Fs7 Bmin D G A7 D G A7 D
```

Row `id=2` (`row_idx=1`, `main_genre="metal"`):
```
<intro_1> E D A/Cs E D A/Cs <verse_1> E D A/Cs E D A/Cs E D A/Cs E D A C <chorus_1> E G D A E G D A E G D A C D E D A/Cs <verse_2> E D A/Cs E D A/Cs E D A/Cs E D A C <chorus_2> E G D A E G D A E G D A C D <bridge_1> E C G D E C G D E C G D C D E G E G D A E G D A E G D A C D <chorus_3> E G D A E G D A E G D A C D <bridge_2> E C G D E C G D E C G D C D E
```

Row `id=8` (`row_idx=7`, `main_genre="pop"`) — shows `no3d` quality and a slash chord over a sharp root:
```
<intro_1> Fsmin Fsno3d Bno3d E/B Fsno3d Bno3d E/B Fsmin B <chorus_1> Asno3d Gsmin B Csmin Gsmin Csmin Gsmin Fs Bmin Fsmin Fsno3d Bno3d E/B Fsno3d Bno3d E/B Fsmin B <chorus_2> Asno3d Gsmin B Csmin Gsmin Csmin Gsmin Fs Bmin Fsmin Fsno3d Bno3d E/B Fsno3d Bno3d E/B Fsmin E/B Fsmin <outro_1> Bno3d E/B Fsmin Bno3d E/B Fsmin
```

Structural marker vocabulary observed across the 10-row sample (not exhaustive):
`<intro_n>`, `<verse_n>`, `<chorus_n>`, `<bridge_n>`, `<solo_n>`, `<interlude_n>`, `<outro_n>`.
The card additionally advertises "structural information related to different parts of the music
piece" generically; no closed enumeration of marker labels is published on the card.

### Raw README.md (fetched in full, verbatim)

```
---
license: cc-by-nc-4.0
---

# Chordonomicon
Chordonomicon: A Dataset of 666,000 Chord Progressions

Chordonomicon is a very large scale dataset featuring the symbolic representation of more than 666,000 contemporary music compositions through the use of music chords and chord progressions. We offer metadata for details such as genre, sub-genre, and release date. Additionally, we include structural information related to different parts of the music piece as well as Spotify IDs.

For a detailed description of the Chordonomicon Dataset, please see our paper on arXiv: https://doi.org/10.48550/arXiv.2410.22046. If you use this dataset, kindly cite the paper to acknowledge the work.

### Citation
> @article{kantarelis2024chordonomicon,
  title={CHORDONOMICON: A Dataset of 666,000 Songs and their Chord Progressions},
  author={Kantarelis, Spyridon and Thomas, Konstantinos and Lyberatos, Vassilis and Dervakos, Edmund and Stamou, Giorgos},
  journal={arXiv preprint arXiv:2410.22046},
  year={2024}


Visit our github: https://github.com/spyroskantarelis/chordonomicon
```

The card has **no dedicated column-documentation section** beyond this prose; the field map above
was reconstructed from the machine-readable schema (`datasets-server /info`) plus inspection of
actual row values (`datasets-server /rows`), which is why this spike step existed.

## Genre inventory

`main_genre` — full, exhaustive inventory (12 distinct values; source:
`GET https://datasets-server.huggingface.co/statistics?dataset=ailsntua%2FChordonomicon&config=default&split=train&column=main_genre`,
`column_type: "string_label"`, computed over all 679,807 rows, so these counts are exact, not
sampled):

| `main_genre` value | count |
|---|---|
| `pop` | 85,185 |
| `rock` | 67,238 |
| `country` | 53,306 |
| `alternative` | 47,252 |
| `pop rock` | 39,557 |
| `punk` | 16,066 |
| `metal` | 11,315 |
| `rap` | 11,186 |
| `soul` | 7,350 |
| `jazz` | 7,001 |
| `reggae` | 3,841 |
| `electronic` | 2,814 |
| **null / missing** | **327,696** |

Sanity check: 85185+67238+53306+47252+39557+16066+11315+11186+7350+7001+3841+2814 = 352,111;
352,111 + 327,696 nulls = 679,807 = total row count. Exact match — this is the complete,
non-sampled inventory, not an artifact of the 10-row preview.

Note for Task 4 (`GENRE_MAP`): **48.2% of rows have `main_genre = null`.** Any genre→bucket map
that only keys on `main_genre` silently drops nearly half the corpus; Task 4 should decide
explicitly whether null-`main_genre` rows are excluded, bucketed as "unknown", or whether the
free-text `genres` column is used as a fallback for those rows (that column is itself null on
36.8% of rows, with a different null population — not verified here whether the two null sets
overlap or are disjoint).

For completeness, `rock_genre` (a *finer-grained* label, populated only where a song is rock- or
rock-adjacent) has **179 distinct values** and is null on 78.6% of rows; full list captured in the
raw `/statistics` response but not reproduced here as it is out of scope for Task 4's top-level
`GENRE_MAP` (12 `main_genre` buckets). The dataset card does not publish a separate genre
inventory in prose — the table above is the complete authoritative enumeration, derived entirely
from the machine-readable statistics endpoint, not from card documentation.

## Fetch instructions

Exact commands used in this spike, plus the reproducible `load_dataset` call for Task 6:

```bash
# Card + license (raw markdown, includes YAML frontmatter)
curl -s "https://huggingface.co/datasets/ailsntua/Chordonomicon/raw/main/README.md"

# Repo metadata (license tag, cardData, siblings, current HEAD sha)
curl -s "https://huggingface.co/api/datasets/ailsntua/Chordonomicon"

# Branch/ref shas
curl -s "https://huggingface.co/api/datasets/ailsntua/Chordonomicon/refs"

# Schema + split sizes + revision sha
curl -s "https://datasets-server.huggingface.co/info?dataset=ailsntua%2FChordonomicon"

# 10 real sample rows
curl -s "https://datasets-server.huggingface.co/rows?dataset=ailsntua%2FChordonomicon&config=default&split=train&offset=0&length=10"

# Exact, non-sampled main_genre value counts
curl -s "https://datasets-server.huggingface.co/statistics?dataset=ailsntua%2FChordonomicon&config=default&split=train&column=main_genre"
```

Task 6's build command should pin the revision explicitly for reproducibility:

```python
from datasets import load_dataset

ds = load_dataset(
    "ailsntua/Chordonomicon",
    revision="f3a8b678bd65639c01caa342921733f1db01be23",  # main HEAD at spike time, 2025-05-15 README commit
    split="train",
)
```

The CSV content itself has not changed since commit `2ad317cdf347277d2ce38c1ff47ddfffdb5dbfea`
(2024-12-10); pinning to the later README-only commit above is equivalent but matches "current
main" convention. Either sha is safe to pin; recorded both for traceability.

## Concerns / issues for the user

- **License is CC BY-NC 4.0 (non-commercial), not one of the brief's GO-listed licenses.** Verdict
  is **PAUSE**, not GO. Per the task's gate: STOP after this notes file, report to the user with
  the quoted license text (done above), and let the user decide given this is a public repo
  (`Bitwig-mcp`, remote `Bitwig-mcp`) versus a personal non-commercial project.
- The dataset id, schema, and row count all matched the brief's expectations exactly — no
  surprises there (no 404, no id drift, `chords` + `main_genre` both present as named).
- `main_genre` is null on 48.2% of rows — flagged above for Task 4's `GENRE_MAP` design, not a
  blocker for this spike but material to downstream work.
- Two plausible "the revision" shas exist (repo HEAD vs. last commit touching the CSV); both
  resolve to byte-identical CSV content, but recorded both to avoid downstream ambiguity.

## Alternative corpus spike (2026-07-11)

Chordonomicon (above) was confirmed CC BY-NC 4.0 and the user rejected NC for this public repo.
This spike searches for a genuinely open replacement (or supplement). Every verdict below is from
a primary source fetched live during this spike — raw README/LICENSE files, official project pages,
or GitHub's own license-detection API — not from third-party blog posts or dataset-card claims
about *other* datasets. Where a source's page could not be fetched raw (cert/DNS issues), that is
called out explicitly and the verdict is marked accordingly conservative.

### Verdict table

| Candidate | License (quoted, source) | Size | Chords format | Genre metadata | Verdict |
|---|---|---|---|---|---|
| **McGill Billboard Project** (DDMAL, McGill) | **CC0.** "we have made them available legally under a CC0 license" / "To the extent possible under law, the DDMAL has waived all copyright and related or neighbouring rights to the McGill *Billboard* annotations." — [ddmal.ca, raw HTML](https://ddmal.ca/research/The_McGill_Billboard_Project_(Chord_Analysis_Dataset)/) | 890 annotated slots, **740 distinct songs** (site: "The set includes annotations and features for 890 slots... and comprises 740 distinct songs") | Absolute chord symbols, Harte et al. ISMIR-2005 syntax (`A:min`, `C:7`, `F#m7b5`...) **plus an explicit per-song/per-section `tonic` (key) field** — Roman-numeralization is a direct mechanical transform against the stated tonic, no key-detection step needed | **None.** Billboard Hot 100 chart entries only; no genre column anywhere in the index or annotation files | **GO** |
| **When in Rome** — CC BY-SA/CC0-verified slice only | **CC BY-SA 4.0** for "new content": "New content in this repository, including the new analyses, code, and the conversion (specifically) of existing analyses is available under the CC BY-SA licence" — [README.md](https://raw.githubusercontent.com/MarkGotham/When-in-Rome/master/README.md). Underlying scores for the lieder slice are separately **CC0** via [OpenScore/Lieder](https://github.com/OpenScore/Lieder) ("These scores are released under Creative Commons Zero (CC0). See LICENSE.txt.") | Cleanly verified GO slice: Bach Preludes (24 analyses, WTC I) + Ground bass works (Bach & Purcell) + 19th-c. lieder sample — a few hundred analyses, **not** the full 1,500-work headline count | **Native RomanText** — already Roman-numeral-encoded, zero absolute→Roman conversion needed | Corpus is organised by composer/era/genre-folder (e.g. `Early_Choral`, `Keyboard_Other`, `OpenScore-LiederCorpus`), not pop/rock/jazz-style tags | **GO (scoped)** — see caveat below |
| — same repo, DCML-sourced sub-corpora (Beethoven Qt, Mozart Sonatas, Chopin Mazurkas 1st set, part of Beethoven Piano Sonatas) | **CC BY-NC-SA 4.0**, confirmed directly at each source repo, e.g. [DCMLab/ABC README](https://raw.githubusercontent.com/DCMLab/ABC/main/README.md): "Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License (CC BY-NC-SA 4.0)"; same text confirmed in [DCMLab/mozart_piano_sonatas](https://raw.githubusercontent.com/DCMLab/mozart_piano_sonatas/main/README.md) and [DCMLab/romantic_piano_corpus](https://raw.githubusercontent.com/DCMLab/romantic_piano_corpus/main/README.md) | 16 quartets/70 movements + 18 sonatas + part of 56 Mazurkas + part of Beethoven Op. piano sonatas | RomanText (converted from DCML TSV) | n/a | **DISQUALIFIED (NC)** — these files live in the *same* When-in-Rome GitHub repo under the same repo-level CC-BY-SA badge, but the README itself says converted content keeps its **original** licence; must be excluded file-by-file from any assembled corpus, not assumed clean because of the repo badge |
| — same repo, Tymoczko/TAOM-supplied (Bach Chorales 371, Monteverdi madrigals 48, 2nd Chopin Mazurka set), TAVERN-sourced (Mozart/Beethoven variations), MTG-sourced (Haydn Op.20, 6 quartets), BPS-FH (Beethoven sonata 1st movements) | **No independently-stated open licence found.** These are listed in the README under "Corpora originating elsewhere" with "please refer to the original source for licence" — but the cited source for the Tymoczko/TAOM material is a *forthcoming book*, not a licensed repo; [jcdevaney/TAVERN README](https://raw.githubusercontent.com/jcdevaney/TAVERN/master/README.md) has no license statement at all; [Tsung-Ping/functional-harmony](https://api.github.com/repos/Tsung-Ping/functional-harmony) (BPS-FH) is **GPL-3.0** — a software copyleft licence, not on the brief's approved data-licence list and awkward to apply to a data compilation | 371 + 48 + ~27 + 6/24 movements + 32 movements | RomanText | n/a | **UNVERIFIED — exclude pending direct confirmation** (e.g. emailing the maintainer, who explicitly invites contact) |
| **OpenEWLD** (`00sapo/OpenEWLD`) | Fragmented and self-policing: MIT for code/db ([LICENSE](https://raw.githubusercontent.com/00sapo/OpenEWLD/master/LICENSE)); CC0 for Discogs-sourced genre/style fields; **CC BY-NC 3.0 for SecondHandSongs-sourced fields** (author/title/date); the musical content itself (the leadsheet MusicXML) is only *asserted*, not verified: "Compressed MusicXML files and Lyrics files are intended to contain only Public Domain content" and "All content of this repository should be free of copyright. If you think that some score is under copyright and I shouldn't distribute it, please open an issue." — [README.md](https://raw.githubusercontent.com/00sapo/OpenEWLD/master/README.md) | **502 songs** (counted `.mxl` files directly from the repo's git tree, commit `ec03cbd809ca5296ee708591b970d0423dcbe31c`) | Real `<harmony>` chord-symbol elements embedded in compressed MusicXML — confirmed by downloading and unzipping a sample (`The_Bells_of_St._Mary's.mxl`): `<root-step>F</root-step><kind>major</kind>`, `<root-step>C</root-step><kind text="7">dominant</kind>`, `<root-step>G</root-step><kind text="m">minor</kind>` | None | **LAST-RESORT / flagged concern** — this is exactly the "laundered provenance" pattern the brief warned about: source is the defunct Wikifonia archive (shut down in 2013 specifically because it **couldn't** maintain licensing for the copyrighted works it hosted), and the "public domain" filter is a single maintainer's best-effort, self-reported, uncorroborated claim, not a documented legal clearance process |
| **`ohollo/lmd_chords`** (HuggingFace) | **CC-BY-SA-4.0** (HF repo tag, `main` branch commit `9f1aa57ca750efd2eebda902b2e9757167a7432f`). This is a *legitimate new licence* the uploader placed on their own derived work (chord extraction) built from the Lakh MIDI Dataset (itself CC-BY 4.0) — not a relabeling of someone else's restricted data, so it does **not** trip the "laundered provenance" flag | 31,032 chord sequences (`num_rows_total` from `datasets-server`) | **Machine-extracted** absolute chord symbols (`chord-extractor`/Chordino method) — no key field, no manual verification/correction pass; sample row: `"title":"Raspberry Beret (LP Version)","artist":"Prince & The Revolution",...,"symbol":["N","Ab7","F#m","Gmaj7",...]` | None (title/artist/track_id/year only; `year` is 0/missing on most sampled rows) | **LAST-RESORT** — usable as a supplement/backup, but algorithmic extraction (unverified accuracy) and no key field, so Roman-numeralization would need a key-estimation step on top |
| **Lakh MIDI Dataset** (raw MIDI, `colinraffel.com/projects/lmd/`) | **CC-BY 4.0**: "The Lakh MIDI Dataset is distributed with a CC-BY 4.0 license" | 176,581 MIDI files (LMD-full); 45,129 matched to Million Song Dataset (LMD-matched) | MIDI only — chords not extracted; would need our own extraction pass (cost not yet paid, unlike `ohollo/lmd_chords` above) | Not included; would require an external genre-annotation layer (see next row) | **LAST-RESORT**, and only if `ohollo/lmd_chords` proves insufficient — no reason to redo extraction it already did |
| tagtraum MSD genre annotations (CD1/CD2, topMAGD) — the layer that would add genre to Lakh/MSD-derived data | **"Research only, strictly non-commercial."** — [tagtraum.com/msd_genre_datasets.html](https://www.tagtraum.com/msd_genre_datasets.html) | n/a (label layer) | n/a | Genre labels for MSD/LMD-matched tracks | **DISQUALIFIED (NC)** — cannot be used to backfill genre onto any LMD-derived chord corpus for a public repo |
| Nottingham Music Database (ABC folk tunes) | Original NMD: no blanket licence statement found on [abc.sourceforge.net/NMD](https://abc.sourceforge.net/NMD/); one named sub-collection states "the IPR associated with the original NMD format collection reside with Mick Peat" (implying the rest is unaddressed, not implicitly free); the popular "cleaned" mirror [jukedeck/nottingham-dataset](https://github.com/jukedeck/nottingham-dataset) self-labels the repackaging **GPLv3** with no evidence of a rights grant from the original transcribers | ~1,000–1,200 tunes | ABC chord symbols (e.g. `Cd` = dim, `Ca` = aug, `C/e` = slash chord) | None | **DISQUALIFIED** — unverifiable and likely-laundered rights on the underlying transcriptions (a third party applying GPLv3 to someone else's ambiguously-rights-held data is not a valid licence grant), and sub-scale even if it were clean |
| Isophonics reference annotations (Beatles/Queen/Carole King/Zweieck) | **No license or terms-of-use statement found anywhere on the official site** ([isophonics.net/content/reference-annotations](https://isophonics.net/content/reference-annotations) — fetched directly, no license/copyright text present on the page) | ~179 Beatles songs + a few dozen others (Queen compilations, Carole King's *Tapestry*, Zweieck's *Zwielicht*) ≈ 250–300 total | Absolute chord symbols (Harte syntax) + key annotations | None | **DISQUALIFIED** — no verifiable open licence at the source, and sub-scale regardless |
| RWC Music Database (AIST, Japan) | Historically pledge-gated research use only: "The databases, copies thereof, or data enabling the reproduction thereof may not be sold, leased, published or distributed to any third party" (per AIST's own distribution terms); per web search, a 2026 re-release moved it to **CC BY-NC 4.0** — still NC either way | n/a (not pursued further once NC confirmed both under old and new terms) | n/a | n/a | **DISQUALIFIED (NC)** |
| Chordonomicon (prior spike, listed here for completeness) | CC BY-NC 4.0 (re-confirmed unchanged from the prior spike; no new fetch needed) | 679,807 | absolute chords | 12-bucket `main_genre` (48.2% null) | **DISQUALIFIED (NC)** — user already rejected this |

Also checked and ruled out quickly via the HuggingFace dataset-search API (`search=chord`, `search=chord+progression`, `search=lead+sheet`; ~50 hits total): the overwhelming majority of HF "chord" datasets are **audio or image** chord-*recognition* datasets (isolated guitar-chord recordings/photos for classification, not song-level progressions) and are irrelevant to this spike. The few text/symbolic ones found were either mirrors of Chordonomicon itself (`Musictheory94/Chordonomicon`, `avgtrash/Chordonomicon` — same CC BY-NC 4.0), MIDI-derived (`ohollo/lmd_chords`, `MikeMpapa/lmd_clean_4bar_mulang_key_chordprog`, `asigalov61/Godzilla-Chords-Progressions` — the last is CC BY-NC-SA 4.0, also NC), or another Wikifonia-derived dataset with the same provenance concern as OpenEWLD (`Chord-Llama/chord_llama_dataset`, Apache-2.0-tagged but "sourced from Wikifonia and Part 1 of MScoreLib" — not independently investigated further given OpenEWLD already covers this territory more thoroughly and at larger scale).

### Recommendation

**Primary GO: McGill Billboard Project** (DDMAL / McGill University).

- **Source:** official page (static, no versioning) —
  `https://ddmal.ca/research/The_McGill_Billboard_Project_(Chord_Analysis_Dataset)/`. Direct-download
  archives are hosted on Dropbox (linked from that page); a convenience GitHub mirror with the
  identical files exists at `https://github.com/boomerr1/The-McGill-Billboard-Project`
  (commit `2234489f96e3cc69d4c000572011c7a717ed1ac5`, 2020-06-28 — static data, unlikely to change).
- **License:** CC0. Quoted in full above; attribution (citing the ISMIR 2011 paper or Burgoyne's
  2012 dissertation) is requested as a **scholarly norm**, not a legal requirement under CC0.
- **Scale:** 890 annotated Billboard-chart slots / 740 distinct songs. This is well below the
  brief's ideal ≥10k songs — **honest scale downgrade from the original Chordonomicon plan**
  (679,807 rows → ~740-890). If 10k+ scale is a hard requirement, this corpus alone cannot meet it;
  it would need to be a seed/validation set, or combined with the `ohollo/lmd_chords` last-resort
  candidate (31,032 machine-extracted sequences, also CC-BY-SA-4.0) to reach five-figure scale at
  the cost of mixing hand-verified and algorithmically-extracted chord quality.
- **Genre:** none. The original plan's per-song genre-bucket feature **cannot** be built from this
  corpus alone; it would collapse to a single global n-gram model, or genre would need to come from
  a separately-licensed source joined in by song title/artist (not attempted in this spike — every
  genre-annotation layer checked, tagtraum and topMAGD, turned out to be NC; see table above).
- **Field map / file format:** `salami_chords.txt` per song. Header block:
  ```
  # title: <song title>
  # artist: <artist name>
  # metre: <time signature, e.g. 4/4>
  # tonic: <pitch class of the opening key, e.g. C, Ab; "?" if no clear tonic>
  ```
  Body: one line per phrase/section, `<timestamp>\t<elements>` where elements are a comma-separated
  mix of structure letters (`A`, `B`...), plain-text section names (`verse`, `chorus`...), and
  bar-delimited chord sequences `| Chord1 | Chord2 | ... |` using Harte-et-al. absolute chord syntax
  (`A:min`, `C:7`, root:quality[/bass]). `tonic`/`metre` comments can recur mid-file on key/metre
  changes, making key-aware (and thus Roman-numeral) extraction straightforward per-section, not
  just per-song.
- **3 verbatim sample values** (fetched from the GitHub mirror, files `billboard-2.0-salami_chords/000{3,4}/salami_chords.txt`):
  ```
  # title: I Don't mind
  # artist: James Brown
  # metre: 6/8
  # tonic: C

  7.3469387e-2	A, intro, | A:min | A:min | C:maj | C:maj |
  ```
  ```
  22.346394557	B, verse, | A:min | A:min | C:maj | C:maj |, (voice
  36.279501133	| F:maj | F:maj | D:maj | D:maj |
  ```
  ```
  # title: You've got a Friend
  # artist: Roberta Flack and Donny Hathaway
  # metre: 4/4
  # tonic: Ab

  0.255419501	A, intro, | Ab:maj | Db:maj/5 | Ab:maj | G:hdim7 C:7 |, (synth)
  ```
- **Fetch instructions:**
  ```bash
  # Primary (official, Dropbox-hosted, linked from the DDMAL page — most durable citation target)
  curl -L -o billboard-2.0-salami_chords.tar.gz \
    "https://www.dropbox.com/s/2lvny9ves8kns4o/billboard-2.0-salami_chords.tar.gz?dl=1"
  curl -L -o billboard-2.0-index.csv \
    "https://www.dropbox.com/s/o0olz0uwl9z9stb/billboard-2.0-index.csv?dl=1"
  tar xzf billboard-2.0-salami_chords.tar.gz   # -> McGill-Billboard/<4-digit-id>/salami_chords.txt

  # Convenience GitHub mirror (same files, identical content, easier for CI/reproducibility)
  git clone https://github.com/boomerr1/The-McGill-Billboard-Project
  git -C The-McGill-Billboard-Project checkout 2234489f96e3cc69d4c000572011c7a717ed1ac5
  ```
- **Impact notes vs. the original plan:** scale drops ~1000x (679,807 → 740-890 songs); genre
  metadata is entirely absent, so Task 4's `GENRE_MAP`/genre-bucket feature has no per-song signal
  to key on from this corpus and would need to be dropped, deferred, or fed from a second,
  separately-licensed source; no extraction step needed (already lead-sheet-quality chord symbols
  with key), which is a meaningful quality win over every MIDI-based alternative — every chord here
  was placed by a human annotator, not inferred.

**Runner-up GO (scoped): When in Rome**, restricted to the verified-CC-BY-SA/CC0 slice only
(Bach Preludes 24 + Ground bass works + 19th-century lieder sample — **do not** pull in the
DCML-sourced (NC) or Tymoczko/TAVERN/MTG/BPS-FH (unverified) sub-corpora without separate
per-sub-corpus confirmation, despite them sitting in the same repo under the same top-level licence
badge). Its decisive advantage over McGill Billboard is that the chord data is **already
Roman-numeral** (RomanText format, e.g. `m1 b2 IV6 b3 V6`), eliminating the absolute→Roman
conversion step (and its dependency on accurate key detection) entirely — but the safely-usable
slice is on the order of a hundred pieces, smaller even than McGill Billboard, and skews entirely
classical (chorales/preludes/lieder), not the "genre spread" the original plan envisioned.
Combining both (McGill Billboard for absolute-chord/key-tagged pop scale, When in Rome's scoped
slice for a small amount of pre-verified, zero-conversion classical Roman-numeral ground truth) is
a reasonable path if the project wants both scale and a Roman-numeral sanity-check set.

**If the brief's ≥10k-song scale target is a hard requirement:** no single candidate found in this
spike clears both the licence gate and 10k+ scale simultaneously — that combination (Chordonomicon)
is exactly the one the user already rejected for its licence. Reaching five-figure scale under an
open licence, as things stand, means combining McGill Billboard (740-890, hand-verified, CC0) with
`ohollo/lmd_chords` (31,032, CC-BY-SA-4.0, machine-extracted-from-MIDI, last-resort quality) — an
honest scale/quality tradeoff, not a clean single-source answer.

### Concerns / issues for the user

- **No single candidate matches Chordonomicon's combination of scale (679,807), ready-made genre
  tags, and open licence.** Every path forward here is a tradeoff: McGill Billboard trades scale and
  genre for licence cleanliness and human-verified quality; When in Rome trades scale and genre
  breadth for zero-conversion Roman-numeral readiness; the MIDI-derived last-resorts trade
  verification quality for scale.
- **"Meta-corpus" repos can smuggle in NC-licensed content under a permissive-looking repo badge.**
  When in Rome's top-level CC-BY-SA-4.0 badge does **not** cover the DCML-sourced sub-corpora
  (confirmed CC BY-NC-SA 4.0 at the DCML source repos themselves) that live inside the same GitHub
  tree. Any automated corpus-assembly script must filter by sub-corpus provenance, not trust the
  repo-level licence tag.
- **Self-asserted "public domain" claims (OpenEWLD) are not the same as verified clearance.** Given
  Wikifonia's own shutdown was specifically caused by an inability to maintain licensing for
  copyrighted content it hosted, OpenEWLD's maintainer-level "should be free of copyright" filter is
  a real, flagged concern, not a clean pass — usable only as a last resort, and only with that
  caveat disclosed downstream.
- **No open-licensed genre-annotation layer was found for any MIDI/audio-based corpus.** tagtraum
  (MSD/topMAGD) is explicitly "research only, strictly non-commercial"; no alternative open genre
  layer was located in this spike.
- **Isophonics' apparent unlicensed status is worth a second look if the user wants to press further**
  — it is a long-standing, widely-cited MIR academic resource, so the absence of any license
  statement on its own site is more likely an oversight from an earlier era of academic data-sharing
  norms than a deliberate restriction, but this spike found nothing to quote in its favour and did
  not attempt to contact the maintainers.
