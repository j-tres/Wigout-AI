> Provenance: original distillation, engineer-v2 cycle 2026-07-10; device names cross-checked against the fact-checked sound-design/coach KBs.

# Loudness targets — LUFS, true peak, and what to actually aim for

Loudness is a *perceptual* measurement, not the same thing as a peak meter. A
peak meter tells you how close you are to clipping; it says nothing about how
loud the track feels. Streaming platforms normalize everything to a reference
loudness on playback, which is why chasing peak level is pointless and why we
measure loudness properly instead of eyeballing a meter.

## LUFS — what it measures

**LUFS** (Loudness Units relative to Full Scale) is a loudness scale weighted to
match how humans hear. Two flavors matter operationally:

- **Momentary / short-term** LUFS is a moving window — how loud the track is
  *right now*, moment to moment. Useful while automating, but a moving target.
- **Integrated** LUFS is a single number for the whole track — the average
  perceived loudness across the file, with quiet passages gated out. This is the
  one platforms normalize to, and the one we report.

`mix_report.py` measures **integrated only**: read it from
`loudness.lufs_integrated`. We do not report momentary/short-term.

## True peak vs sample peak

A **sample peak** is the highest sample value in the file
(`loudness.sample_peak_dbfs`). But when a DAC reconstructs the analog waveform
between samples, the real signal can overshoot the highest sample — an
*inter-sample* (true) peak. A file that reads -0.1 dBFS on sample peak can
easily hit +0.3 dBTP true peak on playback and clip a converter or a lossy
encoder. That is why the true peak, not the sample peak, is the number a ceiling
targets. `mix_report.py` reports it as `loudness.true_peak_est_dbtp`.

## Crest factor — a dynamics proxy

**Crest factor** is peak minus average level (`loudness.crest_db` = sample-peak
dB minus RMS dB). A high crest factor means big transients over a quieter body
(punchy, dynamic); a low crest factor means the peaks and the average have been
squashed together (loud, flat, often over-limited). It is a rough proxy for how
much dynamic life is left — watch it drop as you push loudness, and stop before
the track goes lifeless.

## Targets

| Target | Integrated | True-peak ceiling | Use it for |
|---|---|---|---|
| `streaming` | **-14** LUFS-I | -1 dBTP | Spotify/YouTube-style normalized playback |
| `club` | ~-7 LUFS-I | -0.5 dBTP | loud, competitive club/DJ masters |
| `none` | — (judge on dynamics) | — | early mixes; judge by crest/feel, not a number |

Pass the target with `mix_report.py --audio <wav> --target streaming` (or
`club` / `none`). The **-14** LUFS-I / -1 dBTP streaming row is a reference
point, not a hard rule — different services normalize to slightly different
values, but -14 is the common Spotify-normalized anchor.

**These are normalization conventions, not quality goals.** On a normalized
platform, a master louder than the target is simply turned *down* to the same
reference on playback — so pushing past -14 buys you nothing but a squashed,
lower-crest track that sounds worse next to a well-made one at the same
perceived level. Aim for the target, then stop; spend the effort on balance and
dynamics, not on the loudness war. Use `none` while mixing and judge on
`crest_db` and your ears until the mix is actually finished.

## Honest limits

- Our true peak is a **4x-oversampling estimate**, not a BS.1770 filter-exact
  true-peak meter — treat `true_peak_est_dbtp` as a conservative approximation
  and keep a little extra ceiling margin. For a stricter measurement, a true-peak
  limiter or an ffmpeg `ebur128` pass on the final render is the arbiter.
- We report **no LRA** (loudness range) — that needs ffmpeg's `ebur128`, which
  is often absent. Use `crest_db` as a coarse stand-in for dynamic range.
- The `target` verdicts (`lufs_delta`, `true_peak_margin_db`, `notes`) are
  **advisories**, not arbitration. The numbers inform the decision; the user's
  ears make it.
- An error from mix_report.py can mean the file is silent OR merely below the
  loudness gate — check the message before concluding the render failed.
