package org.wigout.mcp.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.wigout.mcp.common.data.ApiDocIndex;
import org.wigout.mcp.mcp.bridge.BridgeExclusions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task 13: the coverage report — the "entire API" gate.
 *
 * Walks the entire Bitwig API v25 surface, as captured by the generated docs
 * index ({@code bitwig-api-index.json}, loaded through {@link ApiDocIndex}),
 * and classifies every type name and every method into exactly one bucket:
 *
 * <ul>
 *   <li><b>reachable</b> — navigable/invocable by the bridge: a
 *       {@link #ROOT_TYPE_NAMES root} (mirrors {@code BridgeGraph} exactly),
 *       or discovered by closure from a root (rule 2).</li>
 *   <li><b>excluded</b> — deliberately out of scope, with a reason: either an
 *       automatic rule (rule 3, plus one extra auto-rule for deprecated
 *       members — see below), or a human-written reason in
 *       {@code bridge-coverage-exclusions.json} (rule 4).</li>
 *   <li><b>unexplained</b> — neither of the above: a gap that must be closed
 *       before the final assertion can be enabled. This is the whole point
 *       of the gate — it is not supposed to be quietly satisfiable.</li>
 * </ul>
 *
 * <p>Extra auto-rule beyond the task brief's six: a method annotated
 * {@code @Deprecated} is auto-excluded with reason "deprecated in API v25",
 * mirroring {@code PathResolver}'s runtime refusal of deprecated members
 * (navigate/navigateCall both refuse them; {@code ArgCoercer} never even
 * gets a chance to run). This is mechanically checkable
 * ({@code Method.isAnnotationPresent(Deprecated.class)}) and saves the
 * exclusions file from needing one manual entry per deprecated method on an
 * otherwise-reachable interface — deprecation is not a judgment call.</p>
 */
class BridgeCoverageReportTest {

    /**
     * Rule 1 roots — mirrors {@code BridgeGraph}'s root map, using the
     * ACTUAL runtime type where it differs from the field's static type
     * (BridgeGraph fields are sometimes typed to a common supertype; the
     * live object implements the more specific interface, and that's what
     * {@code ReflectionUtil} actually walks). Two such substitutions:
     * <ul>
     *   <li>{@code cursorDevice}: BridgeGraph's constructor parameter is
     *       typed {@code CursorDevice}, but the facade populates it via
     *       {@code cursorTrack.createCursorDevice()}, which returns
     *       {@code PinnableCursorDevice} (a {@code CursorDevice} subtype).
     *       Seeding the closure with the more specific type is a strict
     *       superset of seeding with {@code CursorDevice} — nothing is
     *       lost, and the pin-related members become correctly reachable.</li>
     *   <li>{@code cursorRemoteControls}/{@code projectRemoteControls}:
     *       BridgeGraph stores both in {@code RemoteControlsPage}-typed
     *       fields, but both are populated via
     *       {@code ...createCursorRemoteControlsPage(...)}, which returns
     *       the more specific {@code CursorRemoteControlsPage} — already
     *       named that way in the task brief's rule-1 list, so no
     *       substitution needed here, just noted for consistency with the
     *       cursorDevice case above.</li>
     * </ul>
     * Plus the extra edge the brief calls out explicitly:
     * {@code Device.getCursorSlot()} returns {@code DeviceSlot}. It's also
     * reachable by ordinary closure (Device is reached via
     * PinnableCursorDevice's superinterface chain), so this entry is
     * redundant-but-documented, matching the brief's instruction.
     */
    private static final List<String> ROOT_TYPE_NAMES = List.of(
        "Transport", "Application", "Project", "Arranger", "Mixer", "Groove",
        "MasterTrack", "PopupBrowser", "TrackBank", "SceneBank", "CursorTrack",
        "PinnableCursorDevice", "PinnableCursorClip", "CursorRemoteControlsPage",
        "DrumPadBank", "DeviceLayerBank", "DeviceBank", "NoteStep",
        "BrowserFilterItemBank", "BrowserResultsItemBank", "DeviceSlot");

    /** Rule 6 packages — {@code Class.forName} is tried against each, in order. */
    private static final List<String> API_PACKAGES = List.of(
        "com.bitwig.extension.controller.api",
        "com.bitwig.extension.api",
        "com.bitwig.extension.api.util.midi",
        "com.bitwig.extension.callback",
        "com.bitwig.extension.api.graphics",
        "com.bitwig.extension.api.opensoundcontrol",
        "com.bitwig.extension");

    private static final String REASON_CALLBACK =
        "callback parameter type — passed to observers, never addressed by path";
    private static final String REASON_DATA_TYPE =
        "data type — passed/returned by value";
    private static final String REASON_DEPRECATED =
        "deprecated in API v25 — refused by PathResolver/navigateCall at runtime, never invocable";

    /**
     * DirectParameter bridging (Cycle N): these five methods are called
     * directly by {@code BitwigApiFacade} at init to wire
     * {@code DirectParameterCache} — never through {@code bw_call}. Without
     * this override, {@link #isInvocable} would silently and WRONGLY mark
     * the four {@code addDirectParameter*} methods "invocable": their sole
     * callback-typed parameter (e.g. {@code StringArrayValueChangedCallback})
     * IS a {@code com.bitwig.extension} interface, so
     * {@link #isInvocableParam}'s generic "any Bitwig-interface parameter is
     * a path-resolvable object" fallback accepts it — but no bridge path
     * ever resolves to a callback instance, so {@code ArgCoercer} can never
     * actually supply one; the method is unreachable via {@code bw_call} in
     * practice despite the heuristic's false "invocable" verdict.
     * {@code setObservedParameterIds} is different: its only parameter
     * ({@code String[]}) genuinely IS {@code ArgCoercer}-coercible, but no
     * bridge path resolves to a {@code DirectParameterValueDisplayObserver}
     * instance to call it on (that type is returned only by the
     * callback-taking, therefore-excluded, {@code addDirectParameterValueDisplayObserver}).
     * Named explicitly here rather than folded into a generic
     * callback-parameter auto-rule (which would also reclassify every other
     * {@code addXxxObserver} method across the whole API) — this keeps the
     * fix scoped to what this cycle actually changed.
     */
    private static final Map<String, String> DIRECT_PARAMETER_OBSERVER_REASONS = Map.of(
        "Device#addDirectParameterIdObserver",
            "wired at init in BitwigApiFacade (feeds DirectParameterCache's ordered id list); not "
            + "bw_call-invocable — ArgCoercer can never construct a StringArrayValueChangedCallback "
            + "from a JSON literal or resolve one from a bridge path.",
        "Device#addDirectParameterNameObserver",
            "wired at init in BitwigApiFacade (feeds DirectParameterCache's id->name map); not "
            + "bw_call-invocable — same callback-parameter reason as addDirectParameterIdObserver.",
        "Device#addDirectParameterValueDisplayObserver",
            "NOT wired: registration alone is init-safe, but the returned handle is useless — see "
            + "setObservedParameterIds (live finding #45); not bw_call-invocable — same "
            + "callback-parameter reason as addDirectParameterIdObserver.",
        "Device#addDirectParameterNormalizedValueObserver",
            "wired at init in BitwigApiFacade (feeds DirectParameterCache's id->value map); not "
            + "bw_call-invocable — same callback-parameter reason as addDirectParameterIdObserver.",
        "DirectParameterValueDisplayObserver#setObservedParameterIds",
            "NOT called: HALTS the extension on Bitwig 6.1b4 in every invocation context tried "
            + "(inside the id-observer dispatch, deferred via scheduleTask, empty-array and "
            + "null-for-empty variants) — live finding #45; also not bw_call-invocable — no bridge "
            + "path resolves to a DirectParameterValueDisplayObserver instance."
    );

    @Test
    void entireApiSurfaceIsReachableOrExcludedWithReason() throws Exception {
        // Classpath sanity gate (per the task brief): if bitwig.jar's API
        // classes aren't on the test classpath, every resolve() call below
        // would silently return null and everything would look excluded or
        // unresolved for the wrong reason. Fail loudly and immediately
        // instead.
        Class.forName("com.bitwig.extension.controller.api.Transport");

        List<Class<?>> roots = new ArrayList<>();
        for (String name : ROOT_TYPE_NAMES) {
            Class<?> resolved = resolve(name);
            assertNotNull(resolved, "Root type '" + name
                + "' did not resolve via Class.forName over the rule-6 packages — "
                + "check BridgeGraph hasn't drifted from this test's root list.");
            roots.add(resolved);
        }

        Closure closure = computeClosure(roots);
        Map<String, String> manualExclusions = loadManualExclusions();

        Set<String> reachableTypeNames = new TreeSet<>();
        for (Class<?> c : closure.reachableTypes) {
            reachableTypeNames.add(c.getSimpleName());
        }

        Map<String, String> excluded = new TreeMap<>();
        List<String> unexplainedTypes = new ArrayList<>();
        classifyTypes(reachableTypeNames, manualExclusions, excluded, unexplainedTypes);

        List<String> unexplainedMethods = new ArrayList<>();
        classifyMethods(closure.candidateMethods, manualExclusions, excluded, unexplainedMethods);

        writeReport(reachableTypeNames, excluded, unexplainedTypes, unexplainedMethods);

        assertTrue(unexplainedTypes.isEmpty() && unexplainedMethods.isEmpty(),
            "Unexplained coverage gaps — every in-scope type/method must be reachable or excluded with a reason:\n"
            + String.join("\n", unexplainedTypes) + "\n" + String.join("\n", unexplainedMethods));
    }

    // ------------------------------------------------------------------
    // Type classification (rules 1, 2, 3a/3b/3c, 4, 6)
    // ------------------------------------------------------------------

    /** Every ApiDocIndex type name that isn't reachable gets exactly one bucket: excluded (with a reason) or unexplained. */
    private static void classifyTypes(Set<String> reachableTypeNames, Map<String, String> manualExclusions,
                                      Map<String, String> excluded, List<String> unexplainedTypes) {
        ApiDocIndex index = ApiDocIndex.load();
        for (String name : index.typeNames()) {
            if (reachableTypeNames.contains(name)) {
                continue; // rule 1/2: reachable — nothing to explain.
            }
            if (BridgeExclusions.isExcludedTypeName(name)) {
                excluded.put(name, BridgeExclusions.EXCLUSION_REASON); // rule 3: physical-controller subsystem.
                continue;
            }
            if (name.endsWith("Callback")) {
                excluded.put(name, REASON_CALLBACK); // rule 3: callback parameter type.
                continue;
            }
            Class<?> cls = resolve(name); // rule 6.
            if (cls != null && !cls.isInterface()) {
                excluded.put(name, REASON_DATA_TYPE); // rule 3: enum/class, not interface.
                continue;
            }
            // Either unresolvable (rule 6: "forces a look") or a genuine
            // interface that closure never reached — both land here, and
            // both need a human-written reason (rule 4) or count as
            // unexplained.
            String manual = manualExclusions.get(name);
            if (manual != null) {
                excluded.put(name, manual);
                continue;
            }
            unexplainedTypes.add(name);
        }
    }

    // ------------------------------------------------------------------
    // Method classification (rule 5, + the extra deprecated auto-rule)
    // ------------------------------------------------------------------

    private static void classifyMethods(Set<Method> candidateMethods, Map<String, String> manualExclusions,
                                        Map<String, String> excluded, List<String> unexplainedMethods) {
        for (Method m : candidateMethods) {
            String fullKey = methodSignature(m); // "TypeName#method(ParamType,...)" — for reports.
            String coarseKey = methodKey(m);      // "TypeName#method" — matches the exclusions file's format.

            if (m.isAnnotationPresent(Deprecated.class)) {
                excluded.put(fullKey, REASON_DEPRECATED);
                continue;
            }
            if (BridgeExclusions.isExcludedMethod(m)) {
                excluded.put(fullKey, BridgeExclusions.EXCLUSION_REASON);
                continue;
            }
            String directParameterReason = DIRECT_PARAMETER_OBSERVER_REASONS.get(coarseKey);
            if (directParameterReason != null) {
                excluded.put(fullKey, directParameterReason);
                continue;
            }
            if (isInvocable(m)) {
                continue; // reachable and invocable — nothing to explain.
            }
            String manual = manualExclusions.get(coarseKey);
            if (manual != null) {
                excluded.put(fullKey, manual);
                continue;
            }
            unexplainedMethods.add(fullKey);
        }
    }

    /**
     * Rule 5: every parameter must be something {@code ArgCoercer} can
     * actually produce from a JSON argument. Matches
     * {@code ArgCoercer.coerceOne}/{@code scoreOne} exactly, including the
     * "assignable pass-through" (its own javadoc: "abstract/widening
     * parameter types... e.g. java.lang.Number... plus Object and
     * CharSequence") — without that inclusion, {@code SettableRangedValue
     * .set(Number, Number)}/{@code .inc(Number, Number)} (reachable via
     * Parameter/Send/RemoteControl, all over the graph) would be false
     * gaps.
     */
    private static boolean isInvocable(Method m) {
        for (Class<?> paramType : m.getParameterTypes()) {
            if (!isInvocableParam(paramType)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInvocableParam(Class<?> t) {
        if (t.isPrimitive()) {
            // ArgCoercer.coerceOne only has conversion branches for these five.
            return t == int.class || t == long.class || t == double.class
                || t == float.class || t == boolean.class;
        }
        if (t == Integer.class || t == Long.class || t == Double.class
                || t == Float.class || t == Boolean.class) {
            return true;
        }
        if (t == String.class || t == UUID.class) {
            return true;
        }
        // ArgCoercer's assignable pass-through: any JSON-representable value
        // (Long/Double/Boolean/String) already assignable to the parameter.
        if (t == Number.class || t == Object.class || t == CharSequence.class) {
            return true;
        }
        if (t.isEnum()) {
            return true;
        }
        if (t.isArray()) {
            return isInvocableParam(t.getComponentType());
        }
        // API-object parameter, resolved from a bridge path string.
        return isBitwigInterface(t);
    }

    // ------------------------------------------------------------------
    // Closure (rule 2)
    // ------------------------------------------------------------------

    private record Closure(Set<Class<?>> reachableTypes, Set<Method> candidateMethods) {}

    /**
     * BFS from the roots. An interface becomes reachable if it's a root, a
     * (transitive) superinterface of a reachable interface, the actual type
     * argument of a reachable interface's parameterized supertype (the
     * "bank item type" mechanism — e.g. {@code DeviceLayerBank extends
     * ChannelBank<DeviceLayer>} makes {@code DeviceLayer} reachable even
     * though the only accessor with that covariant return
     * ({@code DeviceLayerBank#getChannel}) is deprecated), or the
     * non-deprecated non-excluded return type (zero-arg or arg-taking — a
     * call segment can navigate either) of a reachable interface's method.
     * {@code candidateMethods} collects every non-{@code Object} method
     * reachable this way, for rule-5 classification; it intentionally
     * includes deprecated/excluded methods too (classifyMethods sorts those
     * out) so the report accounts for every method encountered, not just
     * the ones that end up invocable.
     */
    private static Closure computeClosure(List<Class<?>> roots) {
        Set<Class<?>> reachable = new LinkedHashSet<>();
        Set<Method> candidateMethods = new LinkedHashSet<>();
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>(roots);

        while (!queue.isEmpty()) {
            Class<?> c = queue.poll();
            if (!visited.add(c)) {
                continue;
            }
            if (!c.isInterface() || BridgeExclusions.isExcludedTypeName(c.getSimpleName())
                    || c.getSimpleName().endsWith("Callback")) {
                // Dead end, matching rule 3's auto-exclusions: physical-controller
                // subsystem types (defensive — roots don't touch that subsystem,
                // so this shouldn't fire in practice) and callback types. Callback
                // types are reachable here via a generic-supertype quirk (e.g.
                // BooleanValue extends Value<BooleanValueChangedCallback> — the
                // "bank item type" mechanism in enqueueFromType doesn't
                // distinguish a covariant item-type parameter from an
                // observer-type parameter) — without this check they'd
                // incorrectly land in "reachable" instead of being excluded with
                // rule 3's callback reason, and their observer methods (called BY
                // the extension, never invoked BY the bridge) would incorrectly
                // count as bridge-invocable.
                continue;
            }
            reachable.add(c);

            // Direct superinterfaces + parameterized-supertype type args
            // ("bank item types"), both from getGenericInterfaces().
            for (Type t : c.getGenericInterfaces()) {
                enqueueFromType(t, queue, visited);
            }

            for (Method m : c.getMethods()) {
                if (m.getDeclaringClass() == Object.class) {
                    continue;
                }
                candidateMethods.add(m);
                if (m.isAnnotationPresent(Deprecated.class) || BridgeExclusions.isExcludedMethod(m)) {
                    continue; // doesn't propagate closure — matches PathResolver's runtime refusal.
                }
                enqueueFromType(m.getGenericReturnType(), queue, visited);
            }
        }
        return new Closure(reachable, candidateMethods);
    }

    /** Adds a com.bitwig.extension.* interface Class (or a ParameterizedType's raw type + actual type args) to the frontier. */
    private static void enqueueFromType(Type t, Deque<Class<?>> queue, Set<Class<?>> visited) {
        if (t instanceof Class<?> raw) {
            maybeEnqueue(raw, queue, visited);
        } else if (t instanceof ParameterizedType pt) {
            if (pt.getRawType() instanceof Class<?> raw) {
                maybeEnqueue(raw, queue, visited);
            }
            for (Type arg : pt.getActualTypeArguments()) {
                if (arg instanceof Class<?> argClass) {
                    maybeEnqueue(argClass, queue, visited);
                }
            }
        }
    }

    private static void maybeEnqueue(Class<?> c, Deque<Class<?>> queue, Set<Class<?>> visited) {
        if (isBitwigInterface(c) && !visited.contains(c)) {
            queue.add(c);
        }
    }

    private static boolean isBitwigInterface(Class<?> c) {
        return c.isInterface() && c.getPackageName().startsWith("com.bitwig.extension");
    }

    // ------------------------------------------------------------------
    // Resolution (rule 6) and exclusions loading
    // ------------------------------------------------------------------

    private static Class<?> resolve(String simpleName) {
        for (String pkg : API_PACKAGES) {
            try {
                return Class.forName(pkg + "." + simpleName);
            } catch (ClassNotFoundException ignored) {
                // try the next package
            }
        }
        return null;
    }

    private static Map<String, String> loadManualExclusions() throws IOException {
        try (InputStream in = BridgeCoverageReportTest.class.getResourceAsStream("/bridge-coverage-exclusions.json")) {
            if (in == null) {
                return Map.of();
            }
            JsonNode root = new ObjectMapper().readTree(in);
            Map<String, String> out = new LinkedHashMap<>();
            root.fields().forEachRemaining(e -> out.put(e.getKey(), e.getValue().asText()));
            return out;
        }
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    private static String methodKey(Method m) {
        return m.getDeclaringClass().getSimpleName() + "#" + m.getName();
    }

    private static String methodSignature(Method m) {
        String params = Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName)
            .collect(Collectors.joining(","));
        return methodKey(m) + "(" + params + ")";
    }

    private static void writeReport(Set<String> reachable, Map<String, String> excluded,
                                    List<String> unexplainedTypes, List<String> unexplainedMethods) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.set("reachable", mapper.valueToTree(reachable));
        root.set("excluded", mapper.valueToTree(excluded));
        root.set("unexplained_types", mapper.valueToTree(unexplainedTypes.stream().sorted().toList()));
        root.set("unexplained_methods", mapper.valueToTree(unexplainedMethods.stream().sorted().toList()));

        Path reportPath = Path.of("build", "reports", "bridge-coverage.json");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
    }
}
