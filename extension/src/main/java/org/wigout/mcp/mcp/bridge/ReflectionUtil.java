package org.wigout.mcp.mcp.bridge;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection helpers for the bridge. Methods are always resolved from PUBLIC
 * API INTERFACES (walking getInterfaces() recursively) — Bitwig implementation
 * classes are not public and invoking their Method objects throws
 * IllegalAccessException. Object's own methods and excluded members are
 * filtered out.
 *
 * Task 5a fix pass: the interface walk (collectPublicApiInterfaces) and its
 * downstream filtering used to be redone from scratch, with fresh
 * allocations, on EVERY call. At BridgeInterestMarker's init-time scale
 * (tens of thousands of marked objects sharing a small number of distinct
 * RUNTIME classes — e.g. every Device across a 128-track × 128-device-bank-
 * slot sweep is very likely the same implementation class) that repeated
 * identical work thousands of times, and the interface-traversal fix (fix
 * pass 3) made each walk visit more nodes on top of that. The resolved
 * method list for a given runtime Class never changes, so results are
 * memoized per Class (ConcurrentHashMap — reads/writes can come from the
 * controller thread during init and, later, from Jetty request threads
 * during bw_get/bw_set/bw_describe/bw_call).
 */
public final class ReflectionUtil {

    private static final Map<Class<?>, List<Method>> PUBLIC_API_METHODS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, List<Method>> RAW_METHODS_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> API_TYPE_NAME_CACHE = new ConcurrentHashMap<>();

    private ReflectionUtil() {}

    /**
     * All invocable public API methods of the target, collected from its
     * implemented interfaces (recursively), deduplicated by name+paramCount.
     * Excluded-subsystem members are filtered out. Memoized per runtime
     * class — the returned list is unmodifiable and shared across calls for
     * the same class; callers must not rely on it being a fresh instance.
     */
    public static List<Method> publicApiMethods(Object target) {
        return PUBLIC_API_METHODS_CACHE.computeIfAbsent(target.getClass(), ReflectionUtil::computePublicApiMethods);
    }

