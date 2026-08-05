> Provenance: Original distillation (Wigout Studio) — parameters verified against Bitwig v1 FM-4.

# FM-4

**Synthesis:** compact four-operator **FM** (frequency modulation). Operators
are sine sources; one operator modulating another's frequency generates
sidebands whose spacing is set by the frequency **Ratio**. Non-integer ratios
give inharmonic (metallic/glassy) spectra a filter can never produce.

**Character:** unbeatable for anything with an inharmonic attack — bells,
mallets, glockenspiel, electric pianos / Rhodes, clangs, and biting "DX" basses.
Small ratio changes transform the timbre completely, so treat ratio as a
timbre control to automate/modulate, not a tuning detail you set once.

## Key parameters (what each does sonically, useful range)

The three named controls below are corroborated in the fact-checked KB. Other
controls are named by **function** — confirm their exact displayed labels live.

- **Ratio** (per operator) — the operator's frequency relative to the played
  note. Integer ratios (1, 2, 3…) stay harmonic and musical; non-integer ratios
  (e.g. fractional/detuned) produce the clangy, bell-like inharmonic partials FM
  is prized for. **This is the primary timbre control.** *May be stepped — see
  discrete note.*
- **Algorithm** — selects the routing between the four operators: which are
  *modulators* (bending another's frequency) and which are *carriers* (heard at
  the output). Different algorithms = different families of timbre from the same
  operators. *Discrete selector — see below.*
- **Feedback** — routes an operator back into its own (or the chain's) phase,
  adding brightness and, pushed hard, noise/edge. Low = extra harmonics; high =
  gritty/aggressive.
- **Operator level / modulation depth** — how loud each operator is. For a
  *modulator*, its level sets **how much** it brightens/complicates the carrier
  (more depth = more sidebands = brighter/harsher). For a *carrier*, its level
  is output volume. Confirm the exact label live (level vs. a depth control).
- **Operator envelope (Attack / Decay / Sustain / Release)** — FM's expressive
  core: an envelope on a *modulator's* level makes the bright, inharmonic attack
  decay into a purer tone (the essence of a bell or EP). Fast attack + short
  decay on modulation depth = struck/plucked attack. Confirm live whether each
  operator has its own full envelope.

## Discrete parameters (address at exact normalized step)

A stepped parameter only takes if the write lands exactly on a step. **Do not
invent normalized step values — read the parameter's displayed value live to
enumerate the steps**, then match the readout to the option you want.

- **Algorithm** — discrete list of operator-routing options.
- **Ratio** — may snap to a discrete list of ratio values rather than sweeping
  continuously; read the live readout to tell whether it is stepped and, if so,
  what the steps are.
- Any per-operator waveform selector, if present.

## Good starting points

- **Bell / glockenspiel:** a carrier at ratio 1 modulated by an operator at a
  non-integer ratio, modulation depth moderate-high with a fast-attack /
  medium-decay / low-sustain envelope on that depth so the clang rings out and
  fades; short-ish amp release for a struck feel.
- **Electric piano / Rhodes:** modest modulation depth with a percussive
  envelope on it for the tine attack, carrier near ratio 1, gentle Feedback for
  body; medium decay, moderate sustain.
- **DX bass:** low pitch, an integer modulator ratio for a focused tone, higher
  modulation depth for bite, a little Feedback; tight amp envelope. Pick the
  algorithm whose readout gives a single strong carrier.
