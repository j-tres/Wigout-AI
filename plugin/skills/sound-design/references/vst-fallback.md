> Provenance: Original distillation (Wigout Studio) — heuristic strategy for non-stock (VST/CLAP) plugins. No curated per-device authority; every mapping here is best-effort by construction.

# VST / CLAP fallback — best-effort sound design for non-stock plugins

The per-device guides under `devices/` are the *truth* for stock Bitwig
devices because their parameter surface is finite and knowable. A third-party
**VST or CLAP** plugin is the opposite: its parameters are arbitrary, named by
its own vendor, and change with every plugin and preset. There is no curated
guide to reach for. This file is the honest strategy for that case —
**read what the plugin actually exposes, map its names onto parameter families
by heuristic, reason with the descriptor dictionary, and label everything
best-effort.** You are pattern-matching a vendor's labels, not reading a
verified spec. Say so in the report every time.

**The one rule that never bends:** on a non-stock plugin you have *no curated
authority*. A control called "Cutoff" almost certainly is a filter cutoff — but
the plugin's real semantics may differ (it might be a wavetable position, a
macro, a display-only readout). Treat every mapping below as a hypothesis to be
confirmed by the read-back `display_value` and, ideally, by ear — never as
fact. All guidance produced from this file is **best-effort**.

## 1. Enumerate the plugin's live parameters through the bridge

You cannot design against knobs you cannot see. Before anything else, read the
selected plugin's **live parameter list** off the bridge. There are two read
surfaces; use both, and know what each gives you:

**A. `cursorDevice.directParameters` — full native enumeration (names +
normalized values).** `bw_get cursorDevice.directParameters` returns *every*
automatable parameter the plugin exposes, each with an opaque `id`
(`CONTENTS/PID…`), a live **parameter name**, and a normalized `value` (0..1;
`null` when the plugin reports it inaccessible). **Live-proven 2026-07-12:**
Synplant enumerated 74 params (`Mod Wheel`, `Rotation`, `Tuning`, `Atonality`,
…), Reaktor 6 enumerated 148. This is the answer to the page-less-plugin
problem (below) — it works even when the plugin has zero remote-control pages,
and it re-populates automatically when you move the cursor to a different
device. **You can also WRITE here, and the write self-confirms (live-proven
2026-07-12):** `bw_call cursorDevice setDirectParameterValueNormalized(id,
value, resolution)` genuinely sets the parameter — value is in
`[0..resolution-1]`, so use `resolution=1000, value=round(target01*999)` for a
0..1 target (e.g. 50% → `value 500, resolution 1000`). The bridge auto-refreshes
the cache after the write (it bounces the cursor device internally) and returns
`verified:true` plus the confirmed post-write `value` right in the response — no
manual reselect, and `directParameters` reads fresh immediately after. **Two
things to know:** (1) **no `display_value`** — only the raw normalized value,
because the display-value observer crashes the extension (finding #45, on beta
AND stable); a name like "Tuning" at `value 0.497` tells you the control and its
position but not "-3 cents". (2) **each write costs ~2s and briefly flickers the
device/track selection** — that's the cost of the ground-truth bounce (finding
#48); if a write returns `verified:false` with a "reselect" note, the cache
couldn't be refreshed (rare — e.g. a lone device on the only track) and you
should reselect + re-read to confirm.

**B. `get_selected_device_parameters` / `cursorRemoteControls` page — the 8
pinned controls, WITH `display_value` and confirmable writes.** Returns the 8
assignable remote controls **for the currently selected page**, each with a
name, normalized `value`, and a human `display_value` (e.g. `"Cutoff" →
"1.14 kHz"`). Multi-page walking is confirmed live (2026-07-11): read
`cursorRemoteControls.pageNames` / `pageCount`, `bw_set
cursorRemoteControls.selectedPageIndex`, re-read. This is the surface with
`display_value` strings and the surface whose writes verify — but **a plugin
often has NO pages at all**: three real VSTs (two Synplant, one Reaktor 6)
reported `pageCount 0`, so this returns an **empty list** on a factory-fresh
plugin (pages exist only when a user or preset created them). Creating a page
over the bridge (`bw_call cursorRemoteControls.createPresetPage`) yields an
**empty 8-slot page** — Bitwig does not auto-fill it, and pinning params is a
UI action the API cannot drive.

**How to combine them:** enumerate with **A** to discover what the plugin
actually calls its controls (and their current normalized positions), map
those names to families (§2). You can also *move* controls through **A** —
writes land and self-confirm (the response carries `verified:true` and the
confirmed `value`), at ~2s each. The only reasons to prefer a remote-control
page (**B**) are when you need a human-readable `display_value` in the loop or
faster writes. If a control you need is on neither surface (rare — **A**
enumerates everything), shape around the plugin with stock devices (§4). Trust
the write response's `verified` flag: `true` means the confirmed `value` is
ground truth; `false` means reselect the device and re-read to confirm.

