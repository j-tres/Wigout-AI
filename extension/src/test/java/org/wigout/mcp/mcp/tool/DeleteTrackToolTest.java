package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.features.TrackConstructionController;
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

/**
 * Unit tests for DeleteTrackTool.
 */
class DeleteTrackToolTest {

    @Mock
    private TrackConstructionController controller;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;
    @Mock
    private McpSyncServerExchange exchange;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = DeleteTrackTool.specification(controller, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("delete_track")
            .arguments(arguments)
            .build();
        return spec.callHandler().apply(exchange, request);
    }

    @Test
    void testSpecificationCreation() {
        McpServerFeatures.SyncToolSpecification spec = DeleteTrackTool.specification(controller, structuredLogger);
        assertEquals("delete_track", spec.tool().name());
    }

    @Test
    void testDeleteByIndex() throws Exception {
        when(controller.deleteTrack(2, null)).thenReturn(Map.of("deleted_track", "Doomed", "verified", true));
        McpSchema.CallToolResult result = call(Map.of("track_index", 2));
        assertFalse(result.isError());
        verify(controller).deleteTrack(2, null);
    }

    @Test
    void testDeleteByName() throws Exception {
        when(controller.deleteTrack(null, "Doomed")).thenReturn(Map.of("deleted_track", "Doomed", "verified", true));
        McpSchema.CallToolResult result = call(Map.of("track_name", "Doomed"));
        assertFalse(result.isError());
        verify(controller).deleteTrack(null, "Doomed");
    }

    @Test
    void testBothSelectorsIsError() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 1, "track_name", "Doomed"));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testNeitherSelectorIsError() {
        McpSchema.CallToolResult result = call(Map.of());
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }
}
