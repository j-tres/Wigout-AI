package org.wigout.mcp.mcp.tool;

import com.bitwig.extension.controller.api.SettableBooleanValue;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ValueWriter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BwSetToolTest {

    @Mock private PathResolver resolver;
    @Mock private ValueWriter writer;
    @Mock private StructuredLogger structuredLogger;
    @Mock private Logger baseLogger;
    @Mock private StructuredLogger.TimedOperation timedOperation;
    @Mock private McpSyncServerExchange exchange;
    @Mock private SettableBooleanValue metronome;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-1");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = BwSetTool.specification(resolver, writer, structuredLogger);
        return spec.callHandler().apply(exchange, McpSchema.CallToolRequest.builder()
            .name("bw_set").arguments(arguments).build());
    }

    @Test
    void testSetDelegatesToWriter() throws Exception {
        when(resolver.resolve("transport.isMetronomeEnabled"))
            .thenReturn(new PathResolver.Resolution(metronome, "transport.isMetronomeEnabled"));
        when(writer.write(metronome, Boolean.TRUE)).thenReturn(Map.of("verified", true));

        Map<String, Object> args = new HashMap<>();
        args.put("path", "transport.isMetronomeEnabled");
        args.put("value", Boolean.TRUE);
        McpSchema.CallToolResult result = call(args);

        assertFalse(result.isError());
        verify(writer).write(metronome, Boolean.TRUE);
    }

    @Test
    void testMissingValueIsError() {
        McpSchema.CallToolResult result = call(Map.of("path", "x"));
        assertTrue(result.isError());
        verifyNoInteractions(resolver, writer);
    }
}
