package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.Bank;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceLayer;
import com.bitwig.extension.controller.api.DrumPad;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.Track;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.common.data.ApiDocIndex;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves bridge paths (e.g. "tracks[3].devices[1].volume") to live Bitwig
 * API objects. Navigation rules, in order per segment:
 *   1. first segment: BridgeGraph root name
 *   2. "[i]": target must be a Bank — getItemAt(i) with window bounds check
 *   3. special edge: "devices" on a Track resolved via the per-track
 *      DeviceBank registry (Track has no zero-arg device-list getter)
 *   4. plain name: zero-arg public interface method, invoked reflectively;
 *      deprecated members are refused (runtime @Deprecated is authoritative),
 *      excluded-subsystem members are refused
 *   5. "name(arg,…)": call segment — an overload is picked and coerced by
 *      ArgCoercer from literal arguments (int/decimal/bool/quoted-string);
 *      same deprecated/void-return refusals as plain-name navigation
 */
public final class PathResolver {

    /** A resolved path: the live object and the canonical path string. */
    public record Resolution(Object target, String canonicalPath) {}

    private static final Pattern SEGMENT =
        Pattern.compile("([A-Za-z][A-Za-z0-9]*)(?:\\((.*)\\))?((\\[\\d+\\])*)");
    private static final Pattern INDEX_SUFFIX = Pattern.compile("\\[(\\d+)\\]");

    private final BridgeGraph graph;
    private final ArgCoercer callArgCoercer;

    public PathResolver(BridgeGraph graph) {
        this.graph = graph;
        this.callArgCoercer = new ArgCoercer(this);
    }

