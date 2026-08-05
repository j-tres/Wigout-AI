package org.wigout.mcp.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.bitwig.BitwigApiFacade;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.McpErrorHandler;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GetTrackDetailsToolTest {

    @Mock
    private BitwigApiFacade bitwigApiFacade;
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
        when(structuredLogger.generateOperationId()).thenReturn("op-xyz");
        when(structuredLogger.startTimedOperation(any(), any(), any())).thenReturn(timedOperation);
    }

    @Test
    void testSpecificationBasics() {
        McpServerFeatures.SyncToolSpecification spec = GetTrackDetailsTool.specification(bitwigApiFacade, structuredLogger);
        assertNotNull(spec);
        assertEquals("get_track_details", spec.tool().name());
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testValidateParameters() {
        // None provided -> selected
        var vp1 = GetTrackDetailsTool.validateParameters(Map.of(), "get_track_details");
        // Provided index
        var vp2 = GetTrackDetailsTool.validateParameters(Map.of("track_index", 1), "get_track_details");
        // Provided name
        var vp3 = GetTrackDetailsTool.validateParameters(Map.of("track_name", "Drums"), "get_track_details");
        // Provided get_selected true
        var vp4 = GetTrackDetailsTool.validateParameters(Map.of("get_selected", true), "get_track_details");
        assertNotNull(vp1);
        assertNotNull(vp2);
        assertNotNull(vp3);
        assertNotNull(vp4);
    }

    @Test
    void testHandlerSuccessByIndex() throws Exception {
        Map<String, Object> mockDetails = new LinkedHashMap<>();
        mockDetails.put("name", "Drums");
        when(bitwigApiFacade.getTrackDetailsByIndex(0)).thenReturn(mockDetails);

        // Simulate final success response
        McpSchema.CallToolResult result = McpErrorHandler.createSuccessResponse(mockDetails);
        assertFalse(result.isError());

        JsonNode dataNode = new ObjectMapper().readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertEquals("Drums", dataNode.get("name").asText());
    }

    @Test
    void testErrorNoSelection() {
        // Simulate facade returning null for selected
        BitwigApiException ex = new BitwigApiException(ErrorCode.TRACK_NOT_FOUND, "get_track_details", "No track is currently selected");
        McpSchema.CallToolResult result = McpErrorHandler.createErrorResponse(ex, structuredLogger);
        assertTrue(result.isError());
    }

    @Test
    void testInvalidParamCombination() {
        assertThrows(IllegalArgumentException.class, () ->
            GetTrackDetailsTool.validateParameters(Map.of("track_index", 1, "track_name", "X"), "get_track_details")
        );
    }
}
