> Provenance: Original distillation (Wigout Studio).

# 808 Bass

**Use:** trap/hip-hop sub-kick — a long, tuned sine boom that doubles as bass and kick, with a short click on the attack and a distorted variant that survives on small speakers.

**Start from:** Polymer with a sine/triangle-leaning oscillator slot — see [../devices/polymer.md](../devices/polymer.md). (A sampled 808 in [Sampler](../devices/sampler.md), loop off, is the alternative when you want a specific recorded 808.)

**Build:**
1. Pick the purest source the slot offers (sine, or triangle if you want a little more edge). The 808 is essentially a tuned sine — body lives here, not in the filter.
2. Amp envelope: near-instant attack, **no** sustain plateau, and a long decay/release so the note blooms and rings out. The decay length *is* the "808 length".
3. For the sub-kick thump, add a fast downward pitch blip on the attack (a pitch envelope or Envelope modulator to oscillator pitch — see [../../../coach/references/modulators.md](../../../coach/references/modulators.md)); a few semitones falling over a very short time reads as the kick.
4. Keep the filter mostly open — low-pass is only there to tame any fizz; the sine has almost nothing to remove.
5. Chain (for the "distorted 808" that cuts on phones): Saturator (drive for harmonics an octave up so the pitch is audible on small speakers) → EQ+ (creative: gentle boost ~60-90 Hz for weight, high-pass out DC/rumble below ~30 Hz). Keep it **mono** — see [../fx-character.md](../fx-character.md).

**Parameter targets:** Filter low-pass ~70-90% (barely working). Amp attack ~0%, decay/release long (tune by ear to the tempo). Pitch-blip depth a few semitones, blip time very short. Saturator drive low-to-moderate for the clean version, pushed for the distorted version; match output to input.

**Variations:** clean (little/no saturation, filter fully open) for R&B; distorted (heavy Saturator, then low-pass the fizz) for trap that reads on phones; longer glide (add portamento/glide so pitched 808 lines slide between notes).

**Descriptors it hits:** [boomy](../descriptor-dictionary.md#boomy), [warm](../descriptor-dictionary.md#warm), [punchy](../descriptor-dictionary.md#punchy) (see [descriptor-dictionary](../descriptor-dictionary.md)).
