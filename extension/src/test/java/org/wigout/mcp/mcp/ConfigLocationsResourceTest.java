package org.wigout.mcp.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.wigout.mcp.config.FileLocationsPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigLocationsResourceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void servesCurrentLocationsAsJson() throws Exception {
        FileLocationsPreferences mockPrefs = mock(FileLocationsPreferences.class);
        Map<String, String> locations = new LinkedHashMap<>();
        locations.put("projects", "C:/Music/Projects");
        when(mockPrefs.currentLocations()).thenReturn(locations);

        McpServerFeatures.SyncResourceSpecification spec =
            ConfigLocationsResource.configLocationsSpecification(mockPrefs);
        assertEquals("bitwig://config/locations", spec.resource().uri());
        assertEquals("application/json", spec.resource().mimeType());

        McpSchema.ReadResourceResult result = spec.readHandler().apply(null,
            new McpSchema.ReadResourceRequest("bitwig://config/locations"));

        McpSchema.TextResourceContents contents = (McpSchema.TextResourceContents) result.contents().get(0);
        JsonNode root = objectMapper.readTree(contents.text());
        assertEquals("C:/Music/Projects", root.get("projects").asText());
    }
}
