<!-- provenance: original distillation by Wigout Studio authors, 2026-07; progression names are standard theory, chord voicings realized by theory_engine.py (music21). -->
# Harmony Progressions

Fourteen progressions that carry most of popular music. For each: the Roman
numerals and the exact command to spell the actual notes **in any key**.
Never recall chord tones — run the engine and target the MIDI it returns.

Run from `plugin/scripts`; swap `--key` to transpose (`"F# minor"`,
`"Eb major"`, etc.). Output is JSON with each chord's `midi` array.

Corpus-grounded evidence beyond these fourteen — next-chord odds,
common loops, substitution diagnosis — is the music-theory role's
corpus stats: see
[corpus-stats.md](../../music-theory/references/corpus-stats.md).

## Major-key workhorses
1. **Axis / "four-chord song" — I–V–vi–IV** — uplifting, ubiquitous pop.
   `uv run python theory_engine.py progression --key "C major" --numerals I,V,vi,IV`
2. **Emotional / anthemic — vi–IV–I–V** — the Axis rotated to start minor.
   `uv run python theory_engine.py progression --key "C major" --numerals vi,IV,I,V`
3. **Doo-wop / '50s — I–vi–IV–V** — nostalgic, slow-dance.
   `uv run python theory_engine.py progression --key "C major" --numerals I,vi,IV,V`
4. **Pop punk / optimist — I–IV–vi–V** — driving, bright.
   `uv run python theory_engine.py progression --key "G major" --numerals I,IV,vi,V`
5. **Royal road (J-pop) — IV–V–iii–vi** — bittersweet lift-then-land.
   `uv run python theory_engine.py progression --key "C major" --numerals IV,V,iii,vi`
6. **Canon (Pachelbel) — I–V–vi–iii–IV–I–IV–V** — cascading, classical.
   `uv run python theory_engine.py progression --key "D major" --numerals I,V,vi,iii,IV,I,IV,V`

## Jazz / sophisticated
7. **ii–V–I turnaround** — the fundamental jazz cadence.
   `uv run python theory_engine.py progression --key "C major" --numerals ii,V,I`
8. **ii7–V7–Imaj7 (with sevenths)** — richer, the real jazz sound.
   `uv run python theory_engine.py progression --key "C major" --numerals ii7,V7,Imaj7`
9. **Rhythm changes A — I–vi–ii–V** — the circle turnaround, loops forever.
   `uv run python theory_engine.py progression --key "Bb major" --numerals I,vi,ii,V`
10. **Circle of fifths — vi–ii–V–I** — each root falls a fifth; smooth pull.
    `uv run python theory_engine.py progression --key "C major" --numerals vi,ii,V,I`

## Minor / dark
11. **Aeolian pop — i–VI–III–VII** — the go-to minor loop (EDM, rock, film).
    `uv run python theory_engine.py progression --key "A minor" --numerals i,VI,III,VII`
12. **Andalusian cadence — i–VII–VI–V** — flamenco descent, dramatic.
    `uv run python theory_engine.py progression --key "A minor" --numerals i,VII,VI,V`
13. **Aeolian rock vamp — i–VII–VI–VII** — rock and metal engine.
    `uv run python theory_engine.py progression --key "E minor" --numerals i,VII,VI,VII`
14. **Minor climb — i–III–VII–VI** — hopeful-in-the-dark, cinematic build.
    `uv run python theory_engine.py progression --key "D minor" --numerals i,III,VII,VI`

## Blues bonus — 12-bar
`uv run python theory_engine.py progression --key "E major" --numerals I7,IV7,I7,I7,IV7,IV7,I7,I7,V7,IV7,I7,V7`
Caveat: real blues wants **dominant** sevenths on I and IV, but the engine
spells `I7`/`IV7` diatonically as major-sevenths in a major key (only `V7`
comes out dominant). For authentic blues color, lower the 7th of each I/IV
chord a semitone after writing (or write the roots and add dominant-7
voicings by hand).

## Voice leading — make chords flow, not jump
The engine spells chords in root position, which can leap awkwardly between
changes. Good voicing moves each voice **the smallest distance** to the next
chord:
- Keep **common tones** — if two chords share a note, hold it in the same
  voice/register instead of re-stating it lower or higher.
- Move the other voices by **step or a small leap** to the nearest chord
  tone of the next chord. Invert chords (put the 3rd or 5th in the bass) to
  minimize motion.
- Keep the **bass** moving with intent (roots, or a walking line) while the
  inner voices stay put — motion where you want it, stillness elsewhere.
- Avoid **parallel fifths and octaves** between adjacent chords — they
  collapse independent voices into one and sound hollow.

After you write the chord parts into a clip, **check them**: read the notes
back via the bridge and run

    uv run python theory_engine.py voicecheck --notes-json <notes.json>

which flags parallel fifths/octaves. Re-voice the flagged chords (invert
one, or move the offending voice by step) and re-check until clean. Trust
the check over your memory — parallels are easy to write and hard to hear
until they stack up.

## How to use
1. Pick a progression by feel; set `--key` to your project key.
2. Run the command; write each chord's `midi` into the clip on the chord
   grid (usually one chord per bar or per two beats).
3. Voice for smooth motion, then `voicecheck`.
4. Target your melody at each chord's tones (see `melody-writing.md`).