    private static List<Method> computePublicApiMethods(Class<?> cls) {
        List<Method> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> iface : collectPublicApiInterfaces(cls)) {
            for (Method m : iface.getMethods()) {
                if (m.getDeclaringClass() == Object.class) {
                    continue;
                }
                if (BridgeExclusions.isExcludedMethod(m)) {
                    continue;
                }
                if (seen.add(m.getName() + "/" + m.getParameterCount())) {
                    result.add(m);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** Finds a non-excluded interface method by name and argument count. */
    public static Optional<Method> findMethod(Object target, String name, int argCount) {
        return publicApiMethods(target).stream()
            .filter(m -> m.getName().equals(name) && m.getParameterCount() == argCount)
            .findFirst();
    }

    /** All non-excluded interface methods with the given name (any arity). */
    public static List<Method> findMethodsNamed(Object target, String name) {
        return publicApiMethods(target).stream()
            .filter(m -> m.getName().equals(name))
            .toList();
    }

    /**
     * All interface methods with the given name (any arity), WITHOUT the
     * excluded-subsystem filter. Used only to tell "member exists but is
     * excluded" apart from "member truly doesn't exist" for error messages —
     * never for invocation. The underlying (unfiltered-by-name) per-class
     * method list is memoized the same way as publicApiMethods; only the
     * cheap by-name filter/dedup is redone per call.
     */
    public static List<Method> findRawMethodsNamed(Object target, String name) {
        List<Method> all = RAW_METHODS_CACHE.computeIfAbsent(target.getClass(), ReflectionUtil::computeRawMethods);
        List<Method> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Method m : all) {
            if (!m.getName().equals(name)) {
                continue;
            }
            if (seen.add(m.getName() + "/" + m.getParameterCount())) {
                result.add(m);
            }
        }
        return result;
    }

    private static List<Method> computeRawMethods(Class<?> cls) {
        List<Method> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> iface : collectPublicApiInterfaces(cls)) {
            for (Method m : iface.getMethods()) {
                if (m.getDeclaringClass() == Object.class) {
                    continue;
                }
                if (seen.add(m.getName() + "/" + m.getParameterCount())) {
                    result.add(m);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * All public com.bitwig.extension.* interfaces reachable from cls's
     * class hierarchy, traversing THROUGH internal/obfuscated interfaces as
     * pass-through nodes rather than pruning at them.
     *
     * Live-gate finding: real Bitwig implementation classes for some proxy
     * types (observed on Parameter, e.g. transport.tempo()) implement ONLY
     * an internal, obfuscated interface that itself EXTENDS the public API
     * interface (e.g. an internal iface extends Parameter) — the public
     * interface is reachable ONLY via the internal one's own
     * superinterfaces. Pruning the walk at non-com.bitwig.extension
     * interfaces (as an earlier version of this method did) loses Parameter
     * entirely for such targets: publicApiMethods returned almost nothing,
     * breaking both PathResolver navigation (tempo.value/.displayedValue/
     * .name/.modulatedValue all "unknown member") and
     * BridgeInterestMarker's depth-1 sweep (those same members never got
     * marked). Transport/Track happen to implement their public interfaces
     * directly, which is why they were unaffected.
     *
     * So: traverse ALL interfaces transitively regardless of package —
     * internal interfaces contribute no methods (never accepted) but their
     * own superinterfaces are still walked, until a public API interface is
     * reached and accepted. An accepted interface's own superinterfaces
     * (API or JDK, e.g. BooleanSupplier) get walked too — Class.getMethods()
     * on an accepted interface already includes its full supertype chain,
     * so those extra visits are redundant-but-harmless (deduped by the
     * caller's seen-by-name/paramCount set), not required for correctness.
     */
    private static Set<Class<?>> collectPublicApiInterfaces(Class<?> cls) {
        Deque<Class<?>> queue = new ArrayDeque<>();
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            queue.addAll(Arrays.asList(current.getInterfaces()));
        }
        // If the target itself IS an interface class (mocks), include it too.
        if (cls.isInterface()) {
            queue.add(cls);
        }
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> accepted = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            Class<?> iface = queue.poll();
            if (!visited.add(iface)) {
                continue;
            }
            if (isPublicApiInterface(iface)) {
                accepted.add(iface);
            }
            queue.addAll(Arrays.asList(iface.getInterfaces()));
        }
        return accepted;
    }

    private static boolean isPublicApiInterface(Class<?> iface) {
        return iface.getPackageName().startsWith("com.bitwig.extension");
    }

    /**
     * Derives a human-readable API type name by walking the target's
     * implemented interfaces breadth-first and picking the first one that
     * belongs to the com.bitwig.extension API — never the raw, non-public
     * implementation class name. Falls back to the target's simple class
     * name if no such interface is found. Memoized per runtime class — this
     * doesn't share collectPublicApiInterfaces's walk (it stops at the
     * first accepted interface rather than collecting all of them), so it
     * gets its own small cache.
     *
     * Already traverses THROUGH non-public (internal/obfuscated) interfaces
     * as pass-through nodes, same principle as collectPublicApiInterfaces:
     * the queue is seeded with ALL directly-implemented interfaces
     * regardless of package, and an interface only returns immediately if
     * IT is public — otherwise its own superinterfaces are still enqueued
     * before moving on. So a proxy reachable only via e.g. an internal
     * interface that extends Parameter still names "Parameter" (BFS order
     * means the shallowest public interface found is returned).
     */
    public static String apiTypeName(Object target) {
        return API_TYPE_NAME_CACHE.computeIfAbsent(target.getClass(), ReflectionUtil::computeApiTypeName);
    }

    private static String computeApiTypeName(Class<?> cls) {
        Deque<Class<?>> queue = new ArrayDeque<>();
        for (Class<?> current = cls; current != null; current = current.getSuperclass()) {
            queue.addAll(Arrays.asList(current.getInterfaces()));
        }
        Set<Class<?>> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            Class<?> iface = queue.poll();
            if (!visited.add(iface)) {
                continue;
            }
            if (iface.getPackageName().startsWith("com.bitwig.extension")) {
                return iface.getSimpleName();
            }
            queue.addAll(Arrays.asList(iface.getInterfaces()));
        }
        return cls.getSimpleName();
    }
}
