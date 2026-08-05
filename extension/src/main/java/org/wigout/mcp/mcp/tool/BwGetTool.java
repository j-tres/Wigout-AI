package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.McpErrorHandler;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ValueReader;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool reading any observable value at a bridge path. Values are
 * interest-marked in bulk at extension init (BridgeInterestMarker); a read
 * whose target was already marked there is a plain cache read. The
 * subscribe-on-demand path (result carries subscribed_now) is only a
 * settle/safety net for the rare target the init-time sweep didn't reach —
 * see {@link org.wigout.mcp.mcp.bridge.ValueReader}.
 */
public class BwGetTool {

    public static McpServerFeatures.SyncToolSpecification specification(
            PathResolver resolver, ValueReader reader, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Bridge path of the value to read, e.g. 'transport.tempo' or 'tracks[0].volume'"
                }
              },
              "required": ["path"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("bw_get")
            .description("Read the value at a Bitwig bridge path (see bw_describe for available paths). Values are interest-marked in bulk at extension startup; a result with subscribed_now=true means this read fell back to on-demand subscription (rare) and may still show the pre-subscription default — re-read to confirm. Banks and plain objects return summaries.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "bw_get",
                req.arguments(),
                logger,
                BwGetTool::validatePath,
                (path) -> {
                    PathResolver.Resolution resolution = resolver.resolve(path);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("path", resolution.canonicalPath());
                    result.putAll(reader.read(resolution.target()));
                    return result;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    static String validatePath(Map<String, Object> arguments, String operation) {
        Object pathObj = arguments.get("path");
        if (!(pathObj instanceof String path) || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'path' is required and must be a non-empty string");
        }
        return path.trim();
    }
}
