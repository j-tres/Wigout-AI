package org.wigout.mcp.mcp.bridge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache of the cursor device's DirectParameter-by-ID surface — the bridge's
 * only window into VST parameters when a plugin exposes zero remote-control
 * pages (live-verified: this is the common case). Fed exclusively by three
 * observers registered on cursorDevice AT INIT (init-only rule — none of
 * these can be added later): addDirectParameterIdObserver,
 * addDirectParameterNameObserver, addDirectParameterNormalizedValueObserver
 * (see BitwigApiFacade). Callbacks arrive on Bitwig's controller thread;
 * reads come from Jetty request threads — hence ConcurrentHashMap and a
 * volatile ordered id list, same concurrency rationale as NoteStepCache.
 *
 * No display values: the fourth observer's windowing call
 * (DirectParameterValueDisplayObserver.setObservedParameterIds) HALTS the
 * extension on Bitwig 6.1b4 in every invocation context tried — see live
 * finding #45. Names + normalized values only.
 *
 * The id array is re-fired by Bitwig on every cursor-device change but the
 * per-id maps are never cleared by the API itself, so {@link #onIds} prunes
 * any id no longer present — otherwise a previous device's parameters would
 * leak into the next one's read-back.
 *
 * NaN normalized values ("value not accessible", per the API doc) are stored
 * as absent (the id is removed from the value map) rather than as the
 * Double NaN object, so {@link #valueOf} returns null for them — ValueReader
 * must never emit NaN, which is not valid JSON.
 *
 * {@link #generation()} bumps on every id-array update. A programmatic write
 * does not echo through the value observer (Bitwig suppresses the controller's
 * own write), so the post-write refresh (BitwigApiFacade) bounces the cursor
 * device — two id-array updates (away, back) — and waits for the generation to
 * advance by two, which is the signal that the cache is back on the original
 * device with freshly observed values.
 */
public final class DirectParameterCache {

    private volatile List<String> ids = List.of();
    private volatile long generation = 0;
    private final ConcurrentHashMap<String, String> names = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> values = new ConcurrentHashMap<>();

    /**
     * Observer entry point — wired as
     * {@code cursorDevice.addDirectParameterIdObserver(cache::onIds)} at
     * init. Replaces the ordered id list wholesale and prunes every per-id
     * map of ids no longer present (a device change re-fires this with the
     * new device's full id array; nulls are dropped defensively — the API
     * doc doesn't promise ids are non-null, and a null id must never crash
     * the controller thread).
     */
    public void onIds(String[] newIds) {
        List<String> ordered = new ArrayList<>();
        if (newIds != null) {
            for (String id : newIds) {
                if (id != null) {
                    ordered.add(id);
                }
            }
        }
        ids = List.copyOf(ordered);
        generation++;
        Set<String> keep = new HashSet<>(ordered);
        names.keySet().retainAll(keep);
        values.keySet().retainAll(keep);
    }

    /**
     * Monotonic counter bumped on every id-array update (device change). Used
     * by the post-write refresh to detect that the cursor-device bounce has
     * completed (see class doc).
     */
    public long generation() {
        return generation;
    }

    /** Observer entry point — cursorDevice.addDirectParameterNameObserver(64, cache::onName). */
    public void onName(String id, String name) {
        names.put(id, name);
    }

    /**
     * Observer entry point — cursorDevice.addDirectParameterNormalizedValueObserver(cache::onValue).
     * NaN ("value not accessible") clears any previously cached value instead of storing it.
     */
    public void onValue(String id, double normalizedValue) {
        if (Double.isNaN(normalizedValue)) {
            values.remove(id);
        } else {
            values.put(id, normalizedValue);
        }
    }

    /** Ordered parameter ids for the current cursor device, as last reported by the id observer. */
    public List<String> ids() {
        return ids;
    }

    public int size() {
        return ids.size();
    }

    public String nameOf(String id) {
        return names.get(id);
    }

    /** Null when unknown or last reported as NaN ("not accessible"). */
    public Double valueOf(String id) {
        return values.get(id);
    }
}
