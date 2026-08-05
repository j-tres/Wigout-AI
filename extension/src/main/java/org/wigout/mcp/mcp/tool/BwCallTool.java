package org.wigout.mcp.mcp.tool;

import com.bitwig.extension.controller.api.Value;
import org.wigout.mcp.bitwig.BitwigApiFacade;
import org.wigout.mcp.common.data.ApiDocIndex;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.wigout.mcp.mcp.McpErrorHandler;
import org.wigout.mcp.mcp.bridge.ArgCoercer;
import org.wigout.mcp.mcp.bridge.BridgeExclusions;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ReflectionUtil;
import org.wigout.mcp.mcp.bridge.ValueReader;
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
 * MCP tool invoking any current, in-scope API method at a bridge path.
 * Deprecated methods are refused (a deprecated call halts the extension in
 * Bitwig 6.x) with the documented replacement; excluded-subsystem members are
 * refused with the exclusion reason.
 */
public class BwCallTool {

    /** DirectParameter write methods whose effect the value observer does not
     *  echo — the cache needs a post-write bounce refresh (see BitwigApiFacade). */
    private static final java.util.Set<String> DIRECT_PARAM_WRITE_METHODS =
        java.util.Set.of("setDirectParameterValueNormalized", "incDirectParameterValueNormalized");

