> Provenance: Original distillation (Wigout Studio) — chain-order and character guidance cross-checked against coach `../../coach/references/devices-fx.md` and `../../coach/references/mixing-in-bitwig.md`. No Bitwig-manual text.

# FX character & chaining — order the chain, then build a feeling

Effects in Bitwig process in series down the device chain: each device hears the
output of the one before it, so **the order changes the result**. This file is
about two things — how to *order* a chain, and how to combine devices to build a
named character the user asked for ("more space", "glue the drums", "give it
grit", "make it wider"). None of the ordering below is law; it is a strong
starting point you move when your ears say so.

Device specifics live in the per-device guides under `devices/`
(`devices/fx-eq-plus.md`, `devices/fx-filter.md`,
`devices/fx-saturator-distortion.md`, `devices/fx-compressor.md`,
`devices/fx-chorus-plus.md`, `devices/fx-delay-plus.md`, `devices/fx-reverb.md`).

## Chain order — the default and why

A reliable default insert order, from the coach KB:

**corrective EQ → compression → saturation / tone → creative EQ → time-based (delay / reverb).**

Read that as a set of principles, not a fixed recipe:

- **EQ, pre vs post.** Do *subtractive/corrective* eq first — high-pass the rumble,
  notch a resonance — so everything downstream works on a clean signal (a
  compressor should not be triggered by sub-rumble you were going to remove
  anyway). Do *creative/voicing* eq later in the chain, after saturation and
  dynamics have changed the tone, to shape the final color. Same device (EQ+), two
  jobs, two positions.
- **Saturation, before vs after the filter.** Saturation *adds* harmonics; a
  filter *removes* them. Put **saturation before the filter** when you want the
  filter to tame the new harmonics (grit that stays controlled, the classic
  drive-into-a-low-pass sound). Put **saturation after the filter** when you want
  those harmonics to survive on top, bright and present. Reach for Filter+ and the
  Saturator and try both — it is one of the biggest character decisions in the
  chain.
- **Dynamics placement.** Compression usually sits *after* corrective EQ (so it
  reacts to the sound you actually want, not to rumble) and *before* the
  time-based effects (compress the source, then send the controlled signal to
  reverb/delay — reverberating an uncompressed signal and then compressing the
  wash pumps unpredictably). Whether you compress before or after saturation is a
  taste call: compress-then-saturate evens the level going into the shaper;
  saturate-then-compress tames the grit.
- **Time effects last.** Delay and reverb go at the *end* of the chain (or, better,
  on a send/FX track) so they add space to the finished tone rather than having
  their tails re-processed. Put reverb and delay on send/FX tracks when several
  sources should share one space — see
  `../../coach/references/mixing-in-bitwig.md`.

When in doubt, move the device up or down the chain and listen; ordering is a
tool, not a rule.

## Building a character

### "space" — depth and a sense of place

Space comes from **reverb** and **delay**, and the craft is adding it *without*
washing out the mix. The levers:

- **Pre-delay** (Reverb): lengthen it so the dry attack lands first and the tail
  blooms after — the source stays defined instead of smearing into the room.
- **Tail darkness**: high-cut/damp the reverb tail and darken delay repeats (tone
  filtering in the feedback path) so the space sits *behind* the source rather than
  competing with it.
- **Depth via delay**: a tempo-synced delay with darkening repeats fills the gaps
  behind a lead/vocal and implies distance.
- **Share one space**: run reverb/delay on a send at 100% wet and control the
  amount per source — a single room glues elements together (see
  `../../coach/references/mixing-in-bitwig.md`). Details in `devices/fx-reverb.md`
  and `devices/fx-delay-plus.md`.

### "glue" — many sources moving as one

Glue is a **compression** feel on a group/bus: gentle, shared gain movement so a
section breathes together instead of as separate parts.

- Put a **Compressor+** on the group track (build groups early), low ratio (~2:1),
  threshold set for only **1–3 dB** of gain reduction, medium attack and release so
  the whole bus moves together without obvious pumping.
- A touch of **saturation** on the same bus adds harmonic density that reads as
  cohesion and perceived punch — small amounts, output matched so you judge tone
  not level.
- The master/limiter *loudness* stage is a different job from bus glue — keep them
  separate and measure loudness rather than eyeballing it (see the loudness
  workflow in `../../coach/references/mixing-in-bitwig.md`). See
  `devices/fx-compressor.md`.

### "grit" — harmonic dirt and edge

Grit is added harmonics — **saturation** and, harder, distortion.

- Start with the **Saturator**: raise Drive for harmonic density, pick the curve
  that matches the feel (softer = warm, harder = aggressive), and match output to
  input for a fair A/B.
- For a defined attack with a dirty body, blend **parallel**: a heavily driven copy
  under the clean signal (a mix control or an FX-layer branch) so the transient
  stays clean and the sustain gets grit.
- For breakup harder than the Saturator gives, browse Bitwig's harder
  distortion-family / bit-reduction devices and **confirm the exact device name
  live** — do not assume a remembered proper noun.
- Control it with a filter: pair grit with Filter+ or an EQ+ high-cut to keep the
  harshness in check (saturation before/after the filter, per the chain-order note
  above). See `devices/fx-saturator-distortion.md`.

### "width" — a bigger stereo image (with the mono-bass caveat)

Width comes from **Chorus+** and other stereo/modulated-delay effects.

- **Chorus+**: low Depth, slow Rate, modest mix gently lifts a mono source off the
  center and spreads it. Big settings sound seasick.
- **Stereo delay / ping-pong**: different left/right delay times or bouncing
  repeats spread the tail.
- **Mono-bass caveat — this matters.** Widening effects work by decorrelating the
  left and right channels, which weakens mono/phase compatibility, and the damage
  is worst in the low end (a wide sub can partially cancel when the mix is summed
  to mono, and disappears on club/phone systems). **Keep bass and sub mono** —
  apply width to the mids and highs only, and leave the low register centered. See
  `devices/fx-chorus-plus.md`.

Combine characters freely — e.g. glue a drum bus (compression) + a little grit
(saturation) + a short shared room (reverb) is a complete "make the drums feel
big and cohesive" move. Order per the chain-order section, then trust your ears.
