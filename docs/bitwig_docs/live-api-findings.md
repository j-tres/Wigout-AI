# Live-verified Bitwig API findings

Facts about the Bitwig Controller API v25 proven against a running Bitwig 6.1 Beta 2
during Full API Bridge Cycle 1 (2026-07, branch `feature/api-bridge`, PR #10). Each was
discovered because unit tests **cannot** catch it — mocks obey the documented interface;
the live host does not. Future plans should treat these as constraints, and live in-Bitwig
gates as mandatory for anything touching a new API mechanism.

## Value access

1. **Interest registration is init-only.** `Value.markInterested()` and
   `addValueObserver()` are honored only during `ControllerExtension.init()`. Called
   later they are *silently ignored* — no exception, no log — even when routed to the
   controller thread via `host.scheduleTask(...)` + `requestFlush()`. `Value.get()` on a
   never-marked value throws `"Either call markInterested() or add at least one observer
   in init..."`. Consequence: any generic read surface must bulk-mark at init
   (`BridgeInterestMarker`, depth 2 from roots and bank window items).
2. **Subscription ≠ interest.** `Subscribable.subscribe()` controls observer
   notification efficiency; the driver is "subscribed to everything by default"
   (Subscribable javadoc). Subscribing does not unlock `get()` — only interest does.
3. **Interest is per-proxy.** `track.volume()` and `track.volume().value()` are distinct
   proxies with independent interest state; marking one does not unlock the other.
   A Parameter read touches up to five proxies (itself, `value()`, `displayedValue()`,
   `name()`, `exists()`) — all must be marked.
4. Init-time bulk marking is cheap: ~70k `markInterested()` calls (128-track bank ×
   values × per-track 128-slot device banks) complete within sub-second extension init,
   with per-class reflection caching.

## Reflection over live objects

5. **Bitwig impl classes implement internal obfuscated interfaces that *extend* the
   public API interfaces.** E.g. the live `Parameter` proxy's class does not directly
   implement `Parameter`; it implements an internal interface which extends it.
   Interface walks must traverse *through* non-`com.bitwig.extension` interfaces while
   accepting members only from public API ones. A filter that prunes at internal nodes
   loses the entire Parameter surface. (Transport/Track happen to implement their public
   interfaces directly, which masks the bug in shallow testing.)
6. Live proxies also expose obfuscated members (`BDZ`, `JWa`, `getAssertionChecker`
   returning obfuscated types, `initOnEventThread`, ...) via those internal interfaces —
   another reason member enumeration must be filtered to public API interfaces.
7. **Runtime `@Deprecated` is authoritative and present** on API interface methods.
   Check `Method.isAnnotationPresent(Deprecated.class)` before any reflective invoke;
   a deprecated call at runtime halts the extension in Bitwig 6.x.

## Mutations

8. **`SettableRangedValue.set(double)` honors the user's controller take-over strategy**
   (documented in its javadoc, easy to miss): with take-over configured, a one-shot
   programmatic `set()` silently never applies. Use `setImmediately(double)` for
   programmatic absolute writes. `setRaw()` and boolean/enum/string/integer setters are
   unaffected.
9. App-level mutations (`set`, `play()`, `createAudioTrack`, `createEmptyClip`,
   `setStep`, `deleteObject`) work fine from Jetty request threads — no `scheduleTask`
   needed. Mutations are asynchronous; confirm effects by polling cached reads
   (bounded, honest `verified`).
10. **Note composition works through the generic bridge with zero dedicated code**:
    `clipLauncherSlotBank[i].createEmptyClip(beats)` → `.select()` (cursorClip follows)
    → `cursorClip.setStep(channel, x, key, velocity, durBeats)` → `.launch()`.
    Gap: note *read-back* — `getStep` returns `NoteStep` objects the bridge cannot
    navigate, and step observers are unbuilt (Cycle 2).

## Environment / workflow

11. **The controller follows the focused project.** With multiple projects open, every
    read/write targets whichever project has focus, and focus can change between two
    MCP calls. Agents must read `application.projectName` + target identity in the same
    batch as a mutation. (Cycle 2 idea: optional `expected_project`/`expected_track`
    preconditions on `bw_set`/`bw_call` that refuse on mismatch.)
12. **MIDI clock sync ON silently disables API transport play** (any controller,
    curated or bridge): the transport slaves to external clock; `play()` invokes
    without error and nothing rolls. Check this first when transport won't start.
13. Bitwig hot-reloads the extension when `Wigout.bwextension` changes on disk;
    `./gradlew deploy` is sufficient. Controller script output/errors appear only in
    Bitwig's controller console window — not in `BitwigStudio.log`/`engine.log`.
14. Empty in-window bank items read as empty values (e.g. `tracks[99].name` → `""`,
    `exists` → false) rather than erroring; out-of-window indexes are a bounds error.

## Notes / cursor clip (Cycle 2, Gate A, 2026-07-09)

15. **Finding 14 extends to mutations: slot operations beyond the project's scene
    count silently no-op.** `clipLauncherSlotBank[5].createEmptyClip(4)` in a
    5-scene project invokes without error and creates nothing (`hasContent` stays
    false, `cursorClip.exists` stays false, subsequent `setStep` writes vanish).
    Check `scenes` `item_count` before slot mutations.
16. **The init-registered `NoteStepChangedCallback` is authoritative, not
    delta-only.** It delivers the full content of the clip the cursor points at —
    at extension init (for a pre-existing clip the instance never saw) and on every
    cursor re-point — and delivers `Empty` callbacks for cleared cells on re-point
    and on clip deletion, so a sparse cache holds no ghost entries. Caveat:
    selecting an *empty* slot on the same track does not re-point the cursor clip
    (the cache keeps the last real clip's content — correct cursor semantics, not
    staleness); selecting a slot on another track does re-point it.
17. `setStep(ch, x, y, velocity 0–127 int, beats)` quantizes velocity to MIDI
    (100 → 0.787…); `NoteStep.setVelocity(double 0..1)` on an existing note is
    exact (0.9 → 0.9) and works from Jetty threads on cached NoteStep objects
    long after init. Unwritten cells read back via `getStep` as `state: "Empty"`
    with zeroed fields (not an error).

## Depth domains (Cycle 2, Gate B, 2026-07-09)

18. **The slot cursor is independent of the device cursor.**
    `CursorDevice.selectFirstInSlot("FX")` moves the *device* cursor onto the
    slot's first device; a device bank created from `getCursorSlot()` stays
    empty until the *slot* cursor is pointed explicitly:
    `cursorDevice.getCursorSlot` → `selectSlot("FX")` → the slot-anchored
    device bank populates (`item_count` reflects the slot chain). Discovered
    live by `bw_describe` on the cursor-slot proxy.
19. Cursor-anchored depth re-pointing works exactly as designed:
    `selectDevice(<path>)`, `selectFirstInKeyPad(36)`, `selectFirstInLayer(0)`,
    `selectParent()` all re-anchor `drumPads`/`layers`/`slotDevices` +
    `cursorDevice` values live, from Jetty threads, with sub-second settle.
    DrumPadBank auto-positions its window at the lowest populated key
    (`scroll_position` read 36 on a standard kit).

## Popup browser (Cycle 2, Gate C, 2026-07-09)

20. **The generic browser session works end-to-end with no dedicated tool.**
    `<insertionPoint>.browse()` → `browser.exists` true within ~2 s; filter
    via `<column>.items[i].isSelected.set(true)` re-filters results within
    ~1 s (observed 1435 → 398 on a category select); result select +
    `browser.commit()` inserts the device and **auto-closes the session**
    (`exists` → false — don't try to reuse it); `cancel()` closes with the
    chain untouched.
21. **Column child proxies are stable across calls** — repeated
    `popupBrowser.deviceColumn()` invocations return the same proxy, so an
    identity-keyed map created at init correctly backs the `items` edge at
    runtime. (This generalizes: the whole init-marking architecture relies on
    stable child proxies, now directly confirmed for browser columns.)

## Cycle 2 final E2E (2026-07-09)

22. **`cursorClip.notes` reads back one cell per grid step, not one per note:**
    a 0.5-beat note at 16th-step resolution occupies two cells — `NoteOn` at
    its start plus `NoteSustain` for the tail. Agents counting notes must
    filter `state == "NoteOn"`.
23. **Snapshot depth budget:** `bw_snapshot {path: "tracks", depth: 2}` hits
    the 10k-entry cap (honest `truncated: true`) because 128-slot
    clip-launcher banks dominate the walk. For mixer state use `depth: 1`
    (track-level values only) or batch mode with exact paths; reserve
    `depth: 2` for narrower subtrees like `tracks[0]` or `drumPads`.
24. **Docs/jar mismatch exists in API v25:** `TrackBankFlatteningMode` and
    `TrackBank#setFlatteningMode` appear in `BitwigAPI25.txt` but are absent
    from the compiled `extension-api:25` jar (verified via `javap`/`unzip`).
    The coverage gate excludes them with this stated reason — treat the
    compiled jar as authoritative when the two disagree.
25. Deliberate deviation from the Cycle 2 spec (Decision 3): NoteStep setter
    calls report `verified: false` + a re-read hint like every void `bw_call`
    (Cycle 1 convention), instead of the specced bounded getter re-read.
    Honest-never-fabricated holds either way; agents confirm with
    `bw_get cursorClip.step(ch,x,y)` (finding 17 shows the re-read is exact).

26. **`InsertionPoint.insertFile(path)` works and is context-sensitive** (proven vs
    Bitwig 6.1b2, project "Test_Loops", Wigout Studio Task 16). Given an absolute path
    to a `.wav`, the result depends on the insertion point's context:
    - `track.clipLauncherSlotBank[s].replaceInsertionPoint` → an **audio clip** in that
      launcher slot, `name` = the file's stem (e.g. `probe-440hz`), `hasContent` → true.
      This is the path for landing generated/ACE-Step audio as a clip.
    - An **instrument** track's `startOfDeviceChainInsertionPoint` → inserts a **Sampler**
      with the file loaded (a hum → playable-instrument route without transcription).
    - An **audio-effect** context → inserts a **Convolution** using the file as its IR.
    All four `*InsertionPoint` members (`afterTrack`, `beforeTrack`,
    `startOfDeviceChain`, `endOfDeviceChain`) resolve; slots also expose
    `replaceInsertionPoint`. Returns the usual async `verified: false` + re-read hint;
    confirm with `hasContent` / `list_devices_on_track`.
27. **Call-returned API arrays are not bridge-navigable.** `application.getActions()`
    returns `Action[]`; the bridge reports "Returned an API object — navigate to it with
    a bridge path instead", but the array is **not a Bank**, so `...getActions()[0]` fails
    with "not indexable". Bulk enumeration of actions over the bridge is therefore blocked.
    Targeted lookup works and is null-safe: `application.getAction('<id>')` then
    `.getName()`. Unknown IDs make the *object* null (a later `.getName()` errors
    `target is null`) — probe existence by catching that. Action IDs are **case-sensitive**
    with mixed conventions: `bounce_in_place` → "Bounce In Place (Pre-FX)" (**no dialog**,
    the v2 render-handoff candidate), `bounce` → "Bounce...", `Export Audio` → "Export
    Audio...", `Save` → "Save", `Collect and Save` → "Collect and Save..." (the "..."
    ones open modal dialogs — never invoke headlessly without user consent).
28. **Project audio folder layout** (project "Test_Loops"): `<root>/<Project>/` contains
    `samples/` (collected + recorded audio land here), `bounce/` (Bounce In Place / bounce
    output, named `<Project>-bounce-N.wav` with a `.counts/` sidecar), `multi-samples/`,
    `wavetables/`, `auto-backups/`, and the `.bwproject` file. **A slot-inserted external
    file is NOT copied into the project folder** — the clip references the original path
    (the resolution-ladder weak spot). **`File → Collect and Save…` copies referenced
    external files into `samples/`** by file stem, after which the clip-name → file-name
    match ladder resolves (verified: an externally-inserted `probe-440hz` clip resolved to
    `samples/probe-440hz.wav` only post-collect).
29. **`create_track` / `delete_track` renumber `tracks[]` immediately.** A new track is
    inserted adjacent to the current selection, shifting every higher index (observed: a
    new track landed at index 8 and pushed `Master` from 10 to 11, so a subsequent
    `tracks[11]` mutation hit the wrong track). **Re-read the track list after any
    structural mutation; never reuse a pre-mutation index.** For deletes, remove the
    highest index first when removing several.

## Sound-design KB live-gate (2026-07-10)

30. **Phase-1 curated device-parameter write-path fix verified live.** The
    fix (`set()` → `setImmediately()` for ranged normalized writes, plus an
    honest bounded-poll `verified` readback in place of an unconditional
    success report) was exercised against a live Bitwig instance with the
    project focused and Polysynth selected: `set_selected_device_parameter`
    on Polysynth param 0 (Cutoff) 0.341 → 0.7 returned `verified: true` and
    the displayed value genuinely moved (156 Hz → 2.74 kHz); restoring the
    original value also returned `verified: true`. This is the exact call
    that previously reported false success while the underlying value stayed
    put — confirmed fixed and honest. Separately, **Polysynth's filter
    cutoff remote control is labeled "Cutoff"**, not "Frequency" — Polymer's
    equivalent remote is labeled "Frequency"; the two stock synths differ,
    so read the live displayed parameter names rather than assume a label
    across devices. The 8 Polysynth remote controls observed live: Cutoff,
    Filt EG, Attack, Release, Vibrato, 1/2 Mix, Delay, Output.

## Engineer-v2 render-handoff live-gate (Gate R, 2026-07-11)

31. **No dialog-free POST-FX render action exists.** Confirming the
    render-handoff ladder's riskiest premise: `bounce_in_place` is the
    only headless render and it is explicitly "(Pre-FX)" — it cannot
    capture mix moves or the summed master. 13 plausible post-FX action
    IDs were probed null-safe via `application.getAction('<id>').getName()`
    (`bounce_in_place_post`, `bounce_in_place_post_fx`, `bounce_post`,
    `bounce_post_fx`, `bounce_selection`, `render_in_place`, `consolidate`,
    `export_audio`, `export_audio_no_dialog`, `Bounce In Place`,
    `Bounce in Place (Post-FX)`, `Export Audio Selection`, …); all returned
    a null action (later `.getName()` → "target is null", per finding #27).
    Only the three known IDs resolve: `bounce_in_place` → "Bounce In Place
    (Pre-FX)", `bounce` → "Bounce..." (dialog), `Export Audio` → "Export
    Audio..." (dialog). **Getting the post-FX/summed mix out requires a
    dialog (Export Audio) or a hand-wired record-print track — there is no
    headless post-FX path.**
32. **Track input routing is not settable over the bridge; `SourceSelector`
    exposes no input chooser.** `Track` exposes settable `arm`, `monitor`,
    `monitorMode` (all writable) and a `sourceSelector`/`getSourceSelector`
    navigation member, but `SourceSelector` itself only exposes
    `hasAudioInputSelected` / `hasNoteInputSelected` (both `readable:false`)
    plus subscribe plumbing — **no settable channel/input target**. So a
    fully-API-driven "record the master into an audio track" print path is
    not reachable; the record-print rung degrades to a persistent
    user-created Print track (input wired to the master once by hand),
    after which arm+record+stop is dialog-free.
33. **Dialog-assisted Export Audio is the working render path, and file
    pickup-by-recency works.** `application.getAction('Export Audio').invoke()`
    over `bw_call` opens the export dialog (returns the usual async
    `verified:false`); after the user completes it, the rendered WAV lands
    where the dialog points and is found by comparing a pre-render dir
    listing to a post-render one (newest by mtime). Verified end-to-end:
    an export to `<project>/bounce/` was picked up and measured by
    `mix_report.py` (produced honest LUFS/peak/band numbers). Export
    filename form is `<Project> <YYYY-MM-DD> <HHMM>.wav` — distinct from
    Bounce-In-Place's `<Project>-bounce-N.wav`. Focus caveat reconfirmed:
    the controller follows the focused project (switching projects
    mid-session re-points `application.projectName` and all reads).

## Engineer-v2 mix-verification live-gate (Gate E, 2026-07-11)

34. **Address the master via the `masterTrack` root, NEVER `tracks[N]`.**
    The `tracks[]` bank is a moving window whose indices are not stable
    across selection/scroll: in one project `tracks[9]` read as the master
    early in a session and as an ordinary instrument track ("ProbeInst")
    later, with no structural edit — the window had shifted. Writing
    "the master" as `tracks[9].volume` therefore silently moved an
    instrument track: the write returned `verified:true` and the cached
    `displayedValue` changed (it WAS a real write — to the wrong node),
    while the actual Project Master fader never moved and the summed export
    was unaffected. The stable anchor is the dedicated root
    `masterTrack` (a live-proven bridge root): `masterTrack.volume.value`
    /`.displayedValue` read the real master, and a `setImmediately`-backed
    `bw_set masterTrack.volume.value` moves it visibly and returns
    `verified:true` (proven live: −1.2 dB → −7.3 dB → restored). Lesson:
    resolve mix targets by identity (`masterTrack`, or `tracks[i].name`
    re-read in the same batch), never by a remembered bank index (compare
    finding #29's renumbering rule).
35. **Verify level/parameter moves by direct API state readback, not by
    re-rendering.** A control move is confirmed exactly and cheaply by
    reading `<path>.value` + `.displayedValue` back (the honest `verified`
    flag already does a bounded poll of this). Re-exporting to measure a
    delta is the wrong tool for confirming a *control* changed — it is slow,
    needs a user dialog per render (finding #33), and its output reflects
    only what the render path includes (a move to a track absent from the
    exported region/time-range produces no delta, which reads as a false
    "nothing happened"). Reserve renders + `mix_report.py` for measuring
    the actual summed AUDIO (loudness, spectral balance, true-peak of the
    real mixdown); verify individual moves against project state. This also
    matches reading levels straight from the API rather than round-tripping
    through audio.

## Offline-readers live-gate (Gate O, 2026-07-11)

Project "Arp_Synth_Test_Project" (a cloned project, Bitwig **6.1 Beta 4**),
23 flat tracks incl. 3 groups (Percussion/Ocean/Synths). Bridge driven via
the scratchpad Streamable-HTTP client (no `bitwig` MCP server registered
this session, same as Gates R/E).

36. **The DAWproject export action exists and is `export_project` → "Export
    DAWproject…" — MODAL (dialog), invoke only with user present.** Probed
    13 candidate IDs null-safe via `application.getAction('<id>').getName()`
    (finding #27/#31 method). Exactly one resolved: `export_project` →
    display name `Export DAWproject…` (the "…" marks a dialog, per #27; the
    name is *DAWproject*, not "Export Project"). All other conventions
    (`export_dawproject`, `save_as_dawproject`, `Export DAWproject...`,
    `Export Project`, camel/underscore variants) returned a null action
    ("target is null"). `getAction('export_project').invoke()` over
    `bw_call` returned the usual async `verified:false` and opened the
    Export DAWproject save dialog; after the user completed it, the file
    landed at `<project>/<Project>/<Project>.dawproject` (57 KB) and was
    picked up by pre/post directory listing (finding-#33 recency pattern).
    So agent-triggered DAWproject export = the #33 dialog-assisted flow,
    keyed to `export_project`.
37. **`dawproject_read.py` digest validated against live bridge state.**
    Digest of the real export cross-checked against `bw_get`/`list_tracks`
    in the same session: tempo 110.0 = live `transport.tempo` raw 110.0;
    track names, group nesting, and per-track device names all matched
    (e.g. Percussion group → Synplant/Marimba/… children; Synplant =
    Vst3Plugin, Marimba = Sampler). Faithful-read confirmed on a
    surprising value: several tracks digest `channel.volume 0.0` because
    the export literally encodes `Volume value="0.000000"` — those tracks
    ride a Volume automation lane up from 0 (Marimba has a 2-point Volume
    lane, `target id18`), so the static value really is 0. The reader
    reports what the file holds; it does not infer the automated level.
    `notes`/`automation` extraction proven on child "Marimba": clip
    "MidiArp" → 128 notes with plausible keys/velocities; Volume lane
    listed. `embeddedAudio` was empty (this project references library
    content, not embedded audio) and `hasTempoAutomation` false (constant
    110). Real-file integration tests (`WIGOUT_TEST_DAWPROJECT`/
    `WIGOUT_TEST_BWPROJECT`) went green: 23/23 in the two suites.
38. **Bitwig's arrangement `Lanes` are FLAT siblings, one per track — the
    group-track descendant-scoping concern does not manifest.** The final
    review flagged that `notes()`/`automation()` use `lanes.iter(...)`
    (descends subtrees), which would over-attribute child content IF a
    group's arrangement `Lanes` nested its children's `Lanes`. Verified
    against the real 6.1 export: the `Arrangement` has 23 top-level
    `Lanes[@track]` entries (one per flat track incl. groups) and **zero**
    `Lanes[@track]` nested inside another. Confirmed end-to-end:
    `notes --track "Percussion"` (a group) returns 0 clips — no child
    bleed — while its children return their own notes. Bitwig flattens the
    arrangement even though `Structure` nests groups, so `.iter()` is safe
    for this exporter. (A different DAWproject producer *could* nest;
    re-verify if one appears.) **`bwproject_scan.py`** smoked on the 6.45 MB
    `.bwproject`: `bitwigVersionHint` "6.1" (matches the export), plugin
    hints correctly surfaced Synplant.vst3 and Reaktor 6.vst3 (both live
    devices), 3088 sample-path hints. Observed heuristic artifact (expected,
    `fidelity:"heuristic"` covers it): ASCII runs capture a leading
    length-prefix byte, so some strings read as `?Bitwig/…`/`^C:\Program…`
    — human-recoverable, and the reason the scanner is advisory-only. The
    UTF-16 lookbehind fix only guards UTF-16 runs; ASCII-run leading-byte
    noise is inherent to a strings scan and disclosed, not a defect.

## Live-sweep session (2026-07-11) — deferred legs batch

Bitwig 6.1 Beta 4, project "Arp_Synth_Test_Project" (the Gate-O project),
extension at the deployed 5d1a176 build. Bridge driven via the
Streamable-HTTP client fallback (no MCP server registered in the session).

39. **Preset-saved remote pages REPLACE a stock device's factory pages
    entirely.** Four Polymer presets and two Polysynth presets in a real
    project each exposed exactly ONE page ("Perform"; one preset had
    Main/Envelope/Page 3) — none of the factory pages were reachable. A
    factory-fresh (init-preset) Polysynth exposes 9 factory pages (OSC1,
    OSC2, MIX, FILTER, FILTER/EG, AMP, Envelope, Common, Vibrato), walked
    live via `bw_set cursorRemoteControls.selectedPageIndex` + re-read —
    multi-page enumeration is confirmed working. Consequences: (a) a
    device's full factory surface is only visible on presets without
    custom pages; (b) filter-label truth is *page*-dependent, not
    device-dependent — Polysynth cutoff observed as "Filt Freq" (factory),
    "Cutoff" (one preset macro), "LP" (another preset macro); Polymer's
    default Low-pass MG module page says "Cutoff"/"Reso", NOT "Frequency".
    KB corrected accordingly. Bonus: **Polysynth oscillator sync
    confirmed** — per-oscillator `Sync1`/`Sync2` on the factory OSC1/OSC2
    pages (resolves the deferred Gate-14 claim).
40. **Polymer's swappable modules surface as module-NAMED remote pages;
    the module chooser is API-invisible.** Init Polymer exposes a "Union"
    page (its default Union oscillator: Pulse %/PW/Saw %/Tri %/Num/Den/
    Pitch/Detune) and "Low-pass MG" (Cutoff/Reso/Drive/Keytrack/Ctf Mod).
    `cursorDevice.slotNames` on Polymer = ["FX","Note FX"] only — the
    oscillator/filter engine slots are NOT device-chain slots, so the
    engine list (e.g. whether a Wavetable module is available) cannot be
    enumerated over the bridge; engine swapping is a UI action. The
    Polymer-wavetable KB claim stays a confirm-on-device hedge.
41. **Plugins (VST) expose ZERO remote-control pages until someone creates
    them.** Two Synplant instances and one Reaktor 6 (all `isPlugin:true`)
    reported `pageCount 0` / empty `pageNames` — `get_selected_device_parameters`
    returns an empty list on a factory-fresh plugin, so the "read the
    remote-control page" fallback reads *nothing* by default.
    `bw_call cursorRemoteControls.createPresetPage` DOES create a page
    live (pageCount 0→1, 8 slots) but the slots are **empty** — Bitwig
    does not auto-fill plugin params, and pinning params into slots is
    not API-drivable. `deleteObject` on the page cursor silently no-ops;
    `application.undo()` removed the created page (used for cleanup).
    DirectParameter remains the only full-enumeration surface and is now
    **confirmed structurally unreachable via the generic bridge**:
    registration is init-only AND the observers take function callbacks
    `bw_call` cannot express. Ship condition for VST param access: a
    Java-side DirectParameter observer registered in `init()` (future
    cycle). vst-fallback.md §1 rewritten to match.
42. **`bounce_in_place` targets the UI selection — API `selectSlot()` /
    `slot.select()` are NOT sufficient (silent no-op); `slot.showInEditor()`
    is the working precondition.** Three escalating attempts on a
    launcher MIDI clip: plain selectSlot → no-op; focus_or_toggle_clip_launcher
    + selectSlot → no-op; `tracks[N].clipLauncherSlotBank[S].showInEditor()`
    → bounce rendered. Detection of the no-op: `cursorClip.notes` count
    unchanged and no new file. On success the WAV lands at
    `<project>/bounce/<Track>-bounce-N.wav` (pickup-by-recency works,
    finding #33 pattern) and the slot's MIDI clip becomes an audio clip
    (notes cache → 0). Refines findings #27/#31: the action resolves and
    is dialog-free, but "resolves" ≠ "acts" — it needs a real UI-selected
    clip.
43. **Full generative sound-design loop proven end-to-end** (the deferred
    "reese from scratch" scenario): `create_track` → `insert_device`
    (Polymer) → recipe params via remote pages with `verified:true` on
    all 11 writes (incl. display-value iteration to land discrete `Num`=3)
    → `createNewLauncherClip(0,8)` + `cursorClip.setStep(0,0,28,100,8.0)`
    (E1, verified via the notes cache: 1 NoteOn + 31 NoteSustain cells) →
    showInEditor + bounce_in_place → `sound_analysis.py` on the bounce:
    centroid 131 Hz, low band 0.983, descriptors bright:low / warm:high /
    full:high — matching the dark, held detuned-saw target. Scratch track
    deleted after; the bounce WAV remains in `<project>/bounce/`
    (deliberately not deleted while referenced by undo history).

## Small-items follow-up (2026-07-12)

44. **Polymer Wavetable oscillator CONFIRMED live — via factory-preset load,
    no UI eyeball needed.** The finding-#40 module-named-page mechanism
    doubles as a verification tool: `insert_device` with `preset_path`
    pointing at the factory preset `installed-packages/5.0/Bitwig/Essentials/
    Presets/Polymer/Crystal Computer Arp.bwpreset` produced a Polymer whose
    pages are [Perform, **Wavetable**, Sallen-Key, ADSR, Filter EG, Vibrato,
    Random] — a Wavetable oscillator module running inside Polymer's osc
    slot. Its page params: Index ("47.0 %" — the table position), IndexMod,
    PitchMod, PhaseMod, Unison (Off), U.Detune, Pitch, Detune. Two
    refinements: (a) *factory* presets can keep the module-named factory
    pages alongside a curated "Perform" page — the total-replacement
    behavior in finding #39 was observed on user-saved presets; (b)
    "Sallen-Key" observed as a second filter-module page name (alongside
    "Low-pass MG"). Also: .bwpreset binaries reference modules by UUID —
    a strings scan of all 209 factory Polymer presets found zero module
    name strings, so preset-file scanning cannot answer module questions;
    loading the preset and reading pages can.

## DirectParameter bridging (2026-07-12, extension change + live gate)

Resolves finding #41's open question — VST parameter enumeration — by
bridging the Device DirectParameter-by-ID surface: three observers
(`addDirectParameterIdObserver`, `addDirectParameterNameObserver`,
`addDirectParameterNormalizedValueObserver`) registered on `cursorDevice`
at init feed a `DirectParameterCache`, exposed as the synthetic read path
`cursorDevice.directParameters`.

45. **`DirectParameterValueDisplayObserver.setObservedParameterIds(...)`
    HALTS the extension on Bitwig 6.1 Beta 4** — the whole controller
    goes dead (bridge port stops answering, requires redeploy). Found by
    deploy-bisect: the void observers alone (id/name/value) init cleanly
    and serve reads; adding the display observer AND calling
    `setObservedParameterIds` kills init. Tried four invocation contexts —
    inside the id-observer dispatch, deferred one tick via
    `host.scheduleTask(...)`, the empty-array variant, and the
    null-for-empty variant — all dead. The display observer was therefore
    dropped entirely: the cache serves **names + normalized values only**.
    Consequence: no human-readable per-parameter display strings for
    DirectParameters (the normalized 0..1 value is all we get); the
    remote-control-page path (`get_selected_device_parameters`) remains
    the only source of `display_value` strings, and only for params
    pinned to a page.
    **RE-VERIFIED ON STABLE (2026-07-12, Bitwig 6.0.x, finding #47): still
    crashes — NOT beta-specific.** Better characterized this time: init
    *survived* when the cursor device had no observed plugin params, then
    the extension died the instant a real plugin (Synplant, 74 params)
    became the cursor device and `setObservedParameterIds` fired with its
    ids. Crashed identically with a **128**-id window and an **8**-id
    window — so it is the `setObservedParameterIds` call against real
    plugin parameters that is fatal, not the window size. Display strings
    for DirectParameters are unshippable, full stop.
46. **DirectParameter READ is fully live-proven.** Selecting Synplant
    (`tracks[1]`) yielded **74** parameters with live names (`Mod Wheel`,
    `Rotation`, `Tuning`, `Atonality`, `Effect`, `Release`, …, ids
    `CONTENTS/PID0..`) and normalized values; Reaktor 6 (`tracks[7]`)
    yielded **148** (names all "Reaktor", ids `CONTENTS/PID4d3182xx`). The
    id/value observers re-fire correctly on cursor-device change.
    **WRITE was initially reported unconfirmable — see finding #47, which
    OVERTURNS that: writes DO land.** The beta test only *looked* negative
    because the normalized-value observer does not re-fire on a
    programmatic write, so the cache reads stale immediately afterward; I
    never forced a fresh read (device reselect) to see the landed value.
    (`status:invoked` is meaningless as a success signal — it is returned
    even for a nonexistent parameter id — so it neither proves nor
    disproves the write.)

## DirectParameter stable re-verification (2026-07-12, Bitwig 6.0.x stable)

Same test project (Synplant on `tracks[1]`/`[3]`, Reaktor 6 on `[7]`) on
the latest STABLE Bitwig instead of 6.1 Beta 4. The shipped extension
(names+values read path) loaded and read cleanly — 74 Synplant params,
same as beta.

47. **DirectParameter WRITE LANDS — the beta "unconfirmable" verdict (#46)
    was a stale-cache artifact, not a broken write.** Proof by accident:
    early writes to Synplant `Rotation` (`CONTENTS/PID1`, originally 0.0)
    showed the cache unchanged at 0.0 — but after an unrelated redeploy
    re-init, `Rotation` read back **0.8508508508508509 = exactly 850/999**,
    the precise value of an earlier `setDirectParameterValueNormalized(id,
    850, 1000)` call. The write HAD landed; the cache had just never
    updated. Confirmed deterministically: `set(999, 1000)` then a
    **device reselect** (cursor to Marimba and back, forcing a fresh
    observer read) showed `Rotation = 1.0`; `set(0, 1000)` + reselect
    restored it to `0.0`. Conclusions:
    - **Writes work.** `bw_call cursorDevice
      setDirectParameterValueNormalized(id, value, resolution)` genuinely
      sets VST parameters.
    - **Resolution semantics confirmed:** value is in `[0 .. resolution-1]`,
      i.e. normalized = `value / (resolution-1)`. Practical convention:
      `resolution = 1000`, `value = round(target01 * 999)` for a 0..1
      target (e.g. 50% → `value 500, resolution 1000`).
    - **The cache goes stale after a write.** The normalized-value
      observer re-fires on device *change* but NOT on a controller-issued
      write (Bitwig appears to suppress the echo, avoiding a feedback
      loop). To read back a value you just wrote, **re-select the cursor
      device** (select away and back) — that forces the observer to report
      the true current value. Until then `cursorDevice.directParameters`
      shows the pre-write value.
    - Net: DirectParameter is a **read + write** surface. The two standing
      caveats are (a) no display strings (#45 crash, reproduced on stable),
      and (b) confirm a write by reselecting the device, since the cache
      does not auto-refresh on writes. **Caveat (b) is now automated — see
      finding #48.**

## DirectParameter write auto-refresh (2026-07-12, extension change + live gate)

48. **The bw_call write path now auto-refreshes the cache and returns the
    confirmed value.** Implements the finding-#47 follow-up. When
    `setDirectParameterValueNormalized` / `incDirectParameterValueNormalized`
    is invoked on the cursor device, the bridge bounces the cursor
    (selects a sibling and back — a device-chain hop when the device has a
    sibling, else a track hop; Bitwig restores the track's focused device
    on return, so the exact device is preserved either way) to force the
    id/value observers to re-fire, bounded-polls a cache **generation**
    counter until it advances by two (away + back) with the value settled,
    then returns `verified:true` and the confirmed normalized `value` right
    in the write response. No manual reselect needed. Live-gated on stable:
    Synplant (single-device → track bounce) and a multi-device track
    (device-chain bounce) both confirmed exact values (e.g. `600/1000` →
    `0.6006006`), device preserved, across repeated writes.
    - **Cost: ~2.1s per write** — this is Bitwig's inherent two-selection
      observer-re-fire latency (both bounce types measured the same, so it's
      the observer, not the navigation); the bounce gap is only 60ms. It is
      the floor for ground-truth confirmation via bounce, since there is no
      DirectParameter getter and the display observer crashes (#45). The
      poll ceiling is set to ~150 iterations (well above the ~2s completion)
      so a slower moment can't flip a real refresh into a false
      `verified:false`. Windows `Thread.sleep` is coarse (~15.6ms tick), so
      the effective per-iteration cost is ~2× the nominal 25ms.
    - **When it can't refresh** (no sibling to bounce to — a lone device on
      the only track — or the bounce doesn't settle in the budget) the
      response stays `verified:false` with an honest note to reselect and
      read `cursorDevice.directParameters`. Non-cursor-device writes are not
      refreshed (the cache is cursor-anchored); the hook is gated on target
      == cursorDevice by reference.

## Process lessons (for future cycles)

- **Live gates front-loaded on the riskiest mechanism paid off three times** (init-only
  interest, internal-interface traversal, take-over strategy). None were discoverable
  with mocks; all were found before dependent tasks were built on the broken premise.
- **Discriminating probes beat guessing**: comparing values the old facade happened to
  mark at init vs never-marked values is what isolated the init-only rule; probing
  `tempo.exists` (works) vs `tempo.value` (unknown member) is what isolated the
  traversal pruning bug.
- When a plan's core premise breaks, amend the plan (Amendment A1) with the user's
  decision rather than patching around it.