    public static McpServerFeatures.SyncToolSpecification specification(
            PathResolver resolver, ArgCoercer coercer, ValueReader reader,
            BitwigApiFacade facade, StructuredLogger logger) {

        var schema = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Bridge path of the target object, e.g. 'transport' or 'tracks[0]'"
                },
                "method": {
                  "type": "string",
                  "description": "Method name to invoke (see bw_describe)"
                },
                "args": {
                  "type": "array",
                  "description": "Arguments: numbers/booleans/strings; UUIDs and enum names as strings; API-object parameters as bridge-path strings; arrays as JSON lists."
                }
              },
              "required": ["path", "method"],
              "additionalProperties": false
            }""";

        var tool = McpSchema.Tool.builder()
            .name("bw_call")
            .description("Invoke a Bitwig API method at a bridge path (bw_describe lists methods). Mutations are asynchronous: a void method returns status=invoked without confirmation — verify effects with bw_get. Deprecated methods are refused with their replacement named.")
            .inputSchema(schema)
            .build();

        BiFunction<McpSyncServerExchange, CallToolRequest, McpSchema.CallToolResult> handler =
            (exchange, req) -> McpErrorHandler.executeWithValidation(
                "bw_call",
                req.arguments(),
                logger,
                BwCallTool::validateParameters,
                (params) -> {
                    PathResolver.Resolution resolution = resolver.resolve(params.path());
                    Object target = resolution.target();

                    // commit/cancel on a closed browser: refuse with the recipe
                    // instead of a silent no-op. exists() is init-marked (browser
                    // is a root) so this cached read is legal and cheap.
                    if (target instanceof com.bitwig.extension.controller.api.PopupBrowser browser
                            && ("commit".equals(params.method()) || "cancel".equals(params.method()))
                            && !browser.exists().get()) {
                        throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "bw_call",
                            "No browser session is open (browser.exists is false). Open one first, e.g. "
                            + "bw_call tracks[0].endOfDeviceChainInsertionPoint browse — see the README browser recipe.");
                    }

                    List<Method> candidates = ReflectionUtil.findMethodsNamed(target, params.method());
                    // findMethodsNamed already filters excluded members; detect
                    // exclusion explicitly for a precise error via the same
                    // raw (unfiltered-by-exclusion) method lookup PathResolver
                    // uses, so exclusion detection can't diverge between the
                    // two call sites.
                    if (candidates.isEmpty()) {
                        boolean excluded = ReflectionUtil.findRawMethodsNamed(target, params.method()).stream()
                            .anyMatch(BridgeExclusions::isExcludedMethod);
                        if (excluded) {
                            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "bw_call",
                                "'" + params.method() + "' is " + BridgeExclusions.EXCLUSION_REASON);
                        }
                        throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "bw_call",
                            "No method '" + params.method() + "' at '" + resolution.canonicalPath()
                            + "'. Use bw_describe to list methods.");
                    }
                    // Deprecated overloads are refused (not silently skipped): if any
                    // non-deprecated overload exists, select among those; if only
                    // deprecated ones exist, refuse with the replacement hint.
                    List<Method> current = candidates.stream()
                        .filter(m -> !m.isAnnotationPresent(Deprecated.class)).toList();
                    if (current.isEmpty()) {
                        Method dep = candidates.get(0);
                        String replacement = ApiDocIndex.load()
                            .forMethod(dep.getDeclaringClass().getSimpleName(), dep.getName())
                            .map(ApiDocIndex.MethodDoc::replacement).orElse(null);
                        throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "bw_call",
                            "'" + params.method() + "' is deprecated in API v25 — calling it would halt the extension."
                            + (replacement != null ? " Replacement: " + replacement : ""));
                    }
                    ArgCoercer.Selection selection = coercer.selectAndCoerce(current, params.args(), "bw_call");
                    Method method = selection.method();
                    Object[] javaArgs = selection.args();
                    Object returned;
                    try {
                        returned = method.invoke(target, javaArgs);
                    } catch (Exception e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "bw_call",
                            "Invocation failed: " + cause.getMessage());
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("path", resolution.canonicalPath());
                    result.put("method", params.method());
                    if (method.getReturnType() == void.class) {
                        result.put("status", "invoked");
                        // Spec: never fabricated success — void invocations are
                        // unconfirmed by definition.
                        result.put("verified", false);
                        result.put("note", "Bitwig mutations are asynchronous — confirm effects with bw_get.");
                    } else if (returned instanceof Value<?> value) {
                        try {
                            result.put("result", reader.read(value));
                        } catch (BitwigApiException e) {
                            if (e.getErrorCode() != ErrorCode.OPERATION_FAILED) {
                                throw e;
                            }
                            // The invocation itself succeeded — only the
                            // read-back of its return value hit Bitwig's
                            // init-time interest guard (the returned value
                            // wasn't marked during extension startup). Report
                            // the successful invocation honestly instead of
                            // failing the whole call over an unreadable result.
                            result.put("status", "invoked");
                            result.put("read_note", "Invocation succeeded but the returned value could not be "
                                + "read back: " + e.getMessage());
                        }
                    } else if (returned == null || returned instanceof String || returned instanceof Number
                            || returned instanceof Boolean) {
                        result.put("result", returned);
                    } else {
                        result.put("result_type", returned.getClass().getSimpleName());
                        result.put("note", "Returned an API object — navigate to it with a bridge path instead.");
                    }

                    // Post-write refresh: a DirectParameter write on the cursor
                    // device is not echoed by the value observer, so bounce the
                    // cursor to pull the true value into the cache and report it
                    // here — turning the otherwise-unconfirmable void write into
                    // a confirmed one (finding #47).
                    if (DIRECT_PARAM_WRITE_METHODS.contains(params.method())
                            && target == facade.getCursorDevice()
                            && javaArgs.length >= 1 && javaArgs[0] instanceof String id) {
                        Double confirmed = facade.refreshDirectParametersAfterWrite(id);
                        if (confirmed != null) {
                            result.put("verified", true);
                            result.put("value", confirmed);
                            result.put("note", "DirectParameter cache refreshed via cursor bounce — "
                                + "'value' is the confirmed post-write normalized value.");
                        } else {
                            result.put("note", "DirectParameter write invoked, but the cache could not be "
                                + "refreshed (no sibling to bounce to, or it did not settle) — reselect the "
                                + "device and read cursorDevice.directParameters to confirm.");
                        }
                    }
                    return result;
                }
            );

        return McpServerFeatures.SyncToolSpecification.builder()
            .tool(tool)
            .callHandler(handler)
            .build();
    }

    private static ValidatedParams validateParameters(Map<String, Object> arguments, String operation) {
        String path = BwGetTool.validatePath(arguments, operation);
        Object methodObj = arguments.get("method");
        if (!(methodObj instanceof String method) || method.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter 'method' is required and must be a non-empty string");
        }
        List<Object> args = new ArrayList<>();
        if (arguments.containsKey("args")) {
            Object argsObj = arguments.get("args");
            if (!(argsObj instanceof List<?> list)) {
                throw new IllegalArgumentException("Parameter 'args' must be an array");
            }
            args.addAll(list);
        }
        return new ValidatedParams(path, method.trim(), args);
    }

    private record ValidatedParams(String path, String method, List<Object> args) {}
}
