import io
import json
import urllib.error

import mcp_stdio_bridge as bridge


def test_extract_session_id_finds_header_case_insensitively():
    headers = {"Content-Type": "application/json", "mcp-session-id": "abc123"}

    assert bridge.extract_session_id(headers) == "abc123"


def test_extract_session_id_returns_none_when_absent():
    headers = {"Content-Type": "application/json"}

    assert bridge.extract_session_id(headers) is None


def test_parse_sse_body_extracts_single_data_event():
    body = 'event: message\ndata: {"jsonrpc": "2.0", "id": 1, "result": {}}\n\n'

    assert bridge.parse_sse_body(body) == [{"jsonrpc": "2.0", "id": 1, "result": {}}]


def test_parse_sse_body_extracts_multiple_data_events():
    body = (
        'data: {"jsonrpc": "2.0", "method": "notifications/progress"}\n\n'
        'data: {"jsonrpc": "2.0", "id": 1, "result": {}}\n\n'
    )

    assert bridge.parse_sse_body(body) == [
        {"jsonrpc": "2.0", "method": "notifications/progress"},
        {"jsonrpc": "2.0", "id": 1, "result": {}},
    ]


def test_parse_response_body_decodes_single_json_object():
    body = b'{"jsonrpc": "2.0", "id": 1, "result": {}}'

    messages = bridge.parse_response_body("application/json", body)

    assert messages == [{"jsonrpc": "2.0", "id": 1, "result": {}}]


def test_parse_response_body_ignores_charset_suffix():
    body = b'{"jsonrpc": "2.0", "id": 1, "result": {}}'

    messages = bridge.parse_response_body("application/json; charset=utf-8", body)

    assert messages == [{"jsonrpc": "2.0", "id": 1, "result": {}}]


def test_parse_response_body_decodes_event_stream():
    body = b'data: {"jsonrpc": "2.0", "id": 1, "result": {}}\n\n'

    messages = bridge.parse_response_body("text/event-stream", body)

    assert messages == [{"jsonrpc": "2.0", "id": 1, "result": {}}]


def test_parse_response_body_returns_empty_list_for_empty_body():
    assert bridge.parse_response_body("application/json", b"") == []


class FakeResponse:
    def __init__(self, body=b"", headers=None):
        self.body = body
        self.headers = headers or {}

    def read(self):
        return self.body

    def __enter__(self):
        return self

    def __exit__(self, *exc_info):
        return False


def test_forward_message_posts_json_with_streamable_http_accept_header():
    captured = {}

    def fake_opener(request):
        captured["request"] = request
        return FakeResponse(
            body=b'{"jsonrpc": "2.0", "id": 1, "result": {}}',
            headers={"Content-Type": "application/json"},
        )

    bridge.forward_message(
        {"jsonrpc": "2.0", "id": 1, "method": "initialize"},
        session_id=None,
        url="http://localhost:61169/mcp",
        opener=fake_opener,
    )

    request = captured["request"]
    assert request.full_url == "http://localhost:61169/mcp"
    assert request.get_header("Content-type") == "application/json"
    assert request.get_header("Accept") == "application/json, text/event-stream"
    assert json.loads(request.data) == {"jsonrpc": "2.0", "id": 1, "method": "initialize"}


def test_forward_message_includes_session_header_when_present():
    captured = {}

    def fake_opener(request):
        captured["request"] = request
        return FakeResponse(body=b"{}", headers={"Content-Type": "application/json"})

    bridge.forward_message(
        {"jsonrpc": "2.0", "id": 1}, session_id="abc123", url="http://x/mcp", opener=fake_opener
    )

    assert captured["request"].get_header("Mcp-session-id") == "abc123"


def test_forward_message_omits_session_header_when_absent():
    captured = {}

    def fake_opener(request):
        captured["request"] = request
        return FakeResponse(body=b"{}", headers={"Content-Type": "application/json"})

    bridge.forward_message(
        {"jsonrpc": "2.0", "id": 1}, session_id=None, url="http://x/mcp", opener=fake_opener
    )

    assert captured["request"].get_header("Mcp-session-id") is None


def test_forward_message_returns_parsed_messages_and_new_session_id():
    def fake_opener(request):
        return FakeResponse(
            body=b'{"jsonrpc": "2.0", "id": 1, "result": {}}',
            headers={"Content-Type": "application/json", "Mcp-Session-Id": "new-session"},
        )

    messages, session_id = bridge.forward_message(
        {"jsonrpc": "2.0", "id": 1}, session_id=None, url="http://x/mcp", opener=fake_opener
    )

    assert messages == [{"jsonrpc": "2.0", "id": 1, "result": {}}]
    assert session_id == "new-session"


