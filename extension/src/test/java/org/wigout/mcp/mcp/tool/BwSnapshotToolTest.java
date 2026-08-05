package org.wigout.mcp.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.SnapshotWalker;
import org.wigout.mcp.mcp.bridge.ValueReader;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BwSnapshotToolTest {

    @Mock private SnapshotWalker walker;
    @Mock private PathResolver resolver;
    @Mock private ValueReader reader;
    @Mock private StructuredLogger structuredLogger;
    @Mock private Logger baseLogger;
    @Mock private StructuredLogger.TimedOperation timedOperation;
    @Mock private McpSyncServerExchange exchange;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PathResolver.Resolution projectResolution;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-1");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);

        // Default project-name lookup used by every successful call's header.
        projectResolution = new PathResolver.Resolution(new Object(), "application.projectName");
        when(resolver.resolve("application.projectName")).thenReturn(projectResolution);
        when(reader.read(projectResolution.target())).thenReturn(Map.of("value", "MyProject"));
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec =
            BwSnapshotTool.specification(walker, resolver, reader, structuredLogger);
        return spec.callHandler().apply(exchange, McpSchema.CallToolRequest.builder()
            .name("bw_snapshot").arguments(arguments).build());
    }

    private JsonNode data(McpSchema.CallToolResult result) throws Exception {
        return objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
    }

    @Test
    void testBothPathAndPathsIsError() {
        McpSchema.CallToolResult result = call(Map.of("path", "tracks", "paths", List.of("transport.tempo")));
        assertTrue(result.isError());
        verifyNoInteractions(walker);
    }

    @Test
    void testNeitherPathNorPathsIsError() {
        McpSchema.CallToolResult result = call(Map.of());
        assertTrue(result.isError());
        verifyNoInteractions(walker);
    }

    @Test
    void testSubtreeHeaderCarriesProjectAndSeq() throws Exception {
        when(walker.subtree("tracks", 2)).thenReturn(Map.of("values", Map.of("tracks[0].name", "Drums")));

        McpSchema.CallToolResult result = call(Map.of("path", "tracks"));

        assertFalse(result.isError());
        JsonNode data = data(result);
        assertEquals("MyProject", data.get("project").asText());
        assertTrue(data.has("seq"));
        assertTrue(data.get("seq").asLong() >= 1);
        assertEquals("subtree", data.get("mode").asText());
        assertTrue(data.has("values"));
        verify(walker).subtree("tracks", 2);
    }

    @Test
    void testDepthDefaultsToTwoWhenOmitted() {
        when(walker.subtree("tracks", 2)).thenReturn(Map.of("values", Map.of()));

        call(Map.of("path", "tracks"));

        verify(walker).subtree("tracks", 2);
    }

    @Test
    void testDepthAtCapIsAccepted() {
        when(walker.subtree("tracks", SnapshotWalker.MAX_DEPTH)).thenReturn(Map.of("values", Map.of()));

        McpSchema.CallToolResult result = call(Map.of("path", "tracks", "depth", SnapshotWalker.MAX_DEPTH));

        assertFalse(result.isError());
        verify(walker).subtree("tracks", SnapshotWalker.MAX_DEPTH);
    }

    @Test
    void testDepthAboveCapIsRejected() {
        McpSchema.CallToolResult result = call(Map.of("path", "tracks", "depth", SnapshotWalker.MAX_DEPTH + 1));

        assertTrue(result.isError());
        verifyNoInteractions(walker);
    }

    @Test
    void testDepthBelowOneIsRejected() {
        McpSchema.CallToolResult result = call(Map.of("path", "tracks", "depth", 0));

        assertTrue(result.isError());
        verifyNoInteractions(walker);
    }

    @Test
    void testBatchModeDelegatesToWalkerBatch() throws Exception {
        List<Map<String, Object>> entries =
            List.of(Map.of("path", "transport.tempo", "value", Map.of("kind", "double")));
        when(walker.batch(List.of("transport.tempo"))).thenReturn(entries);

        McpSchema.CallToolResult result = call(Map.of("paths", List.of("transport.tempo")));

        assertFalse(result.isError());
        JsonNode data = data(result);
        assertEquals("batch", data.get("mode").asText());
        assertTrue(data.has("entries"));
        verify(walker).batch(List.of("transport.tempo"));
    }

    @Test
    void testProjectNameFallsBackToUnknownOnResolverFailure() throws Exception {
        when(resolver.resolve("application.projectName")).thenThrow(
            new BitwigApiException(ErrorCode.INVALID_PARAMETER, "resolvePath", "boom"));
        when(walker.subtree("tracks", 2)).thenReturn(Map.of("values", Map.of()));

        McpSchema.CallToolResult result = call(Map.of("path", "tracks"));

        assertFalse(result.isError());
        assertEquals("unknown", data(result).get("project").asText());
    }
}
