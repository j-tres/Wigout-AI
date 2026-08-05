package org.wigout.mcp.mcp.tool;

import com.bitwig.extension.controller.api.Transport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.bridge.PathResolver;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BwDescribeToolTest {

    @Mock private PathResolver resolver;
    @Mock private BridgeGraph graph;
    @Mock private Transport transport;
    @Mock private StructuredLogger structuredLogger;
    @Mock private Logger baseLogger;
    @Mock private StructuredLogger.TimedOperation timedOperation;
    @Mock private McpSyncServerExchange exchange;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-1");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec =
            BwDescribeTool.specification(resolver, graph, structuredLogger);
        return spec.callHandler().apply(exchange, McpSchema.CallToolRequest.builder()
            .name("bw_describe").arguments(arguments).build());
    }

    @Test
    void testNoPathListsRoots() throws Exception {
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("transport", transport);
        when(graph.roots()).thenReturn(roots);

        McpSchema.CallToolResult result = call(Map.of());

        assertFalse(result.isError());
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertTrue(data.get("roots").toString().contains("transport"));
    }

    @Test
    void testDescribePathListsMembersWithDocs() throws Exception {
        when(resolver.resolve("transport"))
            .thenReturn(new PathResolver.Resolution(transport, "transport"));

        McpSchema.CallToolResult result = call(Map.of("path", "transport"));

        assertFalse(result.isError());
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertEquals("transport", data.get("path").asText());
        // Transport.play() exists on the real interface (mock implements it).
        boolean foundPlay = false;
        for (JsonNode member : data.get("members")) {
            if ("play".equals(member.get("name").asText())) {
                foundPlay = true;
            }
        }
        assertTrue(foundPlay, "expected 'play' among transport members");
    }

    @Test
    void testDescribePathCategorizesValueReturningMembersByValueInterface() throws Exception {
        // categorize() now checks Value.class.isAssignableFrom(returnType)
        // instead of a return-type simple-name suffix heuristic —
        // Transport.isPlaying() returns SettableBooleanValue, a Value
        // subtype, and must categorize as "value".
        when(resolver.resolve("transport"))
            .thenReturn(new PathResolver.Resolution(transport, "transport"));

        McpSchema.CallToolResult result = call(Map.of("path", "transport"));

        assertFalse(result.isError());
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        String category = null;
        for (JsonNode member : data.get("members")) {
            if ("isPlaying".equals(member.get("name").asText())) {
                category = member.get("category").asText();
            }
        }
        assertEquals("value", category, "isPlaying() returns a Value subtype and should categorize as 'value'");
    }

    @Test
    void testDescribePathReportsApiInterfaceTypeName() throws Exception {
        when(resolver.resolve("transport"))
            .thenReturn(new PathResolver.Resolution(transport, "transport"));

        McpSchema.CallToolResult result = call(Map.of("path", "transport"));

        assertFalse(result.isError());
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertEquals("Transport", data.get("type").asText());
    }

    private JsonNode describeMembers(Object target, String path) throws Exception {
        when(resolver.resolve(path)).thenReturn(new PathResolver.Resolution(target, path));
        McpSchema.CallToolResult result = call(Map.of("path", path));
        assertFalse(result.isError());
        return objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text())
            .get("data").get("members");
    }

    private JsonNode memberNamed(JsonNode members, String name) {
        for (JsonNode m : members) {
            if (name.equals(m.get("name").asText())) {
                return m;
            }
        }
        return null;
    }

    @Test
    void testValueMembersCarryReadableFlag() throws Exception {
        var playing = mock(com.bitwig.extension.controller.api.SettableBooleanValue.class);
        var tempo = mock(com.bitwig.extension.controller.api.Parameter.class);
        when(transport.isPlaying()).thenReturn(playing);
        when(transport.tempo()).thenReturn(tempo);
        org.wigout.mcp.common.bridge.SubscribeSettle.registerMarked(playing); // tempo NOT marked

        JsonNode members = describeMembers(transport, "transport");
        assertTrue(memberNamed(members, "isPlaying").get("readable").asBoolean());
        assertFalse(memberNamed(members, "tempo").get("readable").asBoolean());
    }

    @Test
    void testClipGetsSyntheticNotesAndStepEntries() throws Exception {
        var clip = mock(com.bitwig.extension.controller.api.PinnableCursorClip.class);
        JsonNode members = describeMembers(clip, "cursorClip");

        JsonNode notes = memberNamed(members, "notes");
        assertNotNull(notes);
        assertTrue(notes.get("synthetic").asBoolean());
        assertEquals("value", notes.get("category").asText());
        assertTrue(notes.get("readable").asBoolean());

        JsonNode step = memberNamed(members, "step");
        assertNotNull(step);
        assertTrue(step.get("synthetic").asBoolean());
        assertEquals(3, step.get("params").asInt());
    }

    @Test
    void testDeviceGetsSyntheticDirectParametersEntry() throws Exception {
        var cursorDevice = mock(com.bitwig.extension.controller.api.CursorDevice.class);
        JsonNode members = describeMembers(cursorDevice, "cursorDevice");

        JsonNode directParameters = memberNamed(members, "directParameters");
        assertNotNull(directParameters);
        assertTrue(directParameters.get("synthetic").asBoolean());
        assertEquals("value", directParameters.get("category").asText());
        assertTrue(directParameters.get("readable").asBoolean());
    }

    @Test
    void testNonDeviceHasNoSyntheticDirectParametersEntry() throws Exception {
        JsonNode members = describeMembers(transport, "transport");
        assertNull(memberNamed(members, "directParameters"));
    }

    @Test
    void testPlainGetterCategorizedAsValueAndReadable() throws Exception {
        var noteStep = mock(com.bitwig.extension.controller.api.NoteStep.class);
        JsonNode members = describeMembers(noteStep, "cursorClip.step(0,0,60)");
        JsonNode velocity = memberNamed(members, "velocity");
        assertEquals("value", velocity.get("category").asText());
        assertTrue(velocity.get("readable").asBoolean());
    }

    // ---- Task 10 (Cycle 2): popup browser "items" synthetic entry ----

    @Test
    void testBrowserColumnGetsSyntheticItemsEntry() throws Exception {
        var column = mock(com.bitwig.extension.controller.api.BrowserFilterColumn.class);
        var bank = mock(com.bitwig.extension.controller.api.BrowserFilterItemBank.class);
        when(graph.itemBankForColumn(column)).thenReturn(bank);

        JsonNode members = describeMembers(column, "browser.deviceColumn");

        JsonNode items = memberNamed(members, "items");
        assertNotNull(items);
        assertTrue(items.get("synthetic").asBoolean());
        assertEquals("navigation", items.get("category").asText());
    }

    @Test
    void testNonBrowserColumnHasNoSyntheticItemsEntry() throws Exception {
        // graph.itemBankForColumn(transport) is unstubbed (returns null) —
        // the synthetic "items" entry must not appear for non-column targets.
        JsonNode members = describeMembers(transport, "transport");
        assertNull(memberNamed(members, "items"));
    }
}
