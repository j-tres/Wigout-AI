package org.wigout.mcp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.wigout.mcp.config.FileLocationsPreferences;

import java.util.List;

/**
 * MCP resource exposing the resolved per-user file locations
 * (~/.wigout-ai/config.json, mirrored into Bitwig Preferences) so agents
 * can discover projects/library/sound-content paths without tool calls.
 */
public class ConfigLocationsResource {

    /** bitwig://config/locations — resolved per-user file locations. */
    public static McpServerFeatures.SyncResourceSpecification configLocationsSpecification(
            FileLocationsPreferences fileLocationsPreferences) {
        McpSchema.Resource resource = McpSchema.Resource.builder()
            .uri("bitwig://config/locations")
            .name("wigout-config-locations")
            .description("Resolved per-user file locations (projects, library, sound content, etc.) from ~/.wigout-ai/config.json.")
            .mimeType("application/json")
            .build();
        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String json = new ObjectMapper().writeValueAsString(fileLocationsPreferences.currentLocations());
                return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(
                    request.uri(), "application/json", json)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize config locations: " + e.getMessage(), e);
            }
        });
    }
}