def test_forward_message_keeps_existing_session_id_when_response_has_none():
    def fake_opener(request):
        return FakeResponse(body=b"{}", headers={"Content-Type": "application/json"})

    _, session_id = bridge.forward_message(
        {"jsonrpc": "2.0", "id": 1}, session_id="still-here", url="http://x/mcp", opener=fake_opener
    )

    assert session_id == "still-here"


def test_run_forwards_one_line_and_writes_result_to_stdout():
    def fake_opener(request):
        return FakeResponse(
            body=b'{"jsonrpc": "2.0", "id": 1, "result": {"ok": true}}',
            headers={"Content-Type": "application/json"},
        )

    instream = io.StringIO('{"jsonrpc": "2.0", "id": 1, "method": "initialize"}\n')
    outstream = io.StringIO()
    errstream = io.StringIO()

    bridge.run(instream, outstream, errstream, url="http://x/mcp", opener=fake_opener)

    assert json.loads(outstream.getvalue().strip()) == {
        "jsonrpc": "2.0",
        "id": 1,
        "result": {"ok": True},
    }


def test_run_propagates_session_id_from_first_response_into_second_request():
    captured_session_ids = []

    def fake_opener(request):
        captured_session_ids.append(request.get_header("Mcp-session-id"))
        if len(captured_session_ids) == 1:
            return FakeResponse(
                body=b'{"jsonrpc": "2.0", "id": 1, "result": {}}',
                headers={"Content-Type": "application/json", "Mcp-Session-Id": "sess-1"},
            )
        return FakeResponse(body=b'{"jsonrpc": "2.0", "id": 2, "result": {}}', headers={"Content-Type": "application/json"})

    instream = io.StringIO(
        '{"jsonrpc": "2.0", "id": 1, "method": "initialize"}\n'
        '{"jsonrpc": "2.0", "id": 2, "method": "tools/list"}\n'
    )

    bridge.run(instream, io.StringIO(), io.StringIO(), url="http://x/mcp", opener=fake_opener)

    assert captured_session_ids == [None, "sess-1"]


def test_run_writes_json_rpc_error_when_server_unreachable_for_a_request():
    def failing_opener(request):
        raise urllib.error.URLError("connection refused")

    instream = io.StringIO('{"jsonrpc": "2.0", "id": 1, "method": "initialize"}\n')
    outstream = io.StringIO()

    bridge.run(instream, outstream, io.StringIO(), url="http://x/mcp", opener=failing_opener)

    response = json.loads(outstream.getvalue().strip())
    assert response["id"] == 1
    assert "error" in response


def test_run_writes_nothing_to_stdout_when_server_unreachable_for_a_notification():
    def failing_opener(request):
        raise urllib.error.URLError("connection refused")

    instream = io.StringIO('{"jsonrpc": "2.0", "method": "notifications/initialized"}\n')
    outstream = io.StringIO()

    bridge.run(instream, outstream, io.StringIO(), url="http://x/mcp", opener=failing_opener)

    assert outstream.getvalue() == ""


def test_run_skips_blank_lines():
    instream = io.StringIO("\n\n")
    outstream = io.StringIO()

    bridge.run(instream, outstream, io.StringIO(), url="http://x/mcp", opener=lambda r: FakeResponse())

    assert outstream.getvalue() == ""


def test_run_logs_and_skips_invalid_json_lines():
    instream = io.StringIO("not json\n")
    outstream = io.StringIO()
    errstream = io.StringIO()

    bridge.run(instream, outstream, errstream, url="http://x/mcp", opener=lambda r: FakeResponse())

    assert outstream.getvalue() == ""
    assert "invalid" in errstream.getvalue().lower()


def test_build_url_from_port():
    assert bridge.build_url(61169) == "http://localhost:61169/mcp"
    assert bridge.build_url(12345) == "http://localhost:12345/mcp"


def test_main_defaults_to_wizard_mcp_port(monkeypatch):
    import wizard as wz

    captured = {}
    monkeypatch.setattr(bridge, "run", lambda instream, outstream, errstream, url, opener=None: captured.update(url=url))
    monkeypatch.setattr("sys.stdin", io.StringIO(""))

    bridge.main([])

    assert captured["url"] == f"http://localhost:{wz.MCP_PORT_DEFAULT}/mcp"


def test_main_honors_port_override(monkeypatch):
    captured = {}
    monkeypatch.setattr(bridge, "run", lambda instream, outstream, errstream, url, opener=None: captured.update(url=url))
    monkeypatch.setattr("sys.stdin", io.StringIO(""))

    bridge.main(["--port", "9999"])

    assert captured["url"] == "http://localhost:9999/mcp"
