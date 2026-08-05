package org.wigout.mcp.mcp.tool;

import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ValueReader;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BwGetToolTest {

    @Mock private PathResolver resolver;
    @Mock private ValueReader reader;
    @Mock private StructuredLogger structuredLogger;
    @Mock private Logger baseLogger;
    @Mock private StructuredLogger.TimedOperation timedOperation;
    @Mock private McpSyncServerExchange exchange;
    @Mock private SettableBooleanValue isPlaying;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-1");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = BwGetTool.specification(resolver, reader, structuredLogger);
        return spec.callHandler().apply(exchange, McpSchema.CallToolRequest.builder()
            .name("bw_get").arguments(arguments).build());
    }

    @Test
    void testGetReadsResolvedValue() throws Exception {
        when(resolver.resolve("transport.isPlaying"))
            .thenReturn(new PathResolver.Resolution(isPlaying, "transport.isPlaying"));
        when(reader.read(isPlaying)).thenReturn(Map.of("kind", "boolean", "value", true));

        McpSchema.CallToolResult result = call(Map.of("path", "transport.isPlaying"));

        assertFalse(result.isError());
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertEquals("transport.isPlaying", data.get("path").asText());
        assertEquals(true, data.get("value").asBoolean());
    }

    @Test
    void testMissingPathIsError() {
        McpSchema.CallToolResult result = call(Map.of());
        assertTrue(result.isError());
        verifyNoInteractions(resolver);
    }

    @Test
    void testResolverErrorPropagates() throws Exception {
        when(resolver.resolve("bad.path")).thenThrow(
            new BitwigApiException(ErrorCode.INVALID_PARAMETER, "resolvePath", "Unknown root 'bad'. Roots: transport"));

        McpSchema.CallToolResult result = call(Map.of("path", "bad.path"));

        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("Unknown root"));
    }
}
