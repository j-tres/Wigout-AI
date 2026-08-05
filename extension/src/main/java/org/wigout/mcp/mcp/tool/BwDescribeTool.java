package org.wigout.mcp.mcp.tool;

import com.bitwig.extension.controller.api.Value;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.common.data.ApiDocIndex;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.McpErrorHandler;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ReflectionUtil;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * MCP tool exposing the Bitwig API surface at a bridge path: members with
 * signatures, javadoc summaries, and deprecation info. With no path, lists
 * the bridge roots. This is how an agent explores the API like documentation.
 */
public class BwDescribeTool {

    public static McpServerFeatures.SyncToolSpecification specification(
            PathResolver resolver, BridgeGraph graph, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Bridge path to describe, e.g. 'transport' or 'tracks[0].volume'. Omit to list the root objects."
                }
              },
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("bw_describe")
            .description("Introspect the Bitwig API at a bridge path: lists members (navigation, values, callable methods) with signatures, doc summaries, and deprecation info. Without a path, lists the available roots. Companion to bw_get/bw_set/bw_call.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "bw_describe",
                req.arguments(),
                logger,
                (arguments, operation) -> {
                    Object pathObj = arguments.get("path");
                    if (pathObj != null && !(pathObj instanceof String)) {
                        throw new IllegalArgumentException("Parameter 'path' must be a string");
                    }
                    String path = pathObj == null ? null : ((String) pathObj).trim();
                    return (path == null || path.isEmpty()) ? null : path;
                },
                (path) -> {
                    if (path == null) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        List<Map<String, Object>> roots = new ArrayList<>();
                        graph.roots().forEach((name, obj) -> {
                            Map<String, Object> root = new LinkedHashMap<>();
                            root.put("path", name);
                            String type = apiTypeName(obj);
                            root.put("type", type);
                            ApiDocIndex.load().typeDoc(type).ifPresent(doc -> root.put("doc", doc));
                            roots.add(root);
                        });
                        result.put("roots", roots);
                        result.put("hint", "Address items with paths like tracks[0].devices[1].isEnabled; bw_describe any path for its members.");
                        return result;
                    }
                    PathResolver.Resolution resolution = resolver.resolve(path);
                    Object target = resolution.target();
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("path", resolution.canonicalPath());
                    String type = apiTypeName(target);
                    result.put("type", type);
                    ApiDocIndex.load().typeDoc(type).ifPresent(doc -> result.put("doc", doc));

                    List<Map<String, Object>> members = new ArrayList<>();
                    for (Method m : ReflectionUtil.publicApiMethods(target)) {
                        Map<String, Object> member = new LinkedHashMap<>();
                        member.put("name", m.getName());
                        member.put("returns", m.getReturnType().getSimpleName());
                        member.put("params", m.getParameterCount());
                        member.put("category", categorize(m));
                        ApiDocIndex.load().forMethod(m.getDeclaringClass().getSimpleName(), m.getName())
                            .ifPresent(doc -> {
                                if (!doc.doc().isEmpty()) {
                                    member.put("doc", doc.doc());
                                }
                                if (doc.deprecated()) {
                                    member.put("deprecated", true);
                                    if (doc.replacement() != null) {
                                        member.put("replacement", doc.replacement());
                                    }
                                }
                            });
                        if (m.isAnnotationPresent(Deprecated.class)) {
                            member.put("deprecated", true);
                        }
                        if ("value".equals(member.get("category"))) {
                            member.put("readable", readableFlag(target, m));
                        }
                        members.add(member);
                    }
                    if (target instanceof com.bitwig.extension.controller.api.Clip) {
                        members.add(syntheticMember("notes", "value",
                            "Bridge-synthesized: observer-fed note cache of the cursor clip. bw_get returns all notes.", 0));
                        members.add(syntheticMember("step", "navigation",
                            "Bridge-synthesized: step(channel, x, y) literal call segment resolving one NoteStep (cache-first).", 3));
                    }
                    if (target instanceof com.bitwig.extension.controller.api.Track
                            || target instanceof com.bitwig.extension.controller.api.DrumPad
                            || target instanceof com.bitwig.extension.controller.api.DeviceLayer) {
                        members.add(syntheticMember("devices", "navigation",
                            "Bridge-synthesized: the init-created device bank for this bank item (tracks[i]/drumPads[i]/layers[i] only).", 0));
                    }
                    if (target instanceof com.bitwig.extension.controller.api.Device) {
                        members.add(syntheticMember("directParameters", "value",
                            "Bridge-synthesized: observer-fed DirectParameter-by-ID cache — the only full-enumeration "
                            + "surface for devices with zero remote-control pages. bw_get returns all parameters "
                            + "(id/name/normalized value); no display strings (the display observer halts the "
                            + "extension on 6.1b4). Always reflects the cursor device (observers are cursor-anchored, "
                            + "not per-Device-instance).", 0));
                    }
                    if (graph.itemBankForColumn(target) != null) {
                        members.add(syntheticMember("items", "navigation",
                            "Bridge-synthesized: the init-created item bank of this browser column (filter: 16, results: 32).", 0));
                    }
                    members.sort((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")));
                    result.put("members", members);
                    return result;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private static String categorize(Method m) {
        if (m.getParameterCount() > 0 || m.getReturnType() == void.class) {
            return "callable";
        }
        if (Value.class.isAssignableFrom(m.getReturnType())) {
            return "value";
        }
        Class<?> rt = m.getReturnType();
        if (rt.isPrimitive() || rt == String.class || rt.isEnum()) {
            return "value"; // plain getter — readable without interest marking
        }
        return "navigation";
    }

    /**
     * Value-returning member: invoke the getter (pure accessor — the marker
     * already invokes all of these at init) and check the interest registry.
     * Plain getters (primitive/String/enum) are always readable.
     */
    private static boolean readableFlag(Object target, Method m) {
        Class<?> rt = m.getReturnType();
        if (rt.isPrimitive() || rt == String.class || rt.isEnum()) {
            return true;
        }
        try {
            Object value = m.invoke(target);
            return org.wigout.mcp.common.bridge.SubscribeSettle.isMarked(value);
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, Object> syntheticMember(String name, String category, String doc, int params) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("name", name);
        member.put("returns", "bridge");
        member.put("params", params);
        member.put("category", category);
        member.put("synthetic", true);
        member.put("doc", doc);
        if ("value".equals(category)) {
            member.put("readable", true);
        }
        return member;
    }

    private static String apiTypeName(Object target) {
        return ReflectionUtil.apiTypeName(target);
    }
}
