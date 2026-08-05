package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.data.DeviceCatalog;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.features.TrackConstructionController;
import org.wigout.mcp.mcp.McpErrorHandler;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * MCP tool for inserting a device at the end of a track's device chain — a
 * curated catalog device by name, any native device by UUID, or a .bwpreset
 * file by absolute path.
 */
public class InsertDeviceTool {

    /**
     * Creates the "insert_device" tool specification.
     *
     * @param controller The TrackConstructionController for insertion operations
     * @param logger The structured logger for logging operations
     * @return A SyncToolSpecification for the "insert_device" tool
     */
    public static McpServerFeatures.SyncToolSpecification specification(
            TrackConstructionController controller, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "track_index": {
                  "type": "integer",
                  "description": "Zero-based index of the target track (provide exactly one of track_index or track_name)",
                  "minimum": 0
                },
                "track_name": {
                  "type": "string",
                  "description": "Name of the target track (provide exactly one of track_index or track_name)"
                },
                "device_name": {
                  "type": "string",
                  "description": "Curated catalog device name (see list_device_catalog). Provide exactly one of device_name, device_uuid, or preset_path."
                },
                "device_uuid": {
                  "type": "string",
                  "description": "Raw Bitwig device UUID. Provide exactly one of device_name, device_uuid, or preset_path."
                },
                "preset_path": {
                  "type": "string",
                  "description": "Absolute path to an existing .bwpreset file. Provide exactly one of device_name, device_uuid, or preset_path."
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("insert_device")
            .description("Insert a device at the end of a track's device chain: a curated native device by name (see list_device_catalog), any native device by UUID, or a .bwpreset file by absolute path. The result reports the track's device list and a 'verified' flag; if false, the insert likely silently failed (e.g. unknown UUID) — confirm with list_devices_on_track.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "insert_device",
                req.arguments(),
                logger,
                InsertDeviceTool::validateParameters,
                (params) -> controller.insertDevice(params.trackIndex(), params.trackName(), params.deviceUuid(), params.presetPath())
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        TrackSelector selector = TrackSelector.from(arguments);

        String deviceName = optionalString(arguments, "device_name");
        String deviceUuid = optionalString(arguments, "device_uuid");
        String presetPath = optionalString(arguments, "preset_path");

        int selectorCount = (deviceName != null ? 1 : 0) + (deviceUuid != null ? 1 : 0) + (presetPath != null ? 1 : 0);
        if (selectorCount != 1) {
            throw new IllegalArgumentException("Provide exactly one of 'device_name', 'device_uuid', or 'preset_path'");
        }

        if (deviceName != null) {
            DeviceCatalog.Entry entry = DeviceCatalog.lookup(deviceName).orElseThrow(() ->
                new IllegalArgumentException("Unknown device_name '" + deviceName + "'. Available: " + DeviceCatalog.availableNames()));
            deviceUuid = entry.uuid();
        }

        if (deviceUuid != null) {
            try {
                UUID.fromString(deviceUuid);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Parameter 'device_uuid' is not a valid UUID: " + deviceUuid);
            }
        }

        if (presetPath != null) {
            Path path = Path.of(presetPath);
            if (!path.isAbsolute() || !Files.isRegularFile(path)
                    || !presetPath.toLowerCase(Locale.ROOT).endsWith(".bwpreset")) {
                throw new IllegalArgumentException("Parameter 'preset_path' must be an absolute path to an existing .bwpreset file, got: " + presetPath);
            }
        }

        return new ValidatedParams(selector.trackIndex(), selector.trackName(), deviceUuid, presetPath);
    }

    private static String optionalString(Map<String, Object> arguments, String key) {
        if (!arguments.containsKey(key)) {
            return null;
        }
        Object value = arguments.get(key);
        if (!(value instanceof String s) || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter '" + key + "' must be a non-empty string");
        }
        return s.trim();
    }

    private record ValidatedParams(Integer trackIndex, String trackName, String deviceUuid, String presetPath) {}
}
