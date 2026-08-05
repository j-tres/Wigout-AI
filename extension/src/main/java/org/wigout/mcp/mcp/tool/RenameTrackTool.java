package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.features.TrackConstructionController;
import org.wigout.mcp.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for renaming a track selected by index or name.
 */
public class RenameTrackTool {

    /**
     * Creates the "rename_track" tool specification.
     *
     * @param controller The TrackConstructionController for track operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "rename_track" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            TrackConstructionController controller, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Zero-based index of the track to rename (provide exactly one of track_index or track_name)",
                  "minimum": 0
                },
                "track_name": {
                  "type": "string",
                  "description": "Current name of the track to rename (provide exactly one of track_index or track_name)"
                },
                "new_name": {
                  "type": "string",
                  "description": "The new track name"
                }
              },
              "required": ["new_name"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("rename_track")
            .description("Rename a track, selected by track_index or track_name (exactly one). The result carries a 'verified' flag; if false, confirm with list_tracks.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "rename_track",
                req.arguments(),
                logger,
                RenameTrackTool::validateParameters,
                (params) -> controller.renameTrack(params.trackIndex(), params.trackName(), params.newName())
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        TrackSelector selector = TrackSelector.from(arguments);

        Object newNameObj = arguments.get("new_name");
        if (!(newNameObj instanceof String newName) || newName.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'new_name' is required and must be a non-empty string");
        }

        return new ValidatedParams(selector.trackIndex(), selector.trackName(), newName);
    }

    private record ValidatedParams(Integer trackIndex, String trackName, String newName) {}
}
