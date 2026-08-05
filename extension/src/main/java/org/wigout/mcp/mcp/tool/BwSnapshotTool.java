package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.McpErrorHandler;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.SnapshotWalker;
import org.wigout.mcp.mcp.bridge.ValueReader;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Bulk state reads: one call for a whole subtree (whole-mix snapshots) or a
 * batched list of paths with per-path error isolation. Every response names
 * the focused project — the controller follows project focus, which can
 * change between calls (live incident 2026-07-09).
 */
public class BwSnapshotTool {

    private static final AtomicLong SEQ = new AtomicLong();

    public static McpServerFeatures.SyncToolSpecification specification(
            SnapshotWalker walker, PathResolver resolver, ValueReader reader, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Subtree mode: dump all readable values under this bridge path (e.g. 'tracks')."
                },
                "paths": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Batch mode: read exactly these paths; individual failures are reported per entry."
                },
                "depth": {
                  "type": "integer",
                  "minimum": 1,
                  "maximum": 4,
                  "description": "Subtree recursion depth in navigation steps (default 2, max 4). Bank indexing is free."
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("bw_snapshot")
            .description("Bulk-read Bitwig state: subtree mode (path+depth) dumps every readable value under a path in one call — e.g. path='tracks' is a whole-mix snapshot; batch mode (paths[]) reads a list of paths with per-path error isolation. Response header carries the focused project name — check it, focus can change between calls.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "bw_snapshot",
                req.arguments(),
                logger,
                BwSnapshotTool::validate,
                (params) -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("project", projectName(resolver, reader));
                    result.put("seq", SEQ.incrementAndGet());
                    if (params.path() != null) {
                        result.put("mode", "subtree");
                        result.putAll(walker.subtree(params.path(), params.depth()));
                    } else {
                        result.put("mode", "batch");
                        result.put("entries", walker.batch(params.paths()));
                    }
                    return result;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool).callHandler(handler).build();
    }

    private static String projectName(PathResolver resolver, ValueReader reader) {
        try {
            Object v = reader.read(resolver.resolve("application.projectName").target()).get("value");
            return v instanceof String s && !s.isEmpty() ? s : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private record Params(String path, List<String> paths, int depth) {}

    private static Params validate(Map<String, Object> arguments, String operation) {
        Object pathObj = arguments.get("path");
        Object pathsObj = arguments.get("paths");
        if ((pathObj == null) == (pathsObj == null)) {
            throw new IllegalArgumentException("Provide exactly one of 'path' (subtree) or 'paths' (batch).");
        }
        int depth = SnapshotWalker.DEFAULT_DEPTH;
        Object depthObj = arguments.get("depth");
        if (depthObj != null) {
            if (!(depthObj instanceof Number n) || n.intValue() < 1 || n.intValue() > SnapshotWalker.MAX_DEPTH) {
                throw new IllegalArgumentException("'depth' must be an integer 1.." + SnapshotWalker.MAX_DEPTH);
            }
            depth = n.intValue();
        }
        if (pathObj != null) {
            if (!(pathObj instanceof String p) || p.trim().isEmpty()) {
                throw new IllegalArgumentException("'path' must be a non-empty string");
            }
            return new Params(p.trim(), null, depth);
        }
        if (!(pathsObj instanceof List<?> list) || list.isEmpty() || !list.stream().allMatch(x -> x instanceof String)) {
            throw new IllegalArgumentException("'paths' must be a non-empty array of strings");
        }
        List<String> paths = new ArrayList<>();
        list.forEach(x -> paths.add(((String) x).trim()));
        return new Params(null, paths, depth);
    }
}
