<!-- provenance: adapted from claude-music plugin references/song-structures.md (MIT, (c) 2026 Daniel Agrici) and bitwize-music-studio/claude-ai-music-skills (CC0), 2026-07. -->
# Song Structures

Structure is where energy lives over time. Pick a form before you write a
note — it tells you how many clips to make and how they should differ.

In Bitwig, treat **one launcher scene = one song section**. A scene holds a
clip per track; firing scenes in order auditions the arrangement before you
commit it to the timeline. Name scenes for their section (`Intro`, `Verse
1`, `Chorus`, `Drop`) so the map is legible. Bars below assume 4/4; a bar =
16 steps at 1/16 resolution.

## Verse–Chorus (pop, rock, most songs with words)
The workhorse. Chorus is the emotional and dynamic peak; the verse pulls
back so the chorus can hit.

```
Intro       4-8 bars   low       just drums + one hook element
Verse 1     16 bars    mid       full groove, sparse top end
Pre-Chorus  8 bars     rising    add tension: filter opens, drums build
Chorus      16 bars    HIGH      widest arrangement, biggest hook
Verse 2     16 bars    mid       reuse Verse 1 clips, mutate one part
Pre-Chorus  8 bars     rising
Chorus      16 bars    HIGH
Bridge      8 bars     contrast  new chords/texture, drop an element
Chorus      16 bars    HIGHEST   final lift — add an octave or a layer
Outro       8 bars     falling   subtract to the intro's palette
```
Scene tip: build one `Chorus` scene, then duplicate it for each repeat and
add one element per repeat (see `arrangement-energy.md`, addition rule).

## AABA (jazz standards, Tin Pan Alley, some ballads)
Two A sections state the theme, B (the "middle eight") contrasts, final A
resolves. 8 bars each, 32-bar form. Energy is flatter than verse-chorus —
contrast comes from harmony and melody, not from dropping the beat.

## EDM Build–Drop (house, techno, dubstep, festival)
Energy is engineered, not sung.

```
Intro       16-32 bars  low       DJ-friendly: drums + one riff, mixable
Breakdown   16 bars     ambient   strip drums, expose melody/pads
Build       8-16 bars   RISING    riser, snare roll (1/16 -> 1/32), filter up
Drop        16-32 bars  PEAK      full bass + lead, four-on-floor locked
Breakdown   16 bars     reset
Build       8-16 bars   RISING
Drop 2      16-32 bars  PEAK      variation — new lead or heavier bass
Outro       16-32 bars  low       drums-only tail for mixing
```
The 32-bar intro/outro exist so DJs can beatmatch — keep them loop-stable.

## Loop-Based / Lo-Fi (lo-fi hip-hop, ambient, beats)
No verse/chorus. One 4–8 bar loop, evolved by addition and subtraction.
Arrange as `A / A' / B / A''` where each variation swaps or mutes one part.
Perfect for launcher workflow: make 3–4 scene variants of the same loop and
perform the arrangement live, recording scene fires into the timeline.

## Short Form (30–60s, Reels/TikTok)
Front-load the hook. `Hook (4-8 bars) / Verse (8 bars) / Hook`. The catchy
part must land in the first 3 seconds — no long intro.

## Bars → seconds
`seconds = bars × 4 × 60 / BPM`. At 120 BPM: 8 bars = 16s, 16 = 32s, 32 =
64s. Read project tempo from the transport before you count seconds.

## Working method
1. Lay out empty scenes named for each section.
2. Build the **Chorus/Drop first** — it's the target everything serves.
3. Derive the verse by subtraction from the chorus.
4. Fire scenes top-to-bottom to hear the contour; fix any section that
   doesn't change energy relative to its neighbors.
5. Record the scene sequence into the arrangement timeline to commit.
