package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.*;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.common.bridge.BridgeInterestMarker;
import org.wigout.mcp.common.bridge.SubscribeSettle;
import org.wigout.mcp.common.error.BitwigApiException;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Bulk reads for bw_snapshot. Subtree mode enumerates the READABLE surface
 * (guard-errors — see {@link SubscribeSettle#isMarkInterestedGuardError} —
 * and any other throwing member are skipped, not raised, mirroring
 * {@link BridgeInterestMarker}'s best-effort init-time sweep tolerance; init-
 * time marking defines what is readable); batch mode isolates errors per
 * path by delegating straight to {@link ValueReader#read} (same reporting as
 * bw_get). Deterministic member order (sorted by name, bank items ascending
 * index), bounded by entry cap + depth cap.
 */
public final class SnapshotWalker {

    public static final int MAX_ENTRIES = 10_000;
    public static final int MAX_DEPTH = 4;
    public static final int DEFAULT_DEPTH = 2;

    private final PathResolver resolver;
    private final BridgeGraph graph;
    private final ValueReader reader;
    private final int maxEntries;

    public SnapshotWalker(PathResolver resolver, BridgeGraph graph, ValueReader reader) {
        this(resolver, graph, reader, MAX_ENTRIES);
    }

    SnapshotWalker(PathResolver resolver, BridgeGraph graph, ValueReader reader, int maxEntries) { // test ctor
        this.resolver = resolver;
        this.graph = graph;
        this.reader = reader;
        this.maxEntries = maxEntries;
    }

    public Map<String, Object> subtree(String path, int depth) throws BitwigApiException {
        PathResolver.Resolution r = resolver.resolve(path);
        Map<String, Object> values = new LinkedHashMap<>();
        boolean truncated = walk(r.target(), r.canonicalPath(), Math.min(Math.max(depth, 1), MAX_DEPTH), values);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("values", values);
        if (truncated) {
            out.put("truncated", true);
            out.put("hint", "Entry cap " + maxEntries + " reached — narrow the path or lower depth.");
        }
        return out;
    }

    /** Batch mode: each path read exactly like bw_get, errors isolated per entry. */
    public List<Map<String, Object>> batch(List<String> paths) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String p : paths) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", p);
            try {
                PathResolver.Resolution r = resolver.resolve(p);
                entry.put("value", reader.read(r.target()));
            } catch (RuntimeException e) { // BitwigApiException is-a RuntimeException
                entry.put("error", e.getMessage());
            }
            entries.add(entry);
        }
        return entries;
    }

    /**
     * Deterministic subtree walk. Dispatch order matters (mirrors the
     * semantics list): entry cap, then Value (terminal emit), then
     * NoteStepCache (terminal emit), then Bank (free item iteration — no
     * depth cost, tried regardless of remaining depth), then plain object
     * (member navigation, which DOES cost one depth step and stops at 0).
     *
     * @return true if the entry cap was hit during this subtree (or a
     *         descendant), signalling the caller to mark the whole subtree
     *         result truncated.
     */
    private boolean walk(Object target, String prefix, int depth, Map<String, Object> values) {
        if (target == null) {
            return false;
        }
        if (values.size() >= maxEntries) {
            return true;
        }
        if (target instanceof Value<?> value) {
            return emitValue(value, prefix, values);
        }
        if (target instanceof NoteStepCache cache) {
            return emitNotes(cache, prefix, values);
        }
        if (target instanceof Bank<?> bank) {
            return walkBank(bank, prefix, depth, values);
        }
        if (depth == 0) {
            return false;
        }
        return walkObject(target, prefix, depth, values);
    }

    /** Bank items are free (no depth cost) — each is walked at the SAME depth, ascending index. */
    private boolean walkBank(Bank<?> bank, String prefix, int depth, Map<String, Object> values) {
        int size = safeSizeOfBank(bank);
        for (int i = 0; i < size; i++) {
            if (values.size() >= maxEntries) {
                return true;
            }
            Object item = safeGetItemAt(bank, i);
            if (item == null) {
                continue; // best-effort: an unreadable/absent window slot is skipped, not fatal
            }
            if (walk(item, prefix + "[" + i + "]", depth, values)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plain-object member navigation: each zero-arg, non-deprecated,
     * Value-or-Bank-or-ObjectProxy-returning member (sorted by name) costs
     * one depth step, plus the two synthesized edges (devices, notes) that
     * the reflective surface alone can't reach.
     */
    private boolean walkObject(Object target, String prefix, int depth, Map<String, Object> values) {
        List<Method> members = new ArrayList<>(ReflectionUtil.publicApiMethods(target));
        members.sort(Comparator.comparing(Method::getName));
        for (Method m : members) {
            if (m.getParameterCount() != 0 || m.getReturnType() == void.class) {
                continue;
            }
            if (m.isAnnotationPresent(Deprecated.class)) {
                continue;
            }
            Class<?> returnType = m.getReturnType();
            if (!Value.class.isAssignableFrom(returnType) && !ObjectProxy.class.isAssignableFrom(returnType)) {
                continue; // not a member this snapshot cares about
            }
            Object result;
            try {
                result = m.invoke(target);
            } catch (Exception e) {
                continue; // best-effort: a bad reflective call must not abort the walk
            }
            if (result == null) {
                continue; // unstubbed mock getter, or a genuinely absent live value
            }
            if (walk(result, prefix + "." + m.getName(), depth - 1, values)) {
                return true;
            }
        }
        return walkEdges(target, prefix, depth, values);
    }

    /**
     * Synthesized edges the reflective member surface can't reach: the
     * per-item DeviceBank behind "devices" (Track/DrumPad/DeviceLayer have
     * no zero-arg device-list getter — same special edge as PathResolver),
     * and the cursor clip's observer-fed note cache behind "notes".
     */
    private boolean walkEdges(Object target, String prefix, int depth, Map<String, Object> values) {
        if (target instanceof Track || target instanceof DrumPad || target instanceof DeviceLayer) {
            try {
                PathResolver.Resolution r = resolver.resolve(prefix + ".devices");
                if (walk(r.target(), r.canonicalPath(), depth - 1, values)) {
                    return true;
                }
            } catch (RuntimeException e) { // BitwigApiException is-a RuntimeException
                // 'devices' is only valid off a directly root-bank-indexed item — skip
            }
        }
        if (target instanceof Clip) {
            return walk(graph.noteStepCache(), prefix + ".notes", depth - 1, values);
        }
        return false;
    }

    /** Terminal emission of a leaf Value's compact encoding, guarded by the entry cap. */
    private boolean emitValue(Value<?> value, String prefix, Map<String, Object> values) {
        Object compact;
        try {
            compact = primary(value);
        } catch (RuntimeException e) {
            // Guard-error (SubscribeSettle.isMarkInterestedGuardError — the value lives
            // outside BridgeInterestMarker's depth-2 init sweep) is the expected cause;
            // any other throwing read gets the same best-effort skip rather than aborting
            // the whole subtree walk — subtree mode enumerates the READABLE surface.
            return false;
        }
        if (compact == null) {
            return false; // unrecognized Value subtype — nothing sensible to compact-encode
        }
        if (values.size() >= maxEntries) {
            return true;
        }
        values.put(prefix, compact);
        return false;
    }

    /** Terminal emission of the note-step cache's notes list, guarded by the entry cap. */
    private boolean emitNotes(NoteStepCache cache, String prefix, Map<String, Object> values) {
        List<Map<String, Object>> notes;
        try {
            notes = cache.all().stream().map(ValueReader::noteStepMap).toList();
        } catch (RuntimeException e) {
            // Same catch-skip contract as emitValue and the .devices edge: a throwing
            // cache read (e.g. a NoteStep getter) skips this entry, never the walk —
            // everything already collected stays in the result.
            return false;
        }
        if (values.size() >= maxEntries) {
            return true;
        }
        values.put(prefix, notes);
        return false;
    }

    /**
     * Compact value encoding — see class javadoc / task spec: most-specific
     * type first (Parameter before RangedValue, BeatTimeValue before
     * DoubleValue), matching {@link ValueReader}'s own dispatch order.
     */
    private static Object primary(Value<?> value) {
        if (value instanceof Parameter p) {
            return rangedMap(p.get(), p.displayedValue());
        }
        if (value instanceof RangedValue r) {
            return rangedMap(r.get(), r.displayedValue());
        }
        if (value instanceof BooleanValue v) {
            return v.get();
        }
        if (value instanceof StringArrayValue v) {
            String[] strings = v.get();
            return strings == null ? List.<String>of() : Arrays.asList(strings);
        }
        if (value instanceof StringValue v) {
            return v.get();
        }
        if (value instanceof EnumValue v) {
            return v.get();
        }
        if (value instanceof IntegerValue v) {
            return v.get();
        }
        if (value instanceof BeatTimeValue v) {
            return v.get();
        }
        if (value instanceof DoubleValue v) {
            return v.get();
        }
        if (value instanceof ColorValue v) {
            return List.of(v.red(), v.green(), v.blue(), v.alpha());
        }
        return null; // unrecognized Value subtype — caller skips the entry
    }

    private static Map<String, Object> rangedMap(double value, StringValue displayedValue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        try {
            m.put("displayed", displayedValue.get());
        } catch (RuntimeException e) {
            // displayed guard-errors independently of the base value — omit it, keep "value"
        }
        return m;
    }

    private static int safeSizeOfBank(Bank<?> bank) {
        try {
            return bank.getSizeOfBank();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Object safeGetItemAt(Bank<?> bank, int index) {
        try {
            return bank.getItemAt(index);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
