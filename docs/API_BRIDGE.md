# Generic API bridge reference

Beyond the curated tools, five meta-tools expose the entire project-level Bitwig
API v25: `bw_describe` (introspect any path), `bw_get` (read), `bw_set` (write
with verify), `bw_call` (invoke), `bw_snapshot` (bulk subtree/batch read).
Paths address the live object graph, e.g. `tracks[3].devices[1].isEnabled`.
MCP resources `bitwig://api/index` and `bitwig://api/roots` carry the full
generated API map (see `scripts/generate-api-index.ps1`); `bitwig://config/locations`
serves the per-user File Locations config.

- Deprecated API members are refused at runtime with their replacement named.
- The physical-controller subsystem (hardware/MIDI/OSC bindings) is out of scope.
- Readable values are interest-marked in bulk at extension init (Bitwig
  requires init-time `markInterested`); paths deeper than the marked surface
  return an honest error.
- Ranged writes use `setImmediately` so the user's controller take-over
  strategy cannot silently swallow them.

## Notes (read & edit)

The launcher cursor clip has a 128×128 step grid and an init-registered
observer cache. Select a clip slot first (`bw_call
tracks[0].clipLauncherSlotBank[0].select`), then:

- `bw_get {path: "cursorClip.notes"}` — all cached notes
  `{channel,x,y,state,velocity,duration,…}`.
- `bw_get {path: "cursorClip.step(0,4,64)"}` — one step (cache-first,
  `getStep` fallback; unwritten cells read `state: "Empty"`).
- `bw_call {path: "cursorClip.step(0,4,64)", method: "setVelocity", args: [0.9]}`
  — per-note expression edits (velocity, duration, pan, timbre, pressure,
  gain, transpose, chance…). Setters only affect steps where a note starts
  (`state: "NoteOn"`); confirm with a re-read — never assume success.
- New notes: `bw_call {path: "cursorClip", method: "setStep", args: [0, 4, 64, 100, 0.25]}`
  (channel, x, y, velocity 0-127, duration in beats).

## Deep domains: drum pads, layers, nested chains (cursor-anchored)

`drumPads`, `layers`, and `slotDevices` follow **cursorDevice**. Point the
cursor first, then read:

1. `bw_call {path: "cursorDevice", method: "selectDevice", args: ["tracks[0].devices[0]"]}`
2. `bw_get {path: "cursorDevice.hasDrumPads"}` / `hasLayers` / `hasSlots`
3. `bw_get {path: "drumPads[4].name"}`, `bw_get {path: "drumPads[4].devices[0].name"}`
4. Deeper nesting needs no new surface — re-anchor the cursor *into* the pad:
   `bw_call {path: "cursorDevice", method: "selectFirstInKeyPad", args: [36]}`
   (or `selectFirstInLayer(0)`, `selectFirstInChannel("drumPads[4]")`,
   `selectParent`), then the same `cursorDevice`-anchored surface reads the
   nested chain. The drum-pad bank window is 16 pads — scroll with
   `bw_call {path: "drumPads", method: "scrollBy", args: [16]}` (or set
   `drumPads.scrollPosition`) for kits beyond it.
5. FX-slot chains (live-verified): `slotDevices` follows the **slot cursor**,
   not the device cursor. Drive it with
   `bw_get {path: "cursorDevice.slotNames"}` →
   `bw_call {path: "cursorDevice.getCursorSlot", method: "selectSlot", args: ["FX"]}`
   → `bw_get {path: "slotDevices[0].name"}`. (`selectFirstInSlot("FX")` is
   different — it moves the *device* cursor onto the slot's first device.)

## Browser sessions (generic)

1. Open: `bw_call {path: "tracks[0].endOfDeviceChainInsertionPoint", method: "browse"}`
   (or `cursorDevice.replaceDeviceInsertionPoint` / `beforeDeviceInsertionPoint` /
   `afterDeviceInsertionPoint`).
2. Confirm: `bw_get {path: "browser.exists"}` → true (allow a settle re-read).
3. Inspect: `bw_snapshot {path: "browser", depth: 2}`;
   `bw_get {path: "browser.deviceColumn.items[3].name"}`.
4. Filter: `bw_call {path: "browser.categoryColumn.items[3].isSelected", method: "set", args: [true]}`
   — then re-read the results bank (filters reset the result list; settle).
5. Choose: `bw_call {path: "browser.resultsColumn.items[0].isSelected", method: "set", args: [true]}`.
6. Commit or bail: `bw_call {path: "browser", method: "commit"}` /
   `{method: "cancel"}` — then verify the inserted device via
   `tracks[0].devices[…]` reads (honest verification, as always).

## Snapshots

- Whole mix in one call: `bw_snapshot {path: "tracks", depth: 2}` → flat
  `{"tracks[0].name": "Drums", "tracks[0].volume": {"value": 0.68, "displayed": "-10.0 dB"}, …}`.
- Batch: `bw_snapshot {paths: ["transport.tempo", "tracks[0].volume"]}` —
  per-path errors, one round trip.
- Every response carries `project` (focused project name) — verify it when
  mixing reads with mutations; focus can change between calls.
- Depth budget (live-verified): `path: "tracks", depth: 2` hits the
  10k-entry cap (honest `truncated: true`) because 128-slot clip banks
  dominate the walk. Use `depth: 1` for mixer state, batch mode for exact
  paths, and `depth: 2` on narrower subtrees (`tracks[0]`, `drumPads`).
