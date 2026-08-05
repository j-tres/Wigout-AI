<!-- provenance: original distillation of docs/bitwig_docs/live-api-findings.md, 2026-07. -->
# Bridge Landmines (distilled)

Full evidence: `docs/bitwig_docs/live-api-findings.md` (repo root).

1. **Focus follows project** — verify `application.projectName` around
   every read/mutate batch.
2. **Transport won't play?** Check Bitwig's MIDI-clock-sync toggle; when
   on, internal play is silently ignored.
3. **Ranged writes**: the bridge uses `setImmediately` semantics — values
   are normalized 0..1 for ranged parameters.
4. **Interest is init-only** (server-side concern, but explains why some
   paths report "not marked": the value wasn't registered at extension
   init — check `bw_describe` `readable` flag before reading).
5. **Deep access = select, then operate** — re-point `cursorDevice` /
   `cursorClip` before touching drum pads, layers, nested chains, notes.
6. **Notes**: `cursorClip.notes` reads the observer cache;
   `cursorClip.step(ch, x, y)` addresses one step for edits.
7. **Target tracks/groups by NAME, not a remembered index** (findings
   #29, #34). The `tracks[]` bank is a moving window: indices shift with
   selection, scroll, and structural edits, so a bare `tracks[9]` can be
   the master one moment and an instrument track the next. Names are
   stable — and are how the user refers to things ("the Mono Bass", "the
   Drums group"), so mirror their language. To act on a named track,
   resolve name→index by reading the track list (`list_tracks`, or
   `tracks[i].name`) in the SAME batch and use the index immediately; or
   use curated tools that accept a name (`create_track` / `rename_track` /
   `delete_track` take `track_name`). The **master is the `masterTrack`
   root**, never `tracks[N]`. After any create/delete, re-read the list —
   never reuse a pre-mutation index.
