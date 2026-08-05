package org.wigout.mcp.mcp;

import com.bitwig.extension.controller.api.Transport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.bitwig.BridgeGraph;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiMapResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testApiIndexResourceServesGeneratedJson() throws Exception {
        McpServerFeatures.SyncResourceSpecification spec = ApiMapResource.apiIndexSpecification();
        assertEquals("bitwig://api/index", spec.resource().uri());
        assertEquals("application/json", spec.resource().mimeType());

        McpSchema.ReadResourceResult result = spec.readHandler().apply(null,
            new McpSchema.ReadResourceRequest("bitwig://api/index"));

        McpSchema.TextResourceContents contents = (McpSchema.TextResourceContents) result.contents().get(0);
        JsonNode root = objectMapper.readTree(contents.text());
        assertTrue(root.path("types").has("Transport"));
    }

    @Test
    void testRootsResourceListsRootPaths() throws Exception {
        BridgeGraph graph = mock(BridgeGraph.class);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("transport", mock(Transport.class));
        when(graph.roots()).thenReturn(roots);

        McpServerFeatures.SyncResourceSpecification spec = ApiMapResource.rootsSpecification(graph);
        McpSchema.ReadResourceResult result = spec.readHandler().apply(null,
            new McpSchema.ReadResourceRequest("bitwig://api/roots"));

        McpSchema.TextResourceContents contents = (McpSchema.TextResourceContents) result.contents().get(0);
        JsonNode array = objectMapper.readTree(contents.text());
        assertTrue(array.isArray());
        assertEquals("transport", array.get(0).get("path").asText());
    }
}
