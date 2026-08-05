<!-- provenance: original distillation by Wigout Studio authors, 2026-07. -->
# Arrangement and Energy

Composition writes the parts; arrangement decides *when each part plays*.
The listener hears one thing: the **energy curve** — how full and intense
the track feels from second to second. Arranging is sculpting that curve.

## The energy curve
Sketch the whole track as a line that rises and falls. A good curve has:
- A **low start** so there's somewhere to go.
- **Steady climb** with a small dip before each lift (contrast makes the
  lift bigger).
- One or two **peaks** — the final chorus/second drop is usually highest.
- **Release** at the end.

The enemy is a **flat line**: everything playing all the time. If bars 1–64
have identical density, nothing feels like an arrival. Every section must
differ in energy from its neighbors.

## Energy comes from layers, not just volume
Rank your elements by how much energy each adds (roughly):
sub bass → kick → snare/clap → hats/percussion → bass line → chords/pads →
lead/vocal → counter-melody/ear-candy → risers/fx.
Raise energy by **adding** layers up this ladder; lower it by **removing**
from the top down. Also modulate: filter openness (closed = low energy),
register (higher = brighter/more energy), rhythmic density, and reverb
size.

## Addition and subtraction
- **Verse/build**: strip to essentials — drums + bass + one melodic hook.
- **Chorus/drop**: add the widest arrangement — the full ladder.
- Between two repeats of the same section, **change one thing**: add a
  counter-melody, an octave, a percussion layer, or open a filter. A second
  chorus that's identical to the first wastes a lift.
- To make a chorus feel huge, make the section *before* it small. Contrast,
  not absolute volume, is what the ear reads as "big."

## The 8-bar rule
Popular music breathes in 8-bar phrases (4 and 16 are the other common
lengths). Introduce or remove an element on an **8-bar boundary** so changes
land where the listener expects a new phrase. Ear-candy and one-shot fills
go on the *last* bar of the phrase to lead into the boundary. When in doubt,
count in 8s.

## Transitions — glue between sections
Transitions tell the ear "something's coming":
- **Riser**: a swelling noise/synth sweep over the last 1–2 bars into a
  drop. Uptuned white noise or a pitched-up synth through a rising filter.
- **Filter sweep**: automate a low-pass opening across a build (closed →
  open), then slam full-open on the downbeat of the drop.
- **Impact / downlifter**: a reverse cymbal or boom *on* the transition
  beat marks the arrival.
- **Snare roll**: accelerating snare into the new section (see
  `rhythm-groove.md`).
- **The drop-out**: cut everything for a beat (or half a bar) right before
  the chorus — the silence makes the entrance hit harder than any riser.
- Use **one or two** of these per transition, not all five — clutter kills
  the impact.

## Mapping to the launcher (Bitwig workflow)
Arrange with scenes before committing to the timeline:
1. One **scene per section** (`Intro`, `Verse`, `Build`, `Drop`…), each clip
   holding only the layers that section needs — this *is* the arrangement.
2. Duplicate a scene to make its variation, then add/remove one clip to bump
   or drop energy.
3. **Perform** the arrangement by firing scenes top-to-bottom; listen for a
   flat spot where energy doesn't change, and fix that scene.
4. Put transition elements (risers, impacts) as clips on an **FX/riser
   track**, fired on the build scene.
5. When the scene order feels right, **record it into the arrangement
   timeline**, then draw automation (filter sweeps, volume swells) across the
   8-bar boundaries.

## Method
1. Draw the target energy curve for the whole track.
2. Assign each section a layer count from the ladder.
3. Ensure every section differs from its neighbor (add or subtract).
4. Place transitions on 8-bar boundaries into each lift.
5. Audition as scenes, fix flat spots, commit to the timeline.
