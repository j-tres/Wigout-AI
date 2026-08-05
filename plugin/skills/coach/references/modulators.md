<!-- provenance: original distillation by Wigout Studio authors, 2026-07. No Bitwig-manual text. -->
# Modulators — the thing that makes Bitwig feel alive

The single most important idea in Bitwig: **any modulator can drive
almost any parameter, on any device, by any amount.** Modulators live in
a strip at the bottom of each device. You add one, click its assignment
button, click the target parameter (a device knob, an effect control,
even another modulator's setting), and drag out a modulation range. The
range is bipolar or unipolar and can be negative, so one source can open
one knob while closing another. A single parameter can be moved by
several modulators at once, and modulators can modulate each other. This
is how a static patch becomes a performance.

The workflow is always the same three steps: **add the modulator →
assign → set the range.** Learn that muscle memory and every device in
the program opens up.

Here are six recipes worth internalizing. Names below are exact Bitwig
modulator names.

**1. LFO → filter cutoff wobble.** Add an **LFO**, assign it to a
**Filter+** (or Polymer filter) **Frequency**. Sync the LFO rate to the
tempo (e.g. 1/8 or 1/4), pick a shape, and set a wide negative-to-neutral
range so the filter breathes with the beat. Increase LFO rate for classic
dubstep-style wobble; slow it right down for a long evolving sweep.

**2. Envelope Follower → sidechain-style pump.** Add an **Envelope
Follower** and point its detection at the kick (via its sidechain/audio
input), then assign it *negatively* to a pad or bass track's level or a
gain utility. Now the pad ducks every time the kick hits — the pumping
glue of dance music, without a separate compressor. Tune the follower's
attack/release so the duck recovers musically between kicks.

**3. Random → per-note variation.** Add **Random** and assign it to
something small — sample start, filter cutoff, a touch of pitch, or pan.
Set it to generate a fresh value on each note. Suddenly a repeated
hi-hat or pluck stops sounding machine-cloned; every hit is slightly
different. Keep the range subtle; humanization, not chaos.

**4. Macro-4 → a performance page.** Add **Macro-4** to get four
assignable macro knobs. Map one macro to several parameters at once — say
"Intensity" opens the filter, raises reverb send, and adds saturation
drive together. Now one knob (automatable, MIDI-mappable) becomes a
musical build control. This is how you make a patch playable instead of a
pile of forty parameters.

**5. Steps → melodic/rhythmic sequencer on pitch.** Add **Steps**, a
built-in step sequencer modulator, and assign it to an oscillator's pitch
(or a filter). Draw a pattern of step values; sync its rate to the tempo.
Held one note, the instrument now plays a moving sequence — arpeggios,
acid lines, rhythmic filter patterns — that follow whatever note you
hold. Change the number of steps for odd-length phrases.

**6. Expressions → velocity/pressure to timbre.** Add **Expressions**,
which exposes per-note performance data (velocity, pressure/aftertouch,
timbre, etc.) as modulation sources. Assign velocity to filter cutoff so
harder hits open up brighter, and pressure to vibrato depth or level for
expressive swells. This is what turns a MIDI part from typed-in to
played-in, and it shines with MPE controllers.

## Teaching notes

- Modulation amount is the whole game. A tiny range often reads as
  "alive"; a huge range reads as "broken". Start small.
- Modulators are per-device instances, so the same LFO shape on two
  tracks are independent — copy the device to reuse a setting.
- When you can't tell whether something is modulated, look for the
  colored ring around a knob; that ring shows a modulator's active range.
- The composer role can add and tweak modulators via exposed parameters,
  but the richest routings are drawn by hand in the UI — a great thing to
  have the user do themselves while you narrate the why.
