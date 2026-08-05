<!-- provenance: original distillation by Wigout Studio authors, 2026-07. No Bitwig-manual text. -->
# Core Workflows — how work actually gets done in Bitwig

Bitwig's defining feature is that the same project has two timelines: the
**Clip Launcher** (a grid of loopable clips per track) and the
**Arranger** (the linear timeline). Understanding when to use each is
the biggest workflow win in the program.

## Clip Launcher vs Arranger

Use the **Clip Launcher** for exploration and performance: jam loops,
try variations of a part in adjacent slots, build a song by triggering
scenes, and record a live take of your own clip launching. It is
non-linear and forgiving — nothing is committed to a fixed position.

Use the **Arranger** to commit to structure: intro/verse/chorus, precise
automation, transitions, and the final form you'll bounce. The typical
flow is to sketch in the launcher, then record or drag the good clips
onto the arranger timeline to lock the arrangement. You can play both at
once and hand control back and forth, which is powerful once it clicks —
teach it as "launcher = ideas, arranger = decisions".

## Recording audio and where files land

When you record audio (or capture the output of a track), Bitwig writes a
new audio file into the current project's recording area, which is why
recorded clips resolve cleanly to files afterward. The consumer roles
rely on this: for the exact resolution logic and the known weak spots,
see **project-audio-access.md in the bitwig-project skill** — don't
restate the ladder, point there so there is one source of truth. The
teachable headline for users: record into Bitwig (or import with
collect-on-import) and your clips stay findable; drag in a loose file
without collecting and it references an outside path the tooling can't
follow.

## Bounce and bounce-in-place

**Bounce** renders a selection to a new audio clip. **Bounce in Place**
renders a clip (or a MIDI clip through its instrument) to audio *and*
swaps it in on the same track, preserving position — the fast way to
"freeze" a CPU-heavy synth part or commit a MIDI idea to audio so you can
edit it as a waveform. Teach the tradeoff: bouncing commits the sound
(cheaper CPU, editable audio) but loses the notes, so duplicate to a
muted track first if the user might want to revise the MIDI.

## Keeping projects self-contained ("Collect and Save…")

The File menu's **Collect and Save…** copies every referenced sample
into the project folder so the project is self-contained and portable.
Encourage this: it makes projects survive being moved or archived, and —
per the audio ladder above — keeps every clip resolvable by the tooling.
Bitwig can also collect files as they are imported (a Preferences
behavior, not a menu command). The habit to teach: collect on import
when enabled, and run Collect and Save… before sharing or archiving a
project.

## Note editor operations

In the piano-roll/note editor, the moves that matter most:

- **Audition** plays a note as you click or drag it, so you hear pitch
  choices while editing rather than guessing.
- **Micro-pitch** nudges a note off the grid in cents for expressive
  detuning, slides, and pitch-bend-style gestures without automation.

Bitwig also treats note expressions (velocity, timbre, pressure, gain,
pan per note) as editable lanes — pair this with the **Expressions**
modulator (see modulators.md) to make parts feel played.

## Comping basics

When you record several takes into the same clip as layers, comping lets
you audition each take and stitch the best moments into one composite
performance by choosing which take is active across regions of the clip.
Teach it as "record a handful of passes, then build the keeper from the
best bits" — far more productive than chasing one flawless take.
