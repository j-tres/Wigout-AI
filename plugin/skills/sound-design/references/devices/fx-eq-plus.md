> Provenance: Original distillation (Wigout Studio) — device and parameter names cross-checked against coach `../../../coach/references/devices-fx.md`; confirm exact displayed labels live.

# EQ+

**What it does:** the flagship parametric equalizer. It carves the frequency
balance of a signal with multiple bands over a live spectrum display, each band a
filter you place, boost, or cut. This is the first and most surgical tool for any
tonal problem — cheaper on CPU and more precise than reaching for a different
device. Every band you add is subtractive/additive shaping in the frequency
domain, not a new sound.

## Key parameters (what each does sonically, useful range)

The controls below are corroborated in the fact-checked KB. Each band shares the
same three continuous controls plus a band-type selector. **Confirm the exact
displayed labels live** if anything reads differently on your build.

- **Frequency** (per band) — where on the spectrum the band acts. Continuous.
  This is where you *point* the band: low for body/mud, low-mids for boxiness,
  high-mids for presence/harshness, highs for air. To find a problem frequency,
  boost a narrow band and sweep the Frequency until the offending resonance jumps
  out, then decide whether to cut it.
- **Gain** (per band) — how much you boost (up) or cut (down) at that Frequency,
  in dB. Continuous, bipolar around 0. Cuts are usually cleaner than boosts for
  fixing problems; small boosts add character. Big boosts change level as well as
  tone — compensate downstream.
- **Q** (per band) — bandwidth: how wide or narrow the affected region is.
  Continuous. High Q = narrow/surgical (notch out one ringing frequency); low Q =
  wide/musical (gentle tonal tilt). Use narrow for corrective cuts, wide for
  broad character moves.
- **Band type** (per band) — selects the shape of that band: bell, high shelf,
  low shelf, high pass, or low pass. Bells boost/cut a region; shelves lift or
  drop everything above/below a corner; the pass filters remove everything beyond
  a corner (a high-pass removes lows). *Discrete selector — see below; confirm
  the available types live.*
- **Number of bands / enabling bands** — you add as many bands as the tonal job
  needs. More bands = finer control but easier to over-process. Confirm live how
  bands are added and bypassed on your build.

The spectrum display is a reading aid: it shows you *where* energy lives so you
aim bands by eye and ear together — but trust the ear for the final call.

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if a write lands exactly on a step — a nearby value
does nothing. **Do not assume normalized step values; read the parameter's
displayed value live to enumerate the steps**, then target the step whose readout
matches the option you want.

- **Band type** — discrete list (bell / high shelf / low shelf / high pass / low
  pass, and any others your build shows). Read the live list.

## Good starting points

- **Clean up the low end:** on anything that is not kick/bass/pad, set one band
  to high-pass and raise its Frequency to roughly 80–120 Hz to clear sub-rumble
  that only muddies the mix. Confirm the audible result — voices and some
  instruments carry useful low-mid body you do not want to gut.
- **Remove a honky resonance:** add a bell band, give it a high Q and a few dB of
  boost, sweep the Frequency until the honk/boxiness is unbearable, then invert
  the Gain to a cut of a few dB and widen Q slightly.
- **Add air:** a gentle high-shelf band, low Q, a couple dB of boost up top for
  openness — small amounts; a big high boost turns to harshness fast.
- **Corrective vs creative:** do surgical cuts early in the chain and broad tonal
  "voicing" boosts later — see `../fx-character.md` for pre/post EQ placement.
