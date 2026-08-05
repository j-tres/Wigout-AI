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
 * Unit tests for RenameTrackTool.
 */
class RenameTrackToolTest {

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
        McpServerFeatures.SyncToolSpecification spec = RenameTrackTool.specification(controller, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("rename_track")
            .arguments(arguments)
            .build();
        return spec.callHandler().apply(exchange, request);
    }

    @Test
    void testSpecificationCreation() {
        McpServerFeatures.SyncToolSpecification spec = RenameTrackTool.specification(controller, structuredLogger);
        assertEquals("rename_track", spec.tool().name());
    }

    @Test
    void testRenameByIndex() throws Exception {
        when(controller.renameTrack(1, null, "New")).thenReturn(Map.of("verified", true));
        McpSchema.CallToolResult result = call(Map.of("track_index", 1, "new_name", "New"));
        assertFalse(result.isError());
        verify(controller).renameTrack(1, null, "New");
    }

    @Test
    void testRenameByName() throws Exception {
        when(controller.renameTrack(null, "Drums", "Percussion")).thenReturn(Map.of("verified", true));
        McpSchema.CallToolResult result = call(Map.of("track_name", "Drums", "new_name", "Percussion"));
        assertFalse(result.isError());
        verify(controller).renameTrack(null, "Drums", "Percussion");
    }

    @Test
    void testBothSelectorsIsError() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 1, "track_name", "Drums", "new_name", "X"));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testNeitherSelectorIsError() {
        McpSchema.CallToolResult result = call(Map.of("new_name", "X"));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testMissingNewNameIsError() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 1));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }
}
