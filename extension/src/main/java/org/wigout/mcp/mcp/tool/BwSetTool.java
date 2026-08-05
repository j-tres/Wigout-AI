package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.McpErrorHandler;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ValueWriter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool setting any settable value at a bridge path, with bounded verify.
 */
public class BwSetTool {

    public static McpServerFeatures.SyncToolSpecification specification(
            PathResolver resolver, ValueWriter writer, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Bridge path of the settable value, e.g. 'tracks[0].volume' or 'transport.isMetronomeEnabled'"
                },
                "value": {
                  "description": "The value to set. boolean/string/number per the target's kind; ranged values take a number in [0,1] or {\\"raw\\": n} for raw units (e.g. BPM, dB); colors take {\\"red\\",\\"green\\",\\"blue\\"[,\\"alpha\\"]}."
                }
              },
              "required": ["path", "value"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("bw_set")
            .description("Set the value at a Bitwig bridge path (bw_describe shows which members are settable). Reports an honest 'verified' flag from polling the cached value; verified=false means unconfirmed, check with bw_get.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "bw_set",
                req.arguments(),
                logger,
                (arguments, operation) -> {
                    String path = BwGetTool.validatePath(arguments, operation);
                    if (!arguments.containsKey("value")) {
                        throw new IllegalArgumentException("Parameter 'value' is required");
                    }
                    return new PathAndValue(path, arguments.get("value"));
                },
                (params) -> {
                    PathResolver.Resolution resolution = resolver.resolve(params.path());
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("path", resolution.canonicalPath());
                    result.putAll(writer.write(resolution.target(), params.value()));
                    return result;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private record PathAndValue(String path, Object value) {}
}
