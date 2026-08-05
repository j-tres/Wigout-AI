<!-- provenance: original, per spec §Decision-log convention. -->
# Decision Log Convention

File: `.studio/decision-log.md` under the current working directory.
Create the directory and file on first write; ensure `.studio/` is
gitignored (append to `.gitignore` if inside a repo and not ignored).

Entry format (markdown list, newest last):

- `2026-07-09 14:32` **composer** — Inserted Polymer on new track
  "Violin" and wrote 14 notes (A minor, 4 bars).
  *Why:* transcribed hum resolved to A-C-E arpeggio; Polymer's bowed-string
  preset family is the closest stock timbre to the requested violin.
  *Look at:* track "Violin", clip slot 1 — open the clip to see the notes.

Three fields, always: what (with the verified outcome), why (the
reasoning a student could learn from), where to look in the Bitwig UI.
One entry per meaningful action group, not per bridge call.
