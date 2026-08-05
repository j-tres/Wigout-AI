package org.wigout.mcp.mcp.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.logging.StructuredLogger;
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
import static org.mockito.Mockito.when;

/**
 * Unit tests for DeviceCatalogTool.
 */
class DeviceCatalogToolTest {

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

    @Test
    void testListCatalogReturnsEntries() throws Exception {
        McpServerFeatures.SyncToolSpecification spec = DeviceCatalogTool.specification(structuredLogger);
        assertEquals("list_device_catalog", spec.tool().name());

        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("list_device_catalog")
            .arguments(Map.of())
            .build();
        McpSchema.CallToolResult result = spec.callHandler().apply(exchange, request);

        assertFalse(result.isError());
        JsonNode response = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text());
        assertEquals("success", response.get("status").asText());
        assertTrue(response.get("data").isArray());
        assertTrue(response.get("data").size() >= 2);

        boolean foundPolymer = false;
        for (JsonNode entry : response.get("data")) {
            assertNotNull(entry.get("name"));
            assertNotNull(entry.get("uuid"));
            assertNotNull(entry.get("category"));
            assertNotNull(entry.get("description"));
            if ("Polymer".equals(entry.get("name").asText())) {
                foundPolymer = true;
                assertEquals("8f58138b-03aa-4e9d-83bd-a038c99a4ed5", entry.get("uuid").asText());
            }
        }
        assertTrue(foundPolymer);
    }
}
