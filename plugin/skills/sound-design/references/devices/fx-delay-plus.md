> Provenance: Original distillation (Wigout Studio) — device and parameter names cross-checked against coach `../../../coach/references/devices-fx.md`; confirm exact displayed labels live.

# Delay+

**What it does:** a flexible delay — it repeats the signal after a set time, feeds
some of the output back for multiple echoes, filters those echoes so they darken
over repeats, and can modulate the delay for movement. Reach for it for rhythmic
echoes, slapback, and width. On sends it fills space behind a lead or vocal
without smearing the dry signal.

## Key parameters (what each does sonically, useful range)

The fact-checked KB describes Delay+ by function (tempo-synced or free time,
feedback, tone filtering, modulation) but does not corroborate exact control
labels, so **every parameter below is named by function — confirm each exact
displayed label live** before you rely on it, and do not treat these as proper
Bitwig names.

- **Delay time (and sync mode)** — how long after the dry signal each repeat
  lands. Can be free (in ms) or tempo-synced (note values like 1/8, dotted 1/8).
  A dotted-eighth sync is the classic rhythmic-lead delay. Whether time runs free
  or synced is typically a *discrete* mode choice — see below.
- **Feedback** — how much of the output is fed back in, i.e. how many repeats you
  hear. Low = one or two slaps; higher = a long train of echoes. Push too high and
  it can build toward runaway/self-oscillation — back off before it swamps the
  mix.
- **Tone filtering (high-cut / low-cut on the repeats)** — filters applied in the
  feedback path so each repeat gets darker (or thinner) than the last. Darkening
  the repeats keeps them behind the dry signal instead of competing with it — the
  key to a delay that fills space without smearing.
- **Modulation (rate/depth on the delay time)** — wavering the delay time adds
  chorus-like movement and stereo life to the tail. Subtle amounts widen; large
  amounts warble.
- **Left/right time offset or ping-pong** (if exposed) — different times or
  bouncing repeats across the stereo field create width. Confirm live how your
  build exposes stereo behavior.
- **Mix / dry-wet** — the balance of echoes against the dry signal. Inline, keep
  it modest; on a send/FX track run it fully wet and control the amount with the
  send (see `../../../coach/references/mixing-in-bitwig.md`).

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if a write lands exactly on a step. **Do not invent
normalized step values; read the parameter's displayed value live to enumerate the
steps**, then match the readout to the option you want.

- **Sync / free time mode** — discrete choice between tempo-synced note values and
  free time.
- **Synced note value** — if the delay time is chosen from a list of note
  divisions, the divisions are discrete steps; read the live readout.
- **Ping-pong / stereo mode** — if present as a stepped selector.

## Good starting points

- **Rhythmic lead/vocal delay:** tempo-synced dotted-eighth time, moderate
  feedback, high-cut the repeats so each is darker, on a send at full wet with the
  amount dialed by the send level so it fills space behind the dry.
- **Slapback:** short free time (roughly 80–140 ms), very low feedback (one
  distinct slap), little to no tone filtering — thickens vocals/guitars without an
  obvious echo.
- **Wide dub throws:** higher feedback, tone filtering that darkens fast, a touch
  of modulation and a stereo/ping-pong offset for a spreading tail; automate the
  send up for a throw on the last word of a phrase.
