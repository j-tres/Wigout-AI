package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.*;
import org.wigout.mcp.common.bridge.SubscribeSettle;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads any Bitwig Value subtype (or Bank/ObjectProxy summary) into a typed
 * result map. Interest is marked in BULK AT EXTENSION INIT
 * (BridgeInterestMarker sweeps roots and bank items to depth 2) — that is
 * the primary mechanism, and most reads here are plain cache reads of an
 * already-interested value. The on-demand path in {@link SubscribeSettle}
 * (subscribe()/markInterested()/addValueObserver best-effort, waits briefly
 * for the controller thread to deliver the first update, and flags the
 * result with subscribed_now=true) is only a settle/safety net for the rare
 * target the init-time sweep didn't reach — the very first such read may
 * still be the stale default (honest reporting over fabricated freshness).
 *
 * instanceof order matters: Parameter extends SettableRangedValue extends
 * RangedValue; BeatTimeValue extends DoubleValue — most-specific first.
 */
public final class ValueReader {

    private final int subscribeWaitAttempts;
    private final long subscribeWaitDelayMs;

    public ValueReader() {
        this(4, 50);
    }

    ValueReader(int subscribeWaitAttempts, long subscribeWaitDelayMs) {
        this.subscribeWaitAttempts = subscribeWaitAttempts;
        this.subscribeWaitDelayMs = subscribeWaitDelayMs;
    }

