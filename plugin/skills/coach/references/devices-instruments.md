<!-- provenance: original distillation by Wigout Studio authors, 2026-07. No Bitwig-manual text. -->
# Stock Instruments — what to reach for

Bitwig ships enough synthesis to cover most productions without a single
third-party plug-in. Pick by the sound you hear in your head, not by
brand loyalty. Here is how the workhorses actually behave.

**Polymer** is the modern all-rounder: a subtractive-hybrid synth with a
swappable oscillator slot, a filter, and note-triggered envelopes. Its
strength is speed — you get a usable pad, bass, or lead in three moves.
Key parameters: the oscillator character/type control, the filter
**Frequency** and **Resonance**, and the amplitude envelope (attack and
release do most of the emotional work). Reach for Polymer when you want
"a synth" and don't want to think about architecture — it's the default
for pads, synth strings, plucks, and clean basses.

**Phase-4** is a phase-modulation synth built around several oscillators
that can modulate each other's phase. It excels at glassy, digital, and
slightly unstable tones — DX-adjacent without being a strict FM
operator maze. Work the per-oscillator pitch and waveform, the
phase-modulation amount between oscillators, and oscillator sync. Reach
for it when a sound needs motion and edge: evolving leads, metallic
plucks, cold digital pads.

**FM-4** is a compact four-operator FM synth: operators with tunable
frequency **Ratio**, selectable **Algorithm** routing, and **Feedback**.
FM is unbeatable for anything with an inharmonic attack — bells,
mallets, electric pianos, clangs, and biting basses. Small ratio changes
transform the timbre completely, so automate or modulate them. Reach for
FM-4 whenever "bell", "Rhodes", or "DX bass" is the brief.

**Sampler** plays back an audio file across the keyboard with pitch
tracking, loop modes, a filter, and an amplitude envelope. It is your
route to realistic acoustic timbres, since a good multisample beats any
synthesis of a real instrument. Key controls: sample **Start**/**Loop**
behavior, keytracking/pitch, the filter, and the amp envelope. Reach for
Sampler for strings, brass, pianos, vocal chops, and any "real
instrument" request.

**Drum Machine** is a container: sixteen pads per bank (addressing the
full 128-note range across banks), each pad a full nested device chain
triggered by its own note. That means every pad can hold its
own synth or sampler plus effects, and pads can share choke groups. Reach
for it to build a kit from one-shots or to layer a synth drum per pad.

**Organ** is a drawbar tonewheel emulation: drawbar levels set the
harmonic mix, plus percussion and vibrato/chorus voicing. Reach for it
for gospel/soul keys, sustained organ pads, and anything that wants that
additive drawbar body.

**Poly Grid** is The Grid running as a polyphonic instrument — a modular
patching environment where you build the synth from oscillator, filter,
envelope, and math modules. Reach for it when the stock synths can't get
there and you want to design the voice yourself (see grid-concepts.md).

## Timbre → device quick picks

Use this when a request names an instrument or a vibe. First choice
listed first; the fallback gets you close without a sample library.

| You want… | First choice | Fallback / synth route |
|---|---|---|
| Violin / bowed strings | Sampler + string multisample | Polymer: sustained saw, slow filter attack, gentle vibrato via LFO |
| String section / pad | Sampler (ensemble sample) | Polymer or Poly Grid detuned-saw pad |
| Piano / keys | Sampler (piano multisample) | Polymer clean tone |
| Electric piano / Rhodes | FM-4 | Sampler EP multisample |
| Organ | Organ | Sampler organ sample |
| Bells / mallets / glockenspiel | FM-4 | Phase-4 |
| Sub / analog bass | Polymer | Phase-4 |
| Reese / gnarly bass | Phase-4 | Poly Grid |
| Pluck | Polymer | Phase-4 |
| Lead (bright/aggressive) | Polymer or FM-4 | Phase-4 |
| Bread-and-butter synth lead / pad | Polysynth — classic two-oscillator subtractive; fast results when Polymer feels too modern | Polymer |
| Evolving digital texture | Phase-4 | Poly Grid |
| Drum kit | Drum Machine | Sampler per track |

When the user asks for "violin" and no sample library is loaded, be
honest: a synth approximation is a pad that reads as strings, not a
convincing solo violin. Say so, offer the Sampler route if they have a
string multisample, and let them choose. Never oversell synthesis as
realism.
