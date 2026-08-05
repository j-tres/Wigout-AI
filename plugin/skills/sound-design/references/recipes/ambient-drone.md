> Provenance: Original distillation (Wigout Studio).

# Ambient Drone

**Use:** a slow-evolving sustained texture with no obvious attack — the bed under ambient, film, and soundscape work. Held tones that shift and breathe over long spans, often built from one or two notes.

**Start from:** [Polymer](../devices/polymer.md) (a single evolving source) or [The Grid](../devices/grid.md) (Poly Grid) when you want a bespoke, deeply-modulated voice — see the guides. Note the Grid's [hard boundary](../devices/grid.md): the tooling can only set macros the patch already exposes, not build the patch; teach the user to wire it in the UI if a custom drone is needed.

**Build:**
1. A sustained source — a saw/wavetable with unison detune, or a moving wavetable position. The point is a tone that never quite holds still.
2. Filter to low-pass, on the darker side; the drone should feel distant and moody rather than forward.
3. Amp envelope: very slow attack, full sustain, very long release — there should be no discernible transient. It fades in, holds, fades out.
4. Layer **multiple slow modulators** on different targets (filter, detune, wavetable position, an oscillator's level) at different slow rates so the texture never repeats obviously (see [../../../coach/references/modulators.md](../../../coach/references/modulators.md)). This is what separates a drone from a held pad.
5. Chain: EQ+ (carve space; high-pass rumble, gentle shaping) → Reverb (very long, large tail — the defining space) → Delay+ (long, dark, feedback-heavy synced repeats for depth). Time effects last; consider a send so several layers share one huge space. See [../fx-character.md](../fx-character.md).

**Parameter targets:** Filter low-pass ~25-45% (dark). Amp attack very slow, sustain full, release very long. Modulators slow, subtle, several of them. Reverb tail very long, mix generous. Delay+ long time, high-ish feedback, repeats darkened.

**Variations:** darker/deeper (lower the filter, longer reverb — toward [dark](../descriptor-dictionary.md#dark)); brighter/airier (open the filter, high-shelf air, brighter reverb — see [airy](../descriptor-dictionary.md#airy)); more motion (add a shimmer/pitch-shifted reverb feel via an octave-up delayed layer).

**Descriptors it hits:** [lush](../descriptor-dictionary.md#lush), [dark](../descriptor-dictionary.md#dark), [airy](../descriptor-dictionary.md#airy) (see [descriptor-dictionary](../descriptor-dictionary.md)).
