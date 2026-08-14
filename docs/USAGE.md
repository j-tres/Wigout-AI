# Usage

## Prerequisites

- Bitwig Studio 6 (API v25)
- JDK 21 (`C:\Program Files\Java\jdk-21.0.10`)

## Build & deploy

```bash
cd extension
export JAVA_HOME='C:/Program Files/Java/jdk-21.0.10'
./gradlew deploy    # builds Wigout.bwextension and copies it to Bitwig's Extensions folder
```

The deploy task resolves the OneDrive-redirected Documents folder automatically
(`%OneDrive%\Documents\Bitwig Studio\Extensions`).

## Activate in Bitwig

Dashboard → Settings → Controllers → Add Controller → vendor "MCP" → "Wigout AI".

## Connect an MCP client

The extension serves Streamable HTTP MCP at `http://localhost:61169/mcp` while Bitwig runs.

Claude Code CLI:

```bash
claude mcp add --transport http Wigout-MCP http://localhost:61169/mcp
```

Claude Desktop chat app: Settings → Developer → Edit Config, and add the
same entry to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "Wigout-MCP": { "type": "http", "url": "http://localhost:61169/mcp" }
  }
}
```

Don't use Settings → Connectors → "Add custom connector" for this — that
flow is Claude's account-level *remote*-connector system (the same one
backing claude.ai's Slack/Notion/Gmail connectors) and requires HTTPS. A
localhost-only bridge is never reachable through it, no matter the
certificate; `claude_desktop_config.json` is the local-settings path meant
for exactly this case, and takes plain `http://localhost`.

The `wigout-studio` plugin (see below) bundles this MCP server too, but
wires it over stdio (`plugin/scripts/mcp_stdio_bridge.py`) instead of raw
HTTP, so its one-click Marketplace install doesn't hit that same
Connectors/HTTPS gate.

## Tests

```bash
cd extension && ./gradlew test
```
