package org.wigout.mcp.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateTrackTool.
 */
class CreateTrackToolTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = CreateTrackTool.specification(controller, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("create_track")
            .arguments(arguments)
            .build();
        return spec.callHandler().apply(exchange, request);
    }

    @Test
    void testSpecificationCreation() {
        McpServerFeatures.SyncToolSpecification spec = CreateTrackTool.specification(controller, structuredLogger);
        assertNotNull(spec);
        assertEquals("create_track", spec.tool().name());
        assertNotNull(spec.tool().description());
        assertNotNull(spec.tool().inputSchema());
    }

    @Test
    void testSuccessfulCreateWithDefaults() throws Exception {
        Map<String, Object> controllerResult = new LinkedHashMap<>();
        controllerResult.put("track_index", 2);
        controllerResult.put("track_name", "Inst 3");
        controllerResult.put("verified", true);
        when(controller.createTrack("instrument", -1, null)).thenReturn(controllerResult);

        McpSchema.CallToolResult result = call(Map.of("type", "instrument"));

        assertFalse(result.isError());
        JsonNode response = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
        assertEquals("success", response.get("status").asText());
        assertEquals(2, response.get("data").get("track_index").asInt());
        assertEquals("Inst 3", response.get("data").get("track_name").asText());
        assertTrue(response.get("data").get("verified").asBoolean());
    }

    @Test
    void testCreateWithPositionAndName() throws Exception {
        Map<String, Object> controllerResult = new LinkedHashMap<>();
        controllerResult.put("track_index", 0);
        controllerResult.put("track_name", "Lead");
        controllerResult.put("verified", true);
        when(controller.createTrack("audio", 0, "Lead")).thenReturn(controllerResult);

        McpSchema.CallToolResult result = call(Map.of("type", "audio", "position", 0, "name", "Lead"));

        assertFalse(result.isError());
        verify(controller).createTrack("audio", 0, "Lead");
    }

    @Test
    void testPositionAcceptsAnyNumberType() throws Exception {
        Map<String, Object> controllerResult = new LinkedHashMap<>();
        controllerResult.put("track_index", 5);
        controllerResult.put("track_name", "Audio 6");
        controllerResult.put("verified", true);
        when(controller.createTrack("audio", 5, null)).thenReturn(controllerResult);

        McpSchema.CallToolResult result = call(Map.of("type", "audio", "position", 5L));

        assertFalse(result.isError());
        verify(controller).createTrack("audio", 5, null);
    }

    @Test
    void testMissingTypeIsError() {
        McpSchema.CallToolResult result = call(Map.of());
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testInvalidTypeIsError() {
        McpSchema.CallToolResult result = call(Map.of("type", "midi"));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testInvalidPositionIsError() {
        McpSchema.CallToolResult result = call(Map.of("type", "audio", "position", -2));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }
}
