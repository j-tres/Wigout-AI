<!-- provenance: original, per spec 2026-07-09-wigout-studio-plugin-design.md §Project-audio access. -->
# Project-Audio Access (resolution ladder)

Audio is retrieved FROM THE BITWIG PROJECT, not requested as loose paths.
The Controller API exposes no clip→file mapping and no bounce/export API,
so resolution is filesystem convention:

1. **Identity**: read the selected clip's track, slot, and clip name via
   the bridge, plus `application.projectName`, in one batch.
2. **Project folder**: resolve `locations.projects` from
   `~/.wigout-ai/config.json` (written by `/studio setup`; read the file
   directly, or reuse `wizard.py scan`'s `config.locations.projects`
   field if you already ran it this session):
   `<projects>/<ProjectName>/` (fuzzy-match folder name if exact miss).
3. **File match**: search the project folder for audio files
   (wav/flac/aiff/mp3/ogg) whose stem matches the clip name
   (case-insensitive, ignoring trailing take numbers). Live-verified
   subfolder layout (`<root>/<Project>/`): **`samples/`** holds collected
   and recorded audio; **`bounce/`** holds Bounce-In-Place and bounce
   output (named `<Project>-bounce-N.wav`); also `multi-samples/`,
   `wavetables/`, `auto-backups/`. Search `samples/` and `bounce/` first.
   If several match, prefer most-recently-modified.
4. **Miss → ask honestly**: tell the user the clip could not be resolved
   and why, and ask for the file. Give the guidance ONCE per session:
   "recording directly into Bitwig, or importing with collect-on-import,
   makes clips resolvable automatically."

Known weak spots (state them, don't hide them; both live-verified):
renamed clips break the match; a file inserted or dragged in from outside
the project is NOT copied into the project folder — the clip references
the original external path this ladder cannot see, **until** the user runs
`File → Collect and Save…`, which copies referenced externals into
`samples/` by stem and makes them resolvable. Escalate to step 4 rather
than guessing.

Engineer audio-sourcing escalation (cheapest sufficient step):
(1) project state alone → (2) existing audio via this ladder →
(3) render ONLY when the audio doesn't exist yet (MIDI tracks, or the
summed mix) — via the render-handoff ladder below.

## Render-handoff ladder (engineer v2)

Getting NEW audio out of Bitwig, in preference order. Rung statuses were
resolved by live Gate R (2026-07-11, findings #31-33).

1. **Dialog-free post-FX action — REFUTED (Gate R, finding #31).** No
   post-FX bounce/export action exists. The only dialog-free action is
   `bounce_in_place` = "Bounce In Place (**Pre-FX**)", which CANNOT
   capture mix moves or the summed master. 13 plausible post-FX IDs were
   probed null-safe (`bounce_in_place_post`, `bounce_post_fx`,
   `render_in_place`, `consolidate`, `export_audio`, …); every one
   returned a null action (`target is null`). There is no headless
   post-FX render — do not keep guessing IDs.
2. **Record-print routing — PARTIAL (Gate R, finding #32).** `arm`,
   `monitor`, and `monitorMode` on a track are settable over the bridge,
   but `sourceSelector` exposes only `hasAudioInputSelected` /
   `hasNoteInputSelected` (both `readable:false`) — no settable input
   CHOOSER, so the bridge cannot point one track's input at another
   track/the master. Record-print is therefore NOT fully API-driven:
   degrade to a persistent user-created "Print" audio track whose input
   is wired to the master once by hand; thereafter arm + record + stop is
   dialog-free over the bridge.
3. **Dialog-assisted Export Audio — CONFIRMED (Gate R, finding #33).**
   `bw_call application.getAction('Export Audio').invoke()` opens the
   export dialog (returns `verified:false` as expected). Tell the user
   exactly what to click (one interaction per render); the file lands
   where they point the dialog and is found by recency. Verified live:
   an export to `bounce/` was picked up and measured by `mix_report.py`.
   This is the working render path for mix/master verification.

Pickup convention (all rungs): list candidate output dirs BEFORE the
render (`bounce/`, `samples/`, and the dialog's chosen export folder);
after the render, poll for a NEW file whose size has stopped growing and
return its path. If no new file appears within the poll budget, report
the failure — never analyze a stale file. Note the export filename form
`<Project> <YYYY-MM-DD> <HHMM>.wav` (Gate R), distinct from Bounce In
Place's `<Project>-bounce-N.wav`.

Per-clip pre-FX `bounce_in_place` remains useful for SOURCE-level
analysis (sound-design verify loop) — state plainly that it cannot
verify mix moves. The `Bounce...`/`Export Audio...`/`Collect and
Save...` actions open modal dialogs — never invoke headlessly without
telling the user first.
