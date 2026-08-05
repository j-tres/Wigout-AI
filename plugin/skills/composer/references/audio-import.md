<!-- provenance: original; recipe live-verified vs Bitwig 6.1b2 (Wigout Studio Task 16), see docs/bitwig_docs/live-api-findings.md finding 26. -->
# Getting an audio file INTO Bitwig (verified recipe)

For landing a file you have on disk (ACE-Step / claude-music output, a
rendered stem, a resolved reference clip) into the project. Uses
`InsertionPoint.insertFile(<absolute path>)`, which is live-verified and
**context-sensitive** — the same call does different things depending on
where you point it.

## As an audio clip in a launcher slot (the usual choice)

```
bw_call {
  "path": "tracks[<i>].clipLauncherSlotBank[<s>].replaceInsertionPoint",
  "method": "insertFile",
  "args": ["C:/abs/path/to/audio.wav"]
}
```
Result: an audio clip appears in that slot, its `name` = the file's stem.
Confirm (the call returns the async `verified:false` + re-read hint):
```
bw_get { "path": "tracks[<i>].clipLauncherSlotBank[<s>].hasContent" }   # -> true
bw_get { "path": "tracks[<i>].clipLauncherSlotBank[<s>].name" }         # -> file stem
```

## As a playable Sampler instrument (hum/one-shot → instrument, no transcription)

Point at an **instrument** track's device-chain insertion point:
```
bw_call {
  "path": "tracks[<i>].startOfDeviceChainInsertionPoint",
  "method": "insertFile",
  "args": ["C:/abs/path/to/sample.wav"]
}
```
Result: a **Sampler** loaded with the file. Confirm with
`list_devices_on_track` → a `Sampler` at index 0. (In an **audio-effect**
context the same call inserts a **Convolution** using the file as its IR —
usually not what you want for musical material.)

## Index safety (critical)

`create_track` / `delete_track` renumber `tracks[]` immediately — a new
track shifts every higher index. After creating the target track, **re-read
the track list and use the fresh index** before calling `insertFile`; never
reuse a pre-creation index (finding 29).

## When to prefer this vs. asking the user

`insertFile` needs an absolute path you already hold (a file the composer
just generated or resolved via the project-audio ladder). It does NOT help
you find a mystery clip's source — that is the resolution ladder's job
(`bitwig-project/references/project-audio-access.md`). If you only have a
selected clip and no path, resolve first; insert second. If resolution
fails, ask the user for the file rather than guessing a path.
