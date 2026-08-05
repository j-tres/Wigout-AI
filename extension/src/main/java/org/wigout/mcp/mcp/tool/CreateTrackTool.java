package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.features.TrackConstructionController;
import org.wigout.mcp.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for creating a new track (instrument, audio, or effect) at a
 * chosen position, optionally naming it.
 */
public class CreateTrackTool {

    private static final List<String> VALID_TYPES = List.of("instrument", "audio", "effect");

    /**
     * Creates the "create_track" tool specification.
     *
     * @param controller The TrackConstructionController for track operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "create_track" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            TrackConstructionController controller, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "type": {
                  "type": "string",
                  "description": "The type of track to create",
                  "enum": ["instrument", "audio", "effect"]
                },
                "position": {
                  "type": "integer",
                  "description": "Index in the track list to insert at; -1 (default) appends at the end. Out-of-range values are pinned by Bitwig.",
                  "minimum": -1
                },
                "name": {
                  "type": "string",
                  "description": "Optional name for the new track, applied right after creation"
                }
              },
              "required": ["type"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("create_track")
            .description("Create a new track (instrument, audio, or effect) at an optional position, optionally naming it. The result reports the new track's index and a 'verified' flag; if verified is false, confirm with list_tracks.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "create_track",
                req.arguments(),
                logger,
                CreateTrackTool::validateParameters,
                (params) -> controller.createTrack(params.type(), params.position(), params.name())
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        Object typeObj = arguments.get("type");
        if (!(typeObj instanceof String type) || type.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'type' is required and must be one of: " + String.join(", ", VALID_TYPES));
        }
        String normalizedType = type.toLowerCase().trim();
        if (!VALID_TYPES.contains(normalizedType)) {
            throw new IllegalArgumentException("Invalid track type '" + type + "'. Must be one of: " + String.join(", ", VALID_TYPES));
        }

        int position = -1;
        if (arguments.containsKey("position")) {
            Object positionObj = arguments.get("position");
            if (!(positionObj instanceof Number positionNum)) {
                throw new IllegalArgumentException("Parameter 'position' must be an integer");
            }
            int positionInt = positionNum.intValue();
            if (positionInt < -1) {
                throw new IllegalArgumentException("Parameter 'position' must be >= 0, or -1 to append at the end");
            }
            position = positionInt;
        }

        String name = null;
        if (arguments.containsKey("name")) {
            Object nameObj = arguments.get("name");
            if (!(nameObj instanceof String nameStr) || nameStr.trim().isEmpty()) {
                throw new IllegalArgumentException("Parameter 'name' must be a non-empty string when provided");
            }
            name = nameStr;
        }

        return new ValidatedParams(normalizedType, position, name);
    }

    private record ValidatedParams(String type, int position, String name) {}
}