    /** Splits on '.' only at paren depth 0 and outside quotes, so literals like ("FX.1") survive. */
    static List<String> splitSegments(String path) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int parens = 0;
        char quote = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < path.length()) { cur.append(path.charAt(++i)); }
                else if (c == quote) { quote = 0; }
                continue;
            }
            switch (c) {
                case '\'', '"' -> { quote = c; cur.append(c); }
                case '(' -> { parens++; cur.append(c); }
                case ')' -> { parens--; cur.append(c); }
                case '.' -> {
                    if (parens == 0) { out.add(cur.toString()); cur.setLength(0); }
                    else { cur.append(c); }
                }
                default -> cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /**
     * Parses "(a, b, c)" contents into Java literals: Long, Double, Boolean, String.
     * Two-phase so whitespace outside quotes never leaks into string values:
     * phase 1 splits on top-level commas keeping each raw token verbatim (no
     * unescaping yet); phase 2 trims and parses each token independently.
     */
    static List<Object> parseArgLiterals(String inside, String operation) throws BitwigApiException {
        List<Object> args = new ArrayList<>();
        if (inside.isBlank()) {
            return args;
        }
        for (String raw : splitTopLevelArgs(inside)) {
            args.add(parseOneLiteral(raw.trim(), operation));
        }
        return args;
    }

    /** Phase 1: splits on ',' outside quotes (escape-aware); tokens stay verbatim, quotes included. */
    private static List<String> splitTopLevelArgs(String inside) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < inside.length(); i++) {
            char c = inside.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && i + 1 < inside.length()) { cur.append(inside.charAt(++i)); }
                else if (c == quote) { quote = 0; }
            }
            else if (c == '\'' || c == '"') { quote = c; cur.append(c); }
            else if (c == ',') { parts.add(cur.toString()); cur.setLength(0); }
            else { cur.append(c); }
        }
        parts.add(cur.toString());
        return parts;
    }

    /** Phase 2: one trimmed raw token → Java literal. */
    private static Object parseOneLiteral(String t, String operation) throws BitwigApiException {
        if (!t.isEmpty() && (t.charAt(0) == '\'' || t.charAt(0) == '"')) {
            char quote = t.charAt(0);
            StringBuilder out = new StringBuilder();
            for (int i = 1; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '\\' && i + 1 < t.length()) { out.append(t.charAt(++i)); } // \' \" \\ unescape
                else if (c == quote) {
                    if (i != t.length() - 1) {
                        throw badLiteral(operation, t); // trailing junk after the close quote, e.g. "a"x
                    }
                    return out.toString(); // verbatim — spaces/dots/commas inside quotes survive
                }
                else { out.append(c); }
            }
            throw badLiteral(operation, t); // unterminated quote
        }
        if (t.equals("true") || t.equals("false")) { return Boolean.parseBoolean(t); }
        if (t.matches("-?\\d+")) { return Long.parseLong(t); }
        if (t.matches("-?\\d+\\.\\d+")) { return Double.parseDouble(t); }
        throw badLiteral(operation, t);
    }

    private static BitwigApiException badLiteral(String operation, String token) {
        return invalid(operation, "Bad call-segment literal '" + token
            + "'. Supported literals: integers, decimals, true/false, quoted strings.");
    }

    public Resolution resolve(String path) throws BitwigApiException {
        final String operation = "resolvePath";
        if (path == null || path.trim().isEmpty()) {
            throw invalid(operation, "Path must be non-empty. Roots: " + rootNames());
        }
        List<String> rawSegments = splitSegments(path.trim());
        Object current = null;
        StringBuilder canonical = new StringBuilder();
        String lastRootBank = null;
        int lastRootIndex = -1;

        for (int s = 0; s < rawSegments.size(); s++) {
            String rawSegment = rawSegments.get(s);
            Matcher m = SEGMENT.matcher(rawSegment);
            if (!m.matches()) {
                throw invalid(operation, "Malformed segment '" + rawSegment
                    + "'. Expected name or name[index], e.g. tracks[3].volume");
            }
            String name = m.group(1);
            String argsPart = m.group(2);
            String indexPart = m.group(3);

            if (s == 0) {
                if (argsPart != null) {
                    throw invalid(operation, "Root '" + name + "' cannot take call arguments.");
                }
                current = graph.rootOrNull(name);
                if (current == null) {
                    throw invalid(operation, "Unknown root '" + name + "'. Roots: " + rootNames());
                }
                canonical.append(name);
            } else if (argsPart != null) {
                List<Object> args = parseArgLiterals(argsPart, operation);
                current = navigateCall(current, name, args, canonical.toString(), operation);
                canonical.append('.').append(name).append('(').append(canonicalArgs(args)).append(')');
            } else {
                current = navigate(current, name, canonical.toString(), lastRootBank, lastRootIndex, operation);
                canonical.append('.').append(name);
            }
            if (("tracks".equals(name) || "drumPads".equals(name) || "layers".equals(name)) && indexPart.isEmpty()) {
                lastRootBank = null;
                lastRootIndex = -1;
            }

            // apply [i] suffixes in order
            Matcher idx = INDEX_SUFFIX.matcher(indexPart);
            while (idx.find()) {
                int i = Integer.parseInt(idx.group(1));
                current = indexInto(current, i, canonical.toString(), operation);
                canonical.append('[').append(i).append(']');
                if (canonical.toString().equals(name + "[" + i + "]")
                        && ("tracks".equals(name) || "drumPads".equals(name) || "layers".equals(name))) {
                    lastRootBank = name;
                    lastRootIndex = i;
                }
            }
        }
        return new Resolution(current, canonical.toString());
    }

    /** Renders coerced call-segment args back into canonical-path literal form: strings
     * double-quoted (with '\' and '"' escaped so the result re-parses), others via String.valueOf. */
    private static String canonicalArgs(List<Object> args) {
        return args.stream().map(a -> a instanceof String s
            ? '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"'
            : String.valueOf(a)).collect(Collectors.joining(","));
    }

    private Object navigate(Object current, String name, String prefix, String lastRootBank, int lastRootIndex,
                            String operation) throws BitwigApiException {
        // Synthesized edge: the cursor clip's note cache (observer-fed; the
        // API's own getStep needs args and NoteStep isn't a Value).
        if ("notes".equals(name) && current instanceof Clip) {
            return graph.noteStepCache();
        }

        // Synthesized edge: DirectParameter-by-ID cache (observer-fed; the
        // only full-enumeration surface for devices with zero remote-control
        // pages). The observers are registered on cursorDevice only (init-only
        // rule), so this always returns the SAME shared cache regardless of
        // which Device instance the path navigates through — it reflects
        // whichever device the cursor currently points at.
        if ("directParameters".equals(name) && current instanceof Device) {
            return graph.directParameterCache();
        }

        // Synthesized edge: init-created browser item banks (createItemBank is
        // init-only; columns have no zero-arg item accessor). Cheapest check
        // first — a null-safe identity-map lookup, so it costs nothing for
        // paths that aren't a browser column.
        if ("items".equals(name)) {
            Object itemBank = graph.itemBankForColumn(current);
            if (itemBank != null) {
                return itemBank;
            }
        }

        // Special edge: devices → the init-created per-track/pad/layer DeviceBank
        // (Track/DrumPad/DeviceLayer have no zero-arg device-list getter).
        if ("devices".equals(name)) {
            if (current instanceof Track && "tracks".equals(lastRootBank)) {
                return graph.deviceBankForTrack(lastRootIndex);
            }
            if (current instanceof DrumPad && "drumPads".equals(lastRootBank)) {
                return graph.deviceBankForDrumPad(lastRootIndex);
            }
            if (current instanceof DeviceLayer && "layers".equals(lastRootBank)) {
                return graph.deviceBankForLayer(lastRootIndex);
            }
            if (current instanceof Track || current instanceof DrumPad || current instanceof DeviceLayer) {
                throw invalid(operation, "'devices' requires a directly bank-indexed path like tracks[2].devices, "
                    + "drumPads[3].devices, or layers[0].devices.");
            }
        }

        Optional<Method> methodOpt = ReflectionUtil.findMethod(current, name, 0);
        if (methodOpt.isEmpty()) {
            // Distinguish excluded/deprecated-but-present from truly unknown.
            List<Method> anyArity = ReflectionUtil.findMethodsNamed(current, name);
            if (anyArity.isEmpty()) {
                // Check the unfiltered interface surface: a zero-arg member that
                // exists but was filtered by BridgeExclusions gets a specific,
                // "excluded" message instead of being reported as unknown.
                boolean excludedZeroArg = ReflectionUtil.findRawMethodsNamed(current, name).stream()
                    .anyMatch(m -> m.getParameterCount() == 0 && BridgeExclusions.isExcludedMethod(m));
                if (excludedZeroArg) {
                    throw invalid(operation, "'" + name + "' at '" + prefix + "' is "
                        + BridgeExclusions.EXCLUSION_REASON + " and cannot be navigated.");
                }
                throw invalid(operation, "Unknown member '" + name + "' at '" + prefix + "'. "
                    + "Navigable members: " + memberNames(current)
                    + ". Use bw_describe on '" + prefix + "' for full details.");
            }
            throw invalid(operation, "'" + name + "' at '" + prefix + "' takes arguments — use bw_call, not path navigation.");
        }
        Method method = methodOpt.get();
        if (method.isAnnotationPresent(Deprecated.class)) {
            String replacement = ApiDocIndex.load()
                .forMethod(method.getDeclaringClass().getSimpleName(), name)
                .map(ApiDocIndex.MethodDoc::replacement).orElse(null);
            throw invalid(operation, "'" + name + "' is deprecated in API v25 and calling it would halt the extension."
                + (replacement != null ? " Replacement: " + replacement : ""));
        }
        if (method.getReturnType() == void.class) {
            throw invalid(operation, "'" + name + "' returns nothing — use bw_call to invoke it.");
        }
        try {
            return method.invoke(current);
        } catch (Exception e) {
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Failed to navigate '" + name + "' at '" + prefix + "': " + e.getMessage());
        }
    }

    private Object navigateCall(Object current, String name, List<Object> args, String prefix,
                                String operation) throws BitwigApiException {
        // Synthesized edge: step(ch,x,y) — cache-first (live observer-fed
        // NoteStep), reflective getStep fallback for cells the observer
        // hasn't reported (reads back as state=Empty until written).
        if ("step".equals(name) && current instanceof Clip clip && args.size() == 3
                && args.stream().allMatch(a -> a instanceof Long)) {
            int ch = ((Long) args.get(0)).intValue();
            int x = ((Long) args.get(1)).intValue();
            int y = ((Long) args.get(2)).intValue();
            if (ch < 0 || ch > 15 || x < 0 || x >= graph.noteGridWidth()
                    || y < 0 || y >= graph.noteGridHeight()) {
                throw invalid(operation, "step(" + ch + "," + x + "," + y
                    + ") is outside the cursor-clip grid (channels 0..15, x 0.."
                    + (graph.noteGridWidth() - 1) + ", y 0.." + (graph.noteGridHeight() - 1) + ").");
            }
            Optional<NoteStep> cached = graph.noteStepCache().find(ch, x, y);
            if (cached.isPresent()) {
                return cached.get();
            }
            try {
                return clip.getStep(ch, x, y);
            } catch (RuntimeException e) {
                throw invalid(operation, "No note at step(" + ch + "," + x + "," + y
                    + ") and getStep failed: " + e.getMessage()
                    + ". cursorClip.notes lists existing notes.");
            }
        }

        List<Method> named = ReflectionUtil.findMethodsNamed(current, name);
        if (named.isEmpty()) {
            throw invalid(operation, "Unknown member '" + name + "' at '" + prefix
                + "'. Use bw_describe on '" + prefix + "'.");
        }
        List<Method> currentOnes = named.stream()
            .filter(m -> !m.isAnnotationPresent(Deprecated.class)).toList();
        if (currentOnes.isEmpty()) {
            Method dep = named.get(0);
            String replacement = ApiDocIndex.load()
                .forMethod(dep.getDeclaringClass().getSimpleName(), name)
                .map(ApiDocIndex.MethodDoc::replacement).orElse(null);
            throw invalid(operation, "'" + name + "' is deprecated in API v25 and calling it would halt the extension."
                + (replacement != null ? " Replacement: " + replacement : ""));
        }
        ArgCoercer.Selection sel = callArgCoercer.selectAndCoerce(currentOnes, args, operation);
        if (sel.method().getReturnType() == void.class) {
            throw invalid(operation, "'" + name + "' at '" + prefix
                + "' returns nothing (void) — a call segment must return an object to navigate. Use bw_call to invoke it.");
        }
        try {
            return sel.method().invoke(current, sel.args());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, operation,
                "Call segment '" + name + "' at '" + prefix + "' failed: " + cause.getMessage());
        }
    }

    private Object indexInto(Object current, int index, String prefix, String operation) throws BitwigApiException {
        if (!(current instanceof Bank<?> bank)) {
            throw invalid(operation, "'" + prefix + "' is not indexable (not a Bank).");
        }
        int size = bank.getSizeOfBank();
        if (index < 0 || index >= size) {
            throw invalid(operation, "Index " + index + " is outside the bank window (size " + size
                + ") at '" + prefix + "'. Use bw_call with scrollIntoView/scrollBy on '" + prefix
                + "' to move the window, or use an index < " + size + ".");
        }
        return bank.getItemAt(index);
    }

    private String rootNames() {
        return graph.roots().keySet().stream().collect(Collectors.joining(", "));
    }

    private String memberNames(Object target) {
        List<String> names = new ArrayList<>();
        for (Method m : ReflectionUtil.publicApiMethods(target)) {
            if (m.getParameterCount() == 0 && m.getReturnType() != void.class
                && !m.isAnnotationPresent(Deprecated.class)) {
                names.add(m.getName());
            }
        }
        names.sort(String::compareTo);
        String joined = String.join(", ", names.subList(0, Math.min(names.size(), 25)));
        return names.size() > 25 ? joined + ", … (" + names.size() + " total)" : joined;
    }

    private static BitwigApiException invalid(String operation, String message) {
        return new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation, message);
    }
}