    /**
     * @throws BitwigApiException if target is null (INVALID_PARAMETER) — the
     *         path did not resolve to a value; or if a typed read hits
     *         Bitwig's init-time interest guard (OPERATION_FAILED) — the
     *         value was outside BridgeInterestMarker's bounded depth-2 sweep
     *         (see {@link SubscribeSettle}).
     */
    public Map<String, Object> read(Object target) throws BitwigApiException {
        if (target == null) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, "bw_get",
                "Target is null — path did not resolve to a value.");
        }
        try {
            return readDispatch(target);
        } catch (BitwigApiException e) {
            throw e; // already structured — do not double-wrap
        } catch (RuntimeException e) {
            if (SubscribeSettle.isMarkInterestedGuardError(e)) {
                throw new BitwigApiException(ErrorCode.OPERATION_FAILED, "bw_get",
                    "This value was not registered during extension startup (Bitwig requires "
                        + "init-time interest marking). The bridge marks values to depth 2 from "
                        + "roots and bank items; deeper paths are not readable in Cycle 1.");
            }
            throw e;
        }
    }

    private Map<String, Object> readDispatch(Object target) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (target instanceof Parameter parameter) {
            boolean subscribedNow = ensureSubscribed(parameter);
            result.put("kind", "parameter");
            result.put("value", parameter.get());
            result.put("raw", parameter.getRaw());
            StringValue displayed = parameter.displayedValue();
            ensureSubscribed(displayed);
            result.put("displayed", displayed.get());
            StringValue name = parameter.name();
            ensureSubscribed(name);
            result.put("name", name.get());
            BooleanValue exists = parameter.exists();
            ensureSubscribed(exists);
            result.put("exists", exists.get());
            markSubscribedNow(result, subscribedNow);
            return result;
        }
        if (target instanceof RangedValue ranged) {
            boolean subscribedNow = ensureSubscribed(ranged);
            result.put("kind", "ranged");
            result.put("value", ranged.get());
            result.put("raw", ranged.getRaw());
            StringValue displayed = ranged.displayedValue();
            ensureSubscribed(displayed);
            result.put("displayed", displayed.get());
            markSubscribedNow(result, subscribedNow);
            return result;
        }
        if (target instanceof BooleanValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "boolean");
            result.put("value", value.get());
            return reorder(result);
        }
        if (target instanceof EnumValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "enum");
            result.put("value", value.get());
            return reorder(result);
        }
        if (target instanceof StringValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "string");
            result.put("value", value.get());
            return reorder(result);
        }
        if (target instanceof StringArrayValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "string_array");
            String[] strings = value.get();
            result.put("value", strings == null ? java.util.List.of() : java.util.Arrays.asList(strings));
            return reorder(result);
        }
        if (target instanceof IntegerValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "integer");
            result.put("value", value.get());
            return reorder(result);
        }
        if (target instanceof BeatTimeValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "beat_time");
            result.put("value", value.get());
            result.put("formatted", value.getFormatted());
            return reorder(result);
        }
        if (target instanceof DoubleValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "double");
            result.put("value", value.get());
            return reorder(result);
        }
        if (target instanceof ColorValue value) {
            markSubscribedNow(result, ensureSubscribed(value));
            result.put("kind", "color");
            result.put("red", value.red());
            result.put("green", value.green());
            result.put("blue", value.blue());
            result.put("alpha", value.alpha());
            return reorder(result);
        }
        if (target instanceof NoteStepCache cache) {
            result.put("kind", "notes");
            result.put("count", cache.size());
            result.put("notes", cache.all().stream().map(ValueReader::noteStepMap).toList());
            result.put("hint", "Launcher cursor-clip note cache. Edit via bw_call on cursorClip.step(ch,x,y) setters; write new notes via cursorClip setStep.");
            return result;
        }
        if (target instanceof DirectParameterCache cache) {
            result.put("kind", "direct_parameters");
            result.put("count", cache.size());
            List<Map<String, Object>> parameters = new ArrayList<>();
            for (String id : cache.ids()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("id", id);
                p.put("name", cache.nameOf(id));
                // Null for unknown/inaccessible (NaN) — never NaN, which is invalid JSON.
                p.put("value", cache.valueOf(id));
                parameters.add(p);
            }
            result.put("parameters", parameters);
            result.put("hint", "Device DirectParameter-by-ID cache (observer-fed at init) — the only "
                + "full-enumeration surface for devices with zero remote-control pages. Names + "
                + "normalized values only: display-value observation is disabled (setObservedParameterIds "
                + "halts the extension on Bitwig 6.1b4 — live finding #45). Write via bw_call on "
                + "cursorDevice.setDirectParameterValueNormalized(id, value, resolution).");
            return result;
        }
        if (target instanceof NoteStep step) {
            return noteStepMap(step);
        }
        if (target instanceof String || target instanceof Number || target instanceof Boolean) {
            result.put("kind", "plain");
            result.put("value", target);
            return result;
        }
        if (target instanceof Enum<?> enumValue) {
            result.put("kind", "plain");
            result.put("value", enumValue.name());
            return result;
        }
        if (target instanceof Bank<?> bank) {
            result.put("kind", "bank");
            result.put("size_of_bank", bank.getSizeOfBank());
            IntegerValue itemCount = bank.itemCount();
            boolean subscribedNow = ensureSubscribed(itemCount);
            result.put("item_count", itemCount.get());
            SettableIntegerValue scrollPosition = bank.scrollPosition();
            subscribedNow |= ensureSubscribed(scrollPosition);
            result.put("scroll_position", scrollPosition.get());
            markSubscribedNow(result, subscribedNow);
            return result;
        }
        if (target instanceof ObjectProxy proxy) {
            result.put("kind", "object");
            result.put("type", apiTypeName(proxy));
            BooleanValue exists = proxy.exists();
            boolean subscribedNow = ensureSubscribed(exists);
            result.put("exists", exists.get());
            result.put("hint", "Not a value — use bw_describe on this path to see its members.");
            markSubscribedNow(result, subscribedNow);
            return result;
        }
        result.put("kind", "opaque");
        result.put("type", apiTypeName(target));
        result.put("hint", "Not a readable value — use bw_describe.");
        return result;
    }

    /** Subscribes if needed; returns true if a new subscription was made. */
    private boolean ensureSubscribed(Subscribable subscribable) {
        return SubscribeSettle.ensureSubscribed(subscribable, subscribeWaitAttempts, subscribeWaitDelayMs);
    }

    private static void markSubscribedNow(Map<String, Object> result, boolean subscribedNow) {
        if (subscribedNow) {
            result.put("subscribed_now", true);
        }
    }

    /** Keeps kind/value before the optional subscribed_now flag. */
    private static Map<String, Object> reorder(Map<String, Object> result) {
        if (!result.containsKey("subscribed_now")) {
            return result;
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        result.forEach((k, v) -> { if (!k.equals("subscribed_now")) ordered.put(k, v); });
        ordered.put("subscribed_now", true);
        return ordered;
    }

    /** @see ReflectionUtil#apiTypeName(Object) */
    private static String apiTypeName(Object target) {
        return ReflectionUtil.apiTypeName(target);
    }

    /** Compact map of a NoteStep's core fields — plain getters, no interest marking involved. */
    static Map<String, Object> noteStepMap(NoteStep s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "note_step");
        m.put("channel", s.channel());
        m.put("x", s.x());
        m.put("y", s.y());
        m.put("state", s.state().name());
        m.put("velocity", s.velocity());
        m.put("release_velocity", s.releaseVelocity());
        m.put("velocity_spread", s.velocitySpread());
        m.put("duration", s.duration());
        m.put("pan", s.pan());
        m.put("timbre", s.timbre());
        m.put("pressure", s.pressure());
        m.put("gain", s.gain());
        m.put("transpose", s.transpose());
        m.put("chance", s.chance());
        return m;
    }
}
