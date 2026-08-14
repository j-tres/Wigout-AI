"""Stdio-to-Streamable-HTTP bridge for the Wigout MCP server.

Claude Desktop's plugin-installed connectors route any "type": "http"
.mcp.json entry through its account-level, HTTPS-only remote-connector
flow -- the wrong path for a server that only ever runs on localhost.
Local stdio servers sidestep that entirely: Claude spawns this script
as a subprocess and speaks MCP over stdin/stdout, and this script
forwards each message to the extension's existing local Streamable
HTTP endpoint (unchanged, still plain http://localhost).

Only valid MCP JSON-RPC lines go to stdout -- everything else (logs,
diagnostics) goes to stderr, per the stdio transport's framing rules.
"""
import argparse
import json
import sys
from urllib.request import Request, urlopen

from wizard import MCP_PORT_DEFAULT

SESSION_ID_HEADER = "Mcp-Session-Id"


def extract_session_id(headers):
    for name, value in headers.items():
        if name.lower() == SESSION_ID_HEADER.lower():
            return value
    return None


def parse_sse_body(body):
    """Extract each `data:` event's JSON payload from an SSE stream body."""
    messages = []
    for line in body.splitlines():
        if line.startswith("data:"):
            messages.append(json.loads(line[len("data:"):].strip()))
    return messages


def parse_response_body(content_type, body):
    """Decode a Streamable HTTP response body into a list of JSON-RPC messages.

    The server responds with either a single JSON object (`application/json`)
    or an SSE stream of one or more `data:` events (`text/event-stream`); a
    202 Accepted for a notification has no body at all.
    """
    if not body:
        return []
    mime = content_type.split(";", 1)[0].strip().lower()
    if mime == "text/event-stream":
        return parse_sse_body(body.decode("utf-8"))
    return [json.loads(body.decode("utf-8"))]


def forward_message(message, session_id, url, opener=urlopen):
    """POST one JSON-RPC message to the Streamable HTTP endpoint.

    Returns (messages, session_id): the JSON-RPC messages found in the
    response, and the session id to use for the next call (the response's
    Mcp-Session-Id if it sent one, otherwise the one passed in unchanged).
    """
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
    }
    if session_id is not None:
        headers[SESSION_ID_HEADER] = session_id

    request = Request(url, data=json.dumps(message).encode("utf-8"), headers=headers, method="POST")
    with opener(request) as response:
        body = response.read()
        content_type = response.headers.get("Content-Type", "application/json")
        messages = parse_response_body(content_type, body)
        new_session_id = extract_session_id(response.headers) or session_id
        return messages, new_session_id


def run(instream, outstream, errstream, url, opener=urlopen):
    """Read one JSON-RPC message per line from `instream`, forward each to
    the Streamable HTTP endpoint at `url`, and write any resulting JSON-RPC
    messages back to `outstream` -- one per line, matching the stdio
    transport's framing. All logging goes to `errstream`; stdout carries
    only protocol messages.
    """
    session_id = None
    for line in instream:
        line = line.strip()
        if not line:
            continue

        try:
            message = json.loads(line)
        except json.JSONDecodeError as exc:
            print(f"mcp_stdio_bridge: invalid JSON from client: {exc}", file=errstream)
            continue

        try:
            messages, session_id = forward_message(message, session_id, url, opener=opener)
        except OSError as exc:
            print(f"mcp_stdio_bridge: request to {url} failed: {exc}", file=errstream)
            if "id" in message:
                error_response = {
                    "jsonrpc": "2.0",
                    "id": message["id"],
                    "error": {
                        "code": -32000,
                        "message": f"Wigout MCP server unreachable at {url}: {exc}",
                    },
                }
                print(json.dumps(error_response), file=outstream)
                outstream.flush()
            continue

        for reply in messages:
            print(json.dumps(reply), file=outstream)
        outstream.flush()


def build_url(port):
    return f"http://localhost:{port}/mcp"


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=MCP_PORT_DEFAULT, help="Wigout MCP server port")
    args = parser.parse_args(argv)
    run(sys.stdin, sys.stdout, sys.stderr, url=build_url(args.port))


if __name__ == "__main__":
    main()
