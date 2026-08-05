package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.common.validation.ParameterValidator;
import org.wigout.mcp.features.ClipSceneController;
import org.wigout.mcp.features.ClipSceneController.ClipLaunchResult;
import org.wigout.mcp.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool for launching clips by track name and clip index using unified error handling architecture.
 * Implements the launch_clip MCP command as specified in the API reference.
 */
public class ClipTool {

    private static final String TOOL_NAME = "launch_clip";

    /**
     * Creates the MCP tool specification for clip launching.
     *
     * @param clipSceneController The controller for clip/scene operations
     * @param logger The structured logger for operation logging
     * @return MCP tool specification
     */
    public static McpServerFeatures.SyncToolSpecification launchClipSpecification(ClipSceneController clipSceneController, StructuredLogger logger) {
        var schema = """
            {
              "type": "object",
              "properties": {
                "track_name": {
                  "type": "string",
                  "description": "Name of the track containing the clip (case-sensitive)"
                },
                "clip_index": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Zero-based index of the clip slot to launch"
                }
              },
              "required": ["track_name", "clip_index"]
            }""";

        var tool = McpSchema.Tool.builder()
            .name(TOOL_NAME)
            .description("Launch a specific clip in Bitwig by providing track name and clip slot index")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithErrorHandling(
                TOOL_NAME,
                logger,
                () -> {
                    // Parse and validate arguments
                    LaunchClipArguments args = parseArguments(req.arguments());

                    // Perform clip launch operation
                    ClipLaunchResult result = clipSceneController.launchClip(args.trackName(), args.clipIndex());

                    if (result.isSuccess()) {
                        return Map.of(
                            "action", "clip_launched",
                            "track_name", args.trackName(),
                            "clip_index", args.clipIndex(),
                            "message", result.getMessage()
                        );
                    } else {
                        throw new BitwigApiException(ErrorCode.OPERATION_FAILED, TOOL_NAME, result.getMessage());
                    }
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    /**
     * Parses the MCP tool arguments into a structured format.
     *
     * @param arguments Raw arguments map from MCP request
     * @return Parsed and validated LaunchClipArguments
     * @throws IllegalArgumentException if arguments are invalid
     */
    private static LaunchClipArguments parseArguments(Map<String, Object> arguments) {
        // Validate required parameters
        String trackName = ParameterValidator.validateRequiredString(arguments, "track_name", TOOL_NAME);
        trackName = ParameterValidator.validateNotEmpty(trackName, "track_name", TOOL_NAME);

        int clipIndex = ParameterValidator.validateRequiredInteger(arguments, "clip_index", TOOL_NAME);
        clipIndex = ParameterValidator.validateClipIndex(clipIndex, TOOL_NAME);

        return new LaunchClipArguments(trackName, clipIndex);
    }

    /**
     * Data record for validated launch clip arguments.
     *
     * @param trackName The name of the track (case-sensitive)
     * @param clipIndex The zero-based clip slot index
     */
    public record LaunchClipArguments(
        @JsonProperty("track_name") String trackName,
        @JsonProperty("clip_index") int clipIndex
    ) {}
}