Capture whichever list you use to JSON — both `cursorDevice.directParameters`
(full native list) and `get_selected_device_parameters` (page controls) carry
per-parameter `name`s, which is the field `param_resolver.py` matches on (see
§3). The DirectParameter list is the fuller map — it discovers *and* writes
every control (confirm via reselect); the page list is the subset with prompt
`display_value` confirmation. Fuzzy-match against whichever list holds the
control you're moving. If a control appears in neither (rare), it is not
addressable from here; say so rather than pretending it came from a surface
you did not actually reach.

## 2. Map common live parameter names to families (heuristic)

Stock guides map a *function* to an exact label. Here you do the reverse: take
the vendor's live **parameter name** and guess which **parameter family** it
belongs to, so the descriptor dictionary (which speaks in families) can drive
it. The families are the same ones used everywhere in this KB —
cutoff, resonance, amp/filter envelope stages, saturation/drive, effect
mix, modulation depth and rate.

Common name → family, most-confident first (all best-effort):

| Live name (and aliases) | Parameter family | Notes / ambiguity |
| --- | --- | --- |
| **Cutoff**, Freq, Frequency, Tone, Color | filter **cutoff** — the primary brightness lever | "Freq" can also be an oscillator/LFO pitch — check the `display_value` units (Hz vs semitones vs %) |
| **Res**, Resonance, Q, Emphasis, Peak | filter **resonance** | high Q on an EQ band ≠ filter resonance; confirm the device role |
| **Attack**, Atk, A | **amp (or filter) envelope** attack | which envelope it drives is unknown from the name alone |
| **Decay**, Dec, D | envelope decay | |
| **Sustain**, Sus, S | envelope sustain level | |
| **Release**, Rel, R | envelope release / tail | |
| **Drive**, Dist, Gain, Sat, Crush, Fold | **saturation / drive** (harmonic density) | "Gain" is overloaded — can be clean level, not dirt; read units |
| **Mix**, Dry/Wet, Amount, Blend, Wet | **effect mix / depth** | for an insert FX this is the wet/dry balance |
| **Depth**, Amount, Mod, Intensity | **modulation depth** | pairs with a Rate control below |
| **Rate**, Speed, Freq (LFO), Time, Sync | **modulation rate** (LFO/chorus/delay) | tempo-synced vs Hz changes how you set it — read the readout |

Use this as a *starting hypothesis*. When two names collide (two "Gain"s, a
"Freq" that could be pitch or cutoff), disambiguate from the `display_value`
units and the plugin's obvious type before you write. Anything you cannot
place, leave alone and name in the report as unmapped.

## 3. Resolve the mapped name against the live list with `param_resolver.py`

Once you have decided *which family* you want to move and *what the plugin
calls it*, resolve that live name to a concrete slot the way every role does —
do not eyeball the index:

```
cd ${CLAUDE_PLUGIN_ROOT}/scripts && uv run python param_resolver.py \
  --params-json - --query "Cutoff"
```

Pipe the JSON parameter list you captured in §1 to stdin. `param_resolver.py`
does string matching only — it fuzzy-matches your query against the *live*
names, so feed it the vendor's actual label (from the read-back), not a
synonym. It returns the best slot plus ranked `candidates`; a low `score` or a
surprising winner is a signal the plugin does not name things the way you
assumed — re-read the list rather than forcing a write. Then write with
`set_selected_device_parameter` / `set_selected_device_parameters` and, as
always, **read the `verified` flag** and re-check the `display_value`.

## 4. Apply the descriptor dictionary generically

With the plugin's controls enumerated and mapped to families, the request
("make it warmer", "too harsh", "wider") is handled exactly as for a stock
device: run it through `descriptor-dictionary.md`, which already speaks in
parameter families, and translate each move onto whichever live control you
mapped to that family. "Warm" → lower filter **cutoff** slightly + gentle
**drive** → find the plugin's Cutoff/Tone and its Drive/Sat, resolve them via
§3, and move them. The *why* behind each move is in `synthesis-fundamentals.md`;
FX-chain ordering and character (adding a Bitwig **stock** EQ+/Saturator/Reverb
*around* the VST when the plugin lacks the tool) is in `fx-character.md`. Often
the most reliable move on an opaque plugin is exactly that — leave the plugin
mostly alone and shape it with stock devices you *do* have guides for.

## 5. Label everything best-effort — honesty

- State plainly, in the report, that the plugin is non-stock and the guidance
  is **best-effort** heuristic name-mapping, not curated device authority.
- Name the parameters you *mapped by guess* and the ones you *left unmapped*.
  If a mapping was ambiguous (a "Freq" that might be pitch or cutoff), say which
  reading you took and why.
- Confirm each change by its read-back `display_value` and the `verified` flag —
  a write that "looks right" by name but did not verify is reported as
  `verified:false`, never smoothed over.
- The plugin's real parameter semantics may differ from any mapping here; if a
  move does not sound like the descriptor predicts, trust the ear (and, when a
  render handoff exists, `sound_analysis.py`) over the name-based guess, correct,
  and re-report.

This ceiling is by design: generic name-mapping will miss idiosyncratic plugin
parameters. Labeled best-effort and confirmed against live read-back, it is
still a genuine, honest path to designing sound on plugins this KB can never
enumerate — see the shared honesty rules in
`../../coach/references/workflows.md` for how roles report uncertainty.
