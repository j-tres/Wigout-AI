<!-- provenance: original distillation by Wigout Studio authors, 2026-07. -->
# Rhythm and Groove

Groove is the feel between the notes. It comes from grid choice, swing,
where you place accents, and how the kick and bass share the bottom.

## Grid resolutions
The bridge writes notes at step positions. One bar of 4/4 divides as:
- **1/4** = 4 steps (the beats: 1, 5, 9, 13 in a 16-step bar)
- **1/8** = 8 steps
- **1/16** = 16 steps — the default working grid; step x = round(start_beats
  / step_beats)
- **1/32** = 32 steps — for rolls and fast hats (double the grid)
- **Triplets**: 1/8T = 12 per bar, 1/16T = 24 per bar — set the clip's grid
  to triplet mode; don't fake them on a straight 16 grid.

Pick the finest resolution any part needs, then place notes sparsely on it.

## Swing and shuffle
Straight 1/16s are rigid. **Swing** delays every *even* (offbeat) step,
pushing it later toward the following beat:
- **50%** = no swing (straight).
- **54-58%** = subtle groove — house, pop, most electronic.
- **60-66%** = obvious shuffle — lo-fi hip-hop, boom-bap, some funk.
- **66%+** = triplet shuffle — jazz, swung blues.

Apply swing to hats and the melodic parts but often leave the **kick
straight** so the pulse stays solid. In Bitwig, use the clip/global groove
amount, or nudge offbeat steps late by hand for per-part control.

## Syncopation — accent the "and"
Syncopation puts emphasis off the strong beats. On a 16 grid the strong
positions are 1, 5, 9, 13; the "e-and-a" are the steps between.
- Push a note **one step early** (anticipation) — e.g. bass hitting step 8
  instead of 9 lands ahead of beat 3 and creates forward drive.
- Accent the **"and" of a beat** (steps 3, 7, 11, 15) with velocity to make
  a part dance rather than march.
- Leave the **downbeat empty** on a lead and let the ear supply it — a
  classic funk/reggae move (the "one" is felt, not played).

## Layering — kick and bass pocket
Kick and bass fight for the same low frequencies; carve a **pocket** so
they lock instead of clash:
- **Lock**: bass note starts on the same step as the kick — huge, unified
  low end (pop, rock, most EDM).
- **Interlock**: bass plays *between* kicks (kick on 1, 5, 9, 13; bass on 3,
  7, 11, 15) — the offbeat bounce of house and funk.
- Either way, **sidechain** the bass to duck under each kick (Compressor
  keyed off the kick), or EQ+ a notch in the bass where the kick's
  fundamental sits. Two sub sources on the same step and same note = mud.

## Hat programming
Hats are the groove's engine. Don't use one flat velocity:
- Program 1/16 closed hats, then **accent the offbeats** (steps 3, 7, 11,
  15) ~15-25 velocity higher, and duck the "e/a" steps for a breathing
  16th-note feel.
- Alternate closed and open hats — open on step 3 or 15 gives the "tss"
  lift. Choke the open hat with a closed one on the next step.
- For trap, drop the grid to 1/32 or triplets on selected steps for
  **rolls**; ramp velocity up across the roll into the accent.

## Fills and variation
Static loops die. Break them to signal motion:
- Every **8 bars**, alter the drums — add a crash on the downbeat, drop the
  kick for a bar, or run a snare/tom fill on the last bar.
- **Snare roll build**: repeat the snare, halving spacing (1/8 → 1/16 →
  1/32) over the last 1–2 bars, velocity rising, into the next section.
- **Subtraction fill**: mute everything but one element for the last beat —
  the silence is the fill.
- Keep fills to the **last bar** of a phrase so they announce the change
  without derailing the groove.

## Method
1. Set the grid to the finest part's resolution.
2. Lay the kick (straight), then snare/clap backbeat, then hats.
3. Decide the kick↔bass relationship (lock or interlock) and place bass.
4. Add swing to offbeat/melodic parts; keep the kick honest.
5. Humanize velocity (accents on offbeats), add an 8-bar fill, read back.
