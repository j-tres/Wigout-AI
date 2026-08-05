package org.wigout.mcp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.common.data.ApiDocIndex;
import org.wigout.mcp.mcp.bridge.ReflectionUtil;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP resources exposing the generated Bitwig API map so agents can browse
 * the API like documentation without tool calls.
 */
public final class ApiMapResource {

    private ApiMapResource() {}

    /** bitwig://api/index — the full generated docs index (large JSON). */
    public static McpServerFeatures.SyncResourceSpecification apiIndexSpecification() {
        McpSchema.Resource resource = McpSchema.Resource.builder()
            .uri("bitwig://api/index")
            .name("bitwig-api-index")
            .description("Full Bitwig Controller API v25 docs index: every type and method with signatures, doc summaries, @since, and deprecation replacements. Use with the bw_describe/bw_get/bw_set/bw_call bridge tools.")
            .mimeType("application/json")
            .build();
        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) ->
            new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(
                request.uri(), "application/json", ApiDocIndex.load().rawJson()))));
    }

    /** bitwig://api/roots — the bridge's addressable root paths. */
    public static McpServerFeatures.SyncResourceSpecification rootsSpecification(BridgeGraph graph) {
        McpSchema.Resource resource = McpSchema.Resource.builder()
            .uri("bitwig://api/roots")
            .name("bitwig-bridge-roots")
            .description("Root paths addressable by the bw_* bridge tools, with their API types.")
            .mimeType("application/json")
            .build();
        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            List<Map<String, Object>> roots = new ArrayList<>();
            graph.roots().forEach((name, obj) -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("path", name);
                entry.put("type", ReflectionUtil.apiTypeName(obj));
                roots.add(entry);
            });
            try {
                String json = new ObjectMapper().writeValueAsString(roots);
                return new McpSchema.ReadResourceResult(List.of(new McpSchema.TextResourceContents(
                    request.uri(), "application/json", json)));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize roots: " + e.getMessage(), e);
            }
        });
    }
}
