<!-- provenance: original distillation by Wigout Studio authors, 2026-07. No Bitwig-manual text. -->
# Mixing in Bitwig — routing, sends, and where the meters live

Mixing well in Bitwig is mostly about understanding how signal flows from
a track to the master, and using the right structure so you fix problems
in one place instead of ten.

## The routing model

Signal runs: **track → (group) → master.** Each track carries a **device
chain** — instruments and effects in series — followed by that track's
fader and pan. Group tracks (track groups) sum their child tracks so you
can process a whole section — all drums, all vocals — with one set of
effects on the group. The **master** track is the final summing bus and
the last place processing happens before output. Teach users to build
groups early: mixing eight drum tracks is easier when one Drum group
carries the shared compression, saturation, and level.

## Device chains as mixer inserts

Bitwig doesn't separate "insert slots" from "the synth" the way older
DAWs do — the track's device chain *is* the insert chain. Effects sit
inline after the instrument, processing in order down the chain (see the
ordering guidance in devices-fx.md). For parallel processing, Bitwig
provides container devices (FX layers) that split the signal into
parallel branches and mix them back — the native way to do parallel
compression or blend a distorted copy under a clean one.

## Sends, pre- and post-fader

Sends route a copy of a track to an **effect/FX track** so several
sources can share one reverb or delay. The key teachable detail is
**pre- vs post-fader**: a **post-fader** send follows the channel fader
(pull the track down and its reverb drops too — the usual, natural
choice), while a **pre-fader** send ignores the fader (useful for
fully-wet effect returns or headphone/cue mixes independent of the main
level). Run the effect on the FX track at 100% wet and control the amount
with the send.

## Where metering lives

Every track and the master show level/peak meters in the mixer view, so
gain-staging is visible at a glance — aim to keep individual tracks and
the master peaking with headroom (not pinned near 0 dBFS) so the mix bus
and any limiter have room to work. For deeper visual inspection, drop an
analyzer device (oscilloscope/spectrum) on the track or master to *see*
the waveform or frequency balance while you work.

## Project-level loudness workflow

Peak meters tell you about headroom and clipping risk, but **not**
perceived loudness — those are different measurements. For the integrated
loudness that streaming platforms care about, don't eyeball the peak
meter; measure it. The **engineer** role runs an EBU R128 analysis
(ffmpeg `ebur128`) on a bounce and interprets it against the streaming
default of **-14 LUFS-I with true peak ≤ -1 dBTP** (the Spotify-style
target). Teach the order of operations: get the mix balanced and clean
first, keep true-peak headroom on the master (a Peak Limiter with the
ceiling around -1 dB), then check integrated loudness against -14 LUFS-I
and adjust — chasing a loudness number before the balance is right just
bakes in problems. Loudness is a mastering-stage measurement, not a
mixing-stage knob.
