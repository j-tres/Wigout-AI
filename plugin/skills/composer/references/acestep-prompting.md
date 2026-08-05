<!-- provenance: adapted from claude-music plugin references/prompt-guide.md (MIT, (c) 2026 Daniel Agrici) and bitwize-music-studio/claude-ai-music-skills config/overrides.example/{suno-preferences,lyric-writing-guide}.md (CC0), 2026-07. All lyric examples original. -->
# ACE-Step Prompting

ACE-Step 1.5 turns a **caption** (the style prompt) plus optional **lyrics**
into audio. This is the audio-generation path (see the composer SKILL for
when it's active). Craft the caption and lyrics well and you iterate less.

## Captions
A caption is a comma-separated tag list, not a sentence. Formula:

    genre + mood + instruments + vocal style + production + era

Aim for **3–5 strong descriptors**. Too few and the model wanders; too many
and they conflict and mush together.

Tag palette (pick a few per axis):
- **Genre**: house, techno, lo-fi hip-hop, dnb, trap, ambient, synthwave,
  pop, rock, cinematic, jazz, r&b, folk, drill.
- **Mood**: melancholic, uplifting, dark, dreamy, nostalgic, euphoric,
  aggressive, intimate, haunting, triumphant.
- **Instruments**: piano, electric guitar, 808, synth pads, strings, brass,
  saxophone, plucked synth, vinyl crackle.
- **Vocal**: female vocal, male vocal, breathy, powerful, falsetto, raspy,
  choir, whispered, rap vocal, soulful.
- **Production/timbre**: warm, crisp, punchy, lush, lo-fi, spacious,
  reverb-heavy, dry, polished.
- **Era**: 80s synth-pop, 90s boom-bap, 2020s, vintage soul, futuristic.

Principles:
- **Specific beats vague**: `dark synthwave, analog saw lead, 80s, driving,
  reverb-heavy` outperforms `a cool retro song`.
- **Don't fight yourself**: avoid conflicting genres (`ambient trap metal`).
- **Repeat to reinforce**: naming a critical element twice weights it.
- **Keep BPM and key OUT of the caption** — pass them as `--bpm` and
  `--key` parameters, not tags.
- **Fewer tags = more model freedom.** Loosen the caption when you want it
  to surprise you; tighten when you need control.
- Maintain a project **genre-tag map** (user's word → ACE-Step tags, e.g.
  "dark electronic" → `dark techno, industrial, ebm`) so terminology stays
  consistent across generations.

## Lyrics and structure markers
Supply lyrics as plain text with **section tags in square brackets**. Tags
steer where the model places sections; they work best with `thinking: true`.

Supported markers: `[Intro]` `[Verse]` / `[Verse 1]` `[Pre-Chorus]`
`[Chorus]` `[Bridge]` `[Hook]` `[Drop]` `[Build]` `[Breakdown]`
`[Instrumental]` `[Guitar Solo]` `[Piano Interlude]` `[Outro]` `[Fade Out]`.

Skeleton (original example lyrics):

    [Intro]

    [Verse 1]
    Gray light on the kitchen floor
    Counting minutes by the door

    [Chorus]
    Hold the line, hold the line
    We were never out of time

    [Verse 2]
    Rain keeps writing on the glass
    ...

    [Bridge]
    (drop the drums, half-time)

    [Chorus]

    [Outro]

Lyric craft:
- **4–8 words per line** for clean vocal timing; break lines at phrases.
- **Simple, singable words** — avoid tongue-twisters and rare vocabulary.
- **Choruses repeat**; that's what makes a hook. But don't paste an
  identical chorus more than twice — vary a line to avoid repetition loops.
- **Match lyric mood to the caption** — melancholic words under a euphoric
  caption confuses the model.
- Verses run 4–6 lines, chorus ~4 lines; a callback to the opening line at
  the end lands well.
- **Density vs duration** (roughly): singing ~2–3 words/sec, rap ~4–5. A
  180s song ≈ 360–540 sung words / 600–900 rapped. Overstuffing causes the
  model to skip or slur words — when in doubt, fewer words.

Language: set `--language` to match the lyrics; spell non-English words
phonetically if pronunciation drifts.

Artifact fixes: lyric-skipping → cut word count / raise `guidance_scale`
(≈4–7); words bleeding → shorter lines, more breaks; timing off → match
density to duration.

## Generate vs Cover vs Repaint
Pick the mode by what you're starting from:
- **Generate** — text → audio from scratch. Caption (+ optional lyrics,
  bpm, key, duration). Use for new ideas.
- **Cover** — an existing audio input reinterpreted in a new style. Keeps
  the melody/structure, restyles timbre and production via a new caption.
  Use to reskin a demo (e.g. your Bitwig MIDI bounce → "orchestral") or to
  turn a hummed idea into a full arrangement.
- **Repaint** — regenerate a **selected time span** while keeping the rest.
  Use for surgical fixes: redo a weak second verse, swap a solo, fix one
  bad line — without rerolling the whole track and losing the good parts.

Rule of thumb: **generate** to explore, **cover** to restyle a whole take,
**repaint** to fix a region. Start at `draft`/`turbo` quality to audition,
then rerun the keeper at higher quality. Iterate the caption in small
edits — change one axis at a time so you learn what moved the result.

## Handing off
Generated audio lands as a file. Getting it into Bitwig follows the
composer SKILL's audio-import recipe (`InsertionPoint.insertFile`, or
manual drag until verified). Report which generation mode and quality you
used, honestly.
