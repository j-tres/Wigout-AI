<!-- provenance: original distillation by Wigout Studio authors, 2026-07. No Bitwig-manual text. -->
# Stock Effects — what, when, and one classic move each

Bitwig's stock processors are studio-grade. You rarely need anything
else. For each: what it is, when to reach for it, the parameters that
matter, and a move worth stealing.

**EQ+** is the flagship parametric equalizer: multiple bands, each with a
selectable type (bell, high/low shelf, high/low pass), plus
**Frequency**, **Gain**, and **Q**, over a live spectrum display. Reach
for it first on every tonal problem — it's cheaper and more surgical than
any other fix. *Classic move:* high-pass everything that isn't a
kick/bass/pad up to 80–120 Hz to clear the low end, then find the one
honky bell frequency (sweep a narrow boosted band until it hurts, then
cut it a few dB).

**Compressor+** is the full-featured dynamics processor: **Threshold**,
**Ratio**, **Attack**, **Release**, makeup gain, and a sidechain input
with its own filtering. Reach for it to control dynamics or add
groove/glue. *Classic move:* slow attack (let the transient through) plus
medium release on a snare to make it punch, watching for a few dB of gain
reduction on hits — not constant crushing.

**Peak Limiter** is a brickwall on the output: set a **Ceiling** (about
-1 dB to leave true-peak headroom), drive input gain into it, and tune
**Release**. Reach for it on the master or a bus to catch stray peaks and
raise perceived level. *Classic move:* on a busy drum bus, push a couple
dB into it with a fast release to glue transients — but check
loudness with the engineer's meter (see mixing-in-bitwig.md); a limiter
is not a loudness meter.

**Delay+** is a flexible delay with tempo-synced or free time, feedback,
tone filtering, and modulation. Reach for it for rhythmic echoes, slap,
and width. *Classic move:* a sync'd dotted-eighth delay with the feedback
filtered darker each repeat, sitting behind a vocal or lead so it fills
space without smearing the dry signal.

**Reverb** is the algorithmic space: **Pre-delay**, decay/size, high- and
low-frequency damping, and a dry/wet mix. Reach for it to place sounds in
a room. *Classic move:* keep pre-delay long enough that the dry attack
lands first, then high-cut the reverb return so the tail is dark and sits
behind the source instead of washing over it. On sends, run it 100% wet.

**Chorus+** thickens and widens via modulated short delays: **Rate**,
**Depth**, voices, and mix. Reach for it to fatten a thin synth or add
subtle stereo movement to keys. *Classic move:* low depth, slow rate, low
mix on a mono pad to gently widen it — big settings sound seasick.

**Saturator** adds harmonics through waveshaping: a **Drive** control and
a selectable saturation curve, with output compensation. Reach for it for
warmth, grit, and loudness-without-level. *Classic move:* light Saturator
drive across the whole drum bus adds harmonic density and perceived
punch — a touch of dirt that makes drums feel bigger without raising the
peak.

**Filter+** is a multimode filter: **Frequency**, **Resonance**, a
type selector (low/high/band/notch), and drive. Reach for it for tone
shaping, risers, and rhythmic movement. *Classic move:* a resonant
low-pass with the **Frequency** modulated by an LFO for a wobble, or
automated open across a build for a filter sweep into the drop (see
modulators.md).

## Ordering guidance

Insert order matters — effects process in series down the chain.
General starting point: corrective EQ → compression → saturation/tone →
creative EQ → time-based (delay/reverb). Put reverb and delay on send/FX
tracks, not inline, when several sources should share one space (see
mixing-in-bitwig.md). None of this is law; trust your ears and move a
device up or down the chain when it sounds better.
