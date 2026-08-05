package org.wigout.mcp.common.bridge;

import com.bitwig.extension.controller.api.Bank;
import com.bitwig.extension.controller.api.Value;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.mcp.bridge.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Bulk, init-time interest marking for the bridge's readable surface (Task
 * 5a / plan amendment A1, user-approved 2026-07-09).
 *
 * Live-gate finding: Bitwig 6.1b2 enforces markInterested()/
 * addValueObserver() as INIT-ONLY — SubscribeSettle's post-init registration
 * (round 1: direct calls on whatever thread the bridge tool call landed on;
 * round 2: routed through host.scheduleTask onto the controller thread) is
 * silently ignored either way — no exception, no log line. Value.get() is
 * gated on init-time interest, not subscription ("By default a driver is
 * subscribed to everything" — Subscribable javadoc). So instead of relying
 * on lazy registration at first read, this reflectively bulk-marks the
 * readable surface ONCE, synchronously, inside BitwigApiFacade's constructor
 * — the only time marking actually works — bounded to depth 2 from each
 * root/bank-item so init stays fast and the sweep doesn't explode into
 * unrelated subsystems. SubscribeSettle's scheduleTask-routed runtime path
 * remains wired as a safety net for anything this bounded sweep doesn't
 * reach, but is no longer the primary mechanism.
 *
 * Depth 2 rationale: sibling proxies have SEPARATE interest state — marking
 * a Parameter (e.g. track.volume()) does not unlock its own nested
 * value()/modulatedValue() proxies, and vice versa; live evidence: bw_get
 * transport.tempo failed only on the unmarked displayedValue()/name()/
 * exists() siblings of an already-marked tempo() Parameter. "Depth 2 from a
 * root/item" means: the root's own zero-arg Value-returning members are
 * marked (depth 1 relative to the root), and each of THOSE values' own such
 * members are marked too (depth 2) — but no deeper, so init stays bounded
 * regardless of how deep the live API graph actually nests.
 */
public final class BridgeInterestMarker {

    private BridgeInterestMarker() {}

    /**
     * Marks interest on the bridge's readable surface to depth 2 from every
     * root, and (for Bank roots and per-track device banks) from every
     * window item too. MUST be called from BitwigApiFacade's constructor —
     * calling it any later is a no-op in practice, since Bitwig silently
     * ignores post-init markInterested()/addValueObserver() calls.
     *
     * @return the number of newly-registered values marked, for logging.
     */
    public static int markAll(BridgeGraph graph) {
        AtomicInteger counter = new AtomicInteger();
        for (Object root : graph.roots().values()) {
            markObject(root, 2, counter);
            if (root instanceof Bank<?> bank) {
                markBankItems(bank, counter);
            }
        }
        markTrackDeviceBanks(graph, counter);
        for (Bank<?> bank : safeList(graph::auxiliaryDeviceBanks)) {
            markObject(bank, 2, counter);
            markBankItems(bank, counter);
        }

        // Cycle 2 Task 10: popup browser columns (not bridge roots — reached
        // only via graph.browserColumns()) and their init-created item banks
        // (createItemBank is init-only; the bank behind "<column>.items" is
        // wired here, once, while marking still works).
        for (Object column : safeList(graph::browserColumns)) {
            markObject(column, 2, counter);
        }
        for (Bank<?> bank : safeCollection(graph::browserItemBanks)) {
            markObject(bank, 2, counter);
            markBankItems(bank, counter);
        }
        return counter.get();
    }

    /**
     * Null/exception-tolerant wrapper for a graph accessor that returns a
     * List — e.g. per-pad/per-layer DeviceBanks or browser columns, none of
     * which are bridge roots. Tolerates a null return (unstubbed Mockito mock
     * in existing tests that predate a given sweep) as well as a thrown
     * RuntimeException, so one bad accessor can't abort the whole sweep.
     */
    private static <T> List<T> safeList(Supplier<List<T>> accessor) {
        try {
            List<T> values = accessor.get();
            return values != null ? values : List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Same tolerance as {@link #safeList}, for accessors returning a Collection. */
    private static <T> Collection<T> safeCollection(Supplier<Collection<T>> accessor) {
        try {
            Collection<T> values = accessor.get();
            return values != null ? values : List.of();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Per-track DeviceBanks aren't bridge roots — they're reached only via
     * graph.deviceBankForTrack(i), bounded by the "tracks" root bank's own
     * window size (mirrors how BitwigApiFacade originally sized them 1:1).
     */
    private static void markTrackDeviceBanks(BridgeGraph graph, AtomicInteger counter) {
        if (!(graph.rootOrNull("tracks") instanceof Bank<?> tracks)) {
            return;
        }
        int size = safeSizeOfBank(tracks);
        for (int i = 0; i < size; i++) {
            Object deviceBank = safeDeviceBankForTrack(graph, i);
            if (deviceBank instanceof Bank<?> bank) {
                markObject(bank, 2, counter);
                markBankItems(bank, counter);
            }
        }
    }

    /** Marks each window item (getItemAt(0..getSizeOfBank())) to depth 2. */
    private static void markBankItems(Bank<?> bank, AtomicInteger counter) {
        int size = safeSizeOfBank(bank);
        for (int i = 0; i < size; i++) {
            Object item = safeGetItemAt(bank, i);
            if (item != null) {
                markObject(item, 2, counter);
            }
        }
    }

    /**
     * Marks target's own zero-arg, non-deprecated, Value-returning members
     * (via ReflectionUtil.publicApiMethods, which already excludes
     * BridgeExclusions-hidden members), recursing one more level when
     * depth > 1. Every reflective step is best-effort: a bad member (throws,
     * or — in unit tests — a mock returning null) is skipped rather than
     * aborting the whole sweep.
     */
    private static void markObject(Object target, int depth, AtomicInteger counter) {
        if (target == null) {
            return;
        }
        for (Method m : ReflectionUtil.publicApiMethods(target)) {
            if (m.getParameterCount() != 0) {
                continue;
            }
            if (m.isAnnotationPresent(Deprecated.class)) {
                continue;
            }
            if (!Value.class.isAssignableFrom(m.getReturnType())) {
                continue;
            }
            Object result;
            try {
                result = m.invoke(target);
            } catch (Exception e) {
                continue; // best-effort: a bad reflective call must not abort the sweep
            }
            if (result == null) {
                continue; // unstubbed mock getter, or a genuinely absent live value
            }
            if (!SubscribeSettle.registerMarked(result)) {
                continue; // already marked via another path — cycles/shared proxies marked once
            }
            markInterestedSafely(result);
            counter.incrementAndGet();
            if (depth > 1) {
                markObject(result, depth - 1, counter);
            }
        }
    }

    private static void markInterestedSafely(Object value) {
        if (!(value instanceof Value<?> v)) {
            return;
        }
        try {
            v.markInterested();
        } catch (RuntimeException e) {
            // best-effort: one bad value must not abort the sweep
        }
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

    private static Object safeDeviceBankForTrack(BridgeGraph graph, int index) {
        try {
            return graph.deviceBankForTrack(index);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
