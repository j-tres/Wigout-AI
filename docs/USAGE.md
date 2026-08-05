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

Claude Code:

```bash
claude mcp add --transport http bitwig http://localhost:61169/mcp
```

## Tests

```bash
cd extension && ./gradlew test
```
