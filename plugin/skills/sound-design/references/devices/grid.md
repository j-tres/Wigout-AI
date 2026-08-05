> Provenance: Original distillation (Wigout Studio) — behavior verified against Bitwig v1 Poly Grid / FX Grid / Note Grid.

# The Grid (Poly Grid / FX Grid / Note Grid)

**Synthesis:** modular. The Grid is a patching environment, not a fixed-
architecture synth — you build the voice from oscillator, filter, envelope, LFO,
math, and I/O modules (Poly Grid = instrument, FX Grid = audio effect, Note Grid
= note effect). See `../../../coach/references/grid-concepts.md` for the mental
model.

## Why there is no fixed parameter list here

Unlike Polymer / Phase-4 / FM-4 / Polysynth / Sampler, **a Grid device has no
canonical parameter set.** Its externally addressable parameters are
**patch-defined** — they are whatever the patch's author chose to expose (the
mapped macros / remote controls), and they differ from patch to patch. So this
file cannot list "the Grid's cutoff" the way the other guides can.

**Hard boundary (state it plainly):** the Controller API — and therefore this
plugin — **cannot create modules, draw patch cords, or edit anything inside a
Grid patch.** You can only read and set the parameters the patch already
exposes to the outside. When a bespoke Grid voice is wanted, teach the user to
wire it in the UI (coach's job); do not promise the tooling will build it.

## How to work a Grid device from the outside

1. **Enumerate live.** Read the device's exposed/remote-control parameters and
   their **displayed names** at runtime — those names come from the patch, so
   never assume a label. Map the sound-designer move (from
   `../descriptor-dictionary.md`) onto whichever exposed control matches by
   function (e.g. a macro the author named "Cutoff", "Tone", "Drive").
2. **Treat every exposed control as unknown until read.** Ranges and whether a
   control is discrete are patch-defined; read the displayed value to tell.
3. **If the needed control is not exposed,** the move is not reachable from the
   outside — say so, and have the user map a macro to it in the UI (see
   `../../../coach/references/modulators.md`, Macro-4 pattern) or build the patch
   themselves.
