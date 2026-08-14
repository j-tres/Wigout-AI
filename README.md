# Wigout AI - Bitwig AI Production Team

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![Bitwig API](https://img.shields.io/badge/Bitwig%20API-v25-orange.svg)
![Java](https://img.shields.io/badge/Java-21-red.svg)

**An MCP server for Bitwig Studio:** control and build Bitwig projects from
any MCP-capable AI agent (Claude Code, Claude Desktop, or any other MCP
client) over Streamable HTTP.

A single Java controller extension, **Wigout AI**, runs inside Bitwig and
exposes the Bitwig Controller API through the Model Context Protocol.
Curated tools cover common workflows (transport, tracks, devices, clips,
notes) and a generic API bridge reaches the rest of the Bitwig API v25
surface directly, so new capability doesn't have to wait on hand-written
wrappers.

```
AI agent ──MCP over HTTP──► Wigout AI extension (inside Bitwig) ──► Bitwig Controller API
```

## Project motivation
Learning Music Production and a modern Digital Audio Workstation (DAW) is f@#$! hard. I wanted a faster way to find answers, grounded in what I was working on, without technical troubleshooting taking me out of the creative flow.

So I wanted an AI Coach I could ask questions, who would be able to see my Bitwig project, analyze from different perspectives, consult specialists and give me very specific advice: *'Here's ***what*** you should do, ***why*** and ***how***.'*

It morphed into a full team of specialists, who have full read/write access in Bitwig when invoked directly. When these specialists are consulted by the Coach, the BW access should be read-only, with constructive feedback/explanations provided by Coach.


## Features

- **Curated tools:** transport control, track/clip + device/FX chain listing and
  control, clip & scene management, track construction.
- **Note read & edit:** read and write clip step data (velocity, duration,
  pan, timbre, pressure, gain, transpose, chance) via an init-registered
  observer cache.
- **Deep domain traversal:** drum pads, device layers, and nested FX chains,
  cursor-anchored so nesting needs no new API surface.
- **Generic browser sessions:** drive Bitwig's device/preset browser (open,
  filter, select, commit) through the same bridge.
- **Bulk snapshots:** read whole subtrees of the mix (`tracks`, `drumPads`,
  …) in one round trip, or batch arbitrary paths together.
- **Honest verification:** mutations report a real `verified` flag from a
  bounded poll of cached state; success is never fabricated.


Full API bridge reference (notes, deep domains, browser sessions, snapshot
depth budgets): [docs/API_BRIDGE.md](docs/API_BRIDGE.md).

## Wigout Studio: the Claude Code plugin

[plugin/](plugin/) ships **Wigout Studio**, a companion Claude plugin
that layers an AI production team of six specialists on top of the bridge,
each reachable by slash command or by natural language through a front-desk
router.

| Command | Specialist | Does |
|---|---|---|
| `/studio` | Front Desk | Routes any musical/Bitwig request to the right specialist (silent routing, at most one clarifying question); `/studio setup` installs/verifies the environment |
| `/coach` | Coach | Teaches against the *real* project: explains, demonstrates by pointing, checks understanding. Read-only, never mutates. |
| `/compose` | Composer | Writes MIDI, builds tracks, converts a clip/hum to MIDI with an instrument, generates audio via ACE-Step when available |
| `/sound-design` | Sound Design | Shapes timbre through direct, verified device-parameter and device-chain changes |
| `/theory` | Music Theory | Key inference, interval analysis, voice-leading checks (computed by music21, not recalled) plus corpus-grounded melody & chord progression statistics, tagged by genre, mood, etc |
| `/mix`, `/master` | Engineer | Full mix/master authority: audio spectral analysis, measured report cards, verified parameter moves, balance/masking fixes; mastering posture adds loudness/true-peak vs. target and matchering against a reference |

Every specialist first loads `bitwig-project`, a shared skill for reading
project state, addressing the bridge, and resolving project audio to files,
the common ground all roles build on. `composer` delegates timbre decisions
to `sound-design` rather than shaping devices itself; `coach` pairs
read-only with whichever role is acting. Full routing and delegation rules:
[plugin/reference/ROLE_INDEX.md](plugin/reference/ROLE_INDEX.md).

Setup: `/studio setup` (wraps `plugin/scripts/setup.ps1`). Python engines
are `uv`-managed (Python 3.10 pinned). Optional external tools it reports
on: ffmpeg (spectrograms; loudness works without it), an NVIDIA GPU +
claude-music/ACE-Step (audio generation; MIDI paths need neither).

See [plugin/README.md](plugin/README.md) for live-verified example
workflows (compose in a key, hum-to-instrument, loudness checks).

## Requirements

- Bitwig Studio 6 (API v25)
- For the Claude Code plugin path: Claude Code + `uv` (Python 3.10, uv-managed)
- For building from source: JDK 21

## Quick start

**Claude Code:**

```
/plugin marketplace add j-tres/wigout-ai
/plugin install wigout-studio
/studio setup
```

`/studio setup` detects your OS, installs `Wigout.bwextension` into
Bitwig's Extensions folder (downloading the latest release automatically),
and walks through the rest of the one-time setup without a manual build
step or a `claude mcp add` call. In Bitwig: **Dashboard → Settings → Controllers → Add
Controller → vendor "MCP" → "Wigout AI"**.

**Other MCP clients** (Claude Desktop chat, etc.): from `plugin/scripts`, run
`uv run python wizard.py deploy-extension` then `uv run python wizard.py
mcp-snippet` for a ready-to-paste MCP server config, just the raw bridge
without a plugin or slash commands. Paste it into a local settings file —
`claude_desktop_config.json` (Settings → Developer → Edit Config in Claude
Desktop) or `.mcp.json`/`~/.claude.json` for Claude Code — **not** "Add
custom connector": that flow is for account-level remote connectors and
requires HTTPS, which a localhost-only bridge can never satisfy.

**Building from source?** See [Building from source](#building-from-source) below.

## Testing

```bash
cd extension && JAVA_HOME='C:/Program Files/Java/jdk-21.0.10' ./gradlew test
```

---

## Technical Features

- A reflection-based dynamic bridge over a live, obfuscated, asynchronous object graph. Rather than hand-wrapping hundreds of API methods, the bridge dynamically resolves and invokes methods by reflection at runtime. The hard problem: Bitwig's real runtime objects implement internal obfuscated interfaces that extend the public API interfaces — so a naive reflection walk either loses the whole surface or invokes garbage. The solution traverses through obfuscated interfaces while accepting members only from the public com.bitwig.extension.* interfaces. 
- **Dynamic API bridge:** `bw_describe` / `bw_get` / `bw_set` / `bw_call` / `bw_snapshot` reach any object in the live Bitwig API graph (`tracks[3].devices[1].isEnabled`); deprecated members are refused at runtime with their replacement named.
- **API-surface coverage gate:** a test enumerates every Bitwig API v25 type/method and fails the build unless it's reachable through the bridge or excluded with a stated reason (`BridgeCoverageReportTest`).
- A coverage-gate test as an architectural invariant. An automated test proves every Bitwig API v25 type/method is either reachable through the bridge or explicitly excluded with a stated reason (276 types accounted for, zero unexplained) — catching drift between the docs, the compiled jar, and the bridge.
- Stateful domain caches for read-back the API doesn't natively give you. Init-registered observer caches implement a 128×128 MIDI step grid for reading/editing notes, and a DirectParameter cache for enumerating and writing VST plugin parameters.

## Building from source

```bash
cd extension
export JAVA_HOME='C:/Program Files/Java/jdk-21.0.10'
./gradlew deploy    # builds Wigout.bwextension and copies it to Bitwig's Extensions folder
```

Then connect an MCP client manually:

```bash
claude mcp add --transport http Wigout-MCP http://localhost:61169/mcp
```

This is the path for contributors working on the extension itself. End
users should use the [Quick start](#quick-start) above instead, which
never requires a JDK.

## Design

- Bitwig API **v25 only**: `docs/bitwig_docs/BitwigAPI25.txt` is the source
  of truth; no deprecated calls (`-Xlint:deprecation -Werror` at build time,
  plus a runtime check on reflective paths).
- Protocol design inspired by [WigAI](https://github.com/fabb/WigAI).
- Design docs: `docs/superpowers/specs/`.

## Repo layout

- [extension/](extension/): the Java controller extension (MCP server + Bitwig API bridge).
- [docs/](docs/): usage, API bridge reference, Bitwig API docs, design specs.
- [scripts/](scripts/): API index generation and other maintenance scripts.
- [plugin/](plugin/): the Wigout Studio Claude Code plugin (see above).

## Roadmap

1. **Foundation** - transport, track/device/clip listing & control ✅
2. **Construction** - create tracks, insert devices via curated name→UUID catalog ✅
3. **Composition** - write notes into clips (`Clip.setStep`) ✅
4. **Device/patch design** - verified device-parameter/chain edits, DirectParameter read+write with ground-truth verification ✅
5. **Fallbacks** - .bwproject editing, keyboard automation
6. **Audio Synthesis** - connect generative model for creating audio clips/samples based on hummed melodies & natural language descriptors.

## License

MIT: see [extension/LICENSE](extension/LICENSE)

## Credits
Early inspiration on HTTP communication protocol inspired by WigAI.
