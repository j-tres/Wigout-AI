> Provenance: Original distillation (Wigout Studio) — device and parameter names cross-checked against coach `../../../coach/references/devices-fx.md`; confirm exact displayed labels live.

# Reverb

**What it does:** the algorithmic space. It simulates the wash of reflections a
room, hall, or plate adds around a sound, placing a dry source into an environment
so it feels like it exists somewhere rather than in a vacuum. Reach for it to add
depth, glue elements into a shared space, and push sounds back from the listener.

## Key parameters (what each does sonically, useful range)

**Pre-delay** is corroborated in the fact-checked KB; decay/size, damping, and
mix are described by function. **Confirm the exact displayed labels live.**

- **Pre-delay** — the gap between the dry sound and the onset of the reverb tail.
  Continuous. Short = the reverb hugs the source (can smear the attack); longer =
  the dry attack lands cleanly first and the tail blooms after, which keeps
  transients defined and separates source from space. A key clarity control.
- **Decay / size** — how long the tail lasts and how big the modeled space is.
  Continuous. Short = a small tight room/ambience; long = a big hall/washy tail.
  Bigger/longer fills more space but muddies faster — match it to the tempo and
  arrangement density. Confirm live whether decay and size are one control or two.
- **High-frequency damping** — how quickly the highs die away in the tail.
  Continuous. More damping = a darker, more natural tail that sits behind the
  source; less = a bright, present tail. Darkening the tail is the main trick for
  a reverb that adds depth without washing over the mix.
- **Low-frequency damping / low-cut** — controls how much low energy the tail
  carries. Continuous. Trimming lows out of the reverb keeps the space from
  muddying the low end (bass and kick stay tight while everything else gets room).
- **Mix / dry-wet** — the balance of reverb against the dry signal. Continuous.
  Inline, keep it modest; on a send/FX track run the Reverb fully wet and set the
  amount with the send so several sources share one space (see
  `../../../coach/references/mixing-in-bitwig.md`).
- **Room / algorithm type** (if exposed) — chooses the character of the space
  (room / hall / plate-style). *If it is a stepped selector, treat it as discrete
  — see below.* Confirm the available types live.

## Discrete parameters (address at exact normalized step)

A stepped selector only takes if a write lands exactly on a step. **Do not invent
normalized step values; read the parameter's displayed value live to enumerate the
steps**, then match the readout to the option you want.

- **Room / algorithm type** — if the space character is chosen from a list, the
  options are discrete steps; read the live readout.

## Good starting points

- **Depth without wash (the classic move):** longer Pre-delay so the dry attack
  lands first, then high-frequency damping up so the tail is dark and sits behind
  the source. On a send, run it 100% wet and dial the amount with the send.
- **Tight ambience/glue:** short decay/small size, modest mix — adds a sense of a
  shared room to drums or a group without an obvious tail.
- **Big lush hall (pad/vocal):** long decay, moderate Pre-delay, low-cut the tail
  so it does not muddy the lows, high-cut it a little so it stays behind the
  source. Keep it on a send shared across the elements that belong in that space.
- **Keep the low end clean:** always trim the reverb's lows (low-frequency
  damping / low-cut) so the space does not fight the bass — see the "space" recipe
  in `../fx-character.md`.
