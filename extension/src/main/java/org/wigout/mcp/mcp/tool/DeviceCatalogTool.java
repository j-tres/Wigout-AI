package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.data.DeviceCatalog;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool listing the curated device catalog — the device names accepted by
 * insert_device's device_name argument.
 */
public class DeviceCatalogTool {

    /**
     * Creates the "list_device_catalog" tool specification.
     *
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "list_device_catalog" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("list_device_catalog")
            .description("List the curated native Bitwig devices insertable by name via insert_device (name, uuid, category, description).")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                "list_device_catalog",
                logger,
                () -> {
                    List<Map<String, Object>> entries = DeviceCatalog.entries().stream()
                        .<Map<String, Object>>map(entry -> Map.of(
                            "name", entry.name(),
                            "uuid", entry.uuid(),
                            "category", entry.category(),
                            "description", entry.description()))
                        .toList();
                    return entries;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }
}
