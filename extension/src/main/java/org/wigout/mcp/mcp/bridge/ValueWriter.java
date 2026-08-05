package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.*;
import org.wigout.mcp.common.bridge.SubscribeSettle;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Writes JSON values into Settable* Bitwig values with bounded verify —
 * poll the cached value until it reflects the write, up to
 * verifyAttempts × verifyDelayMs (30 × 100 ms in production; device/engine
 * latency measured >500 ms live). verified=false means "not observed in
 * time", never a fabricated success.
 *
 * JSON mapping:
 *   SettableBooleanValue  <- boolean
 *   SettableStringValue   <- string
 *   SettableIntegerValue  <- number (intValue)
 *   SettableEnumValue     <- string
 *   SettableRangedValue   <- number in [0,1] -> setImmediately() (bypasses user
 *                            take-over strategy, unlike set(double) — see
 *                            SettableRangedValue javadoc); {"raw": n} -> setRaw()
 *   SettableDoubleValue   <- number -> set(double)   (covers SettableBeatTimeValue)
 *   SettableColorValue    <- {"red","green","blue"[,"alpha"]} floats 0..1
 */
public final class ValueWriter {

    private static final double EPSILON = 1e-4;

    // Pre-set subscribe settle: short and fixed (matches ValueReader's production
    // default), independent of verifyAttempts/verifyDelayMs. Its only job is
    // letting the FIRST cache update land before we set(); it has no early exit
    // (nothing to poll yet), so it must stay short — the verify() poll below,
    // which DOES have an early exit, is what absorbs real post-write engine
    // latency (up to verifyAttempts x verifyDelayMs, 30 x 100 ms in production).
    private static final int SETTLE_WAIT_ATTEMPTS = 4;
    private static final long SETTLE_WAIT_DELAY_MS = 50;

    private final int verifyAttempts;
    private final long verifyDelayMs;

    public ValueWriter() {
        this(30, 100);
    }

    ValueWriter(int verifyAttempts, long verifyDelayMs) {
        this.verifyAttempts = verifyAttempts;
        this.verifyDelayMs = verifyDelayMs;
    }

    public Map<String, Object> write(Object target, Object jsonValue) throws BitwigApiException {
        final String operation = "bw_set";

        if (target == null) {
            throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
                "Target is null — path did not resolve to a settable value.");
        }

        if (target instanceof SettableBooleanValue value) {
            if (!(jsonValue instanceof Boolean b)) {
                throw typeError(operation, "boolean", jsonValue);
            }
            ensureSubscribed(value);
            value.set(b);
            return verified(() -> value.get() == b);
        }
        // Parameter extends SettableRangedValue — this branch handles both.
        if (target instanceof SettableRangedValue value) {
            if (jsonValue instanceof Map<?, ?> map && map.containsKey("raw")) {
                Object rawObj = map.get("raw");
                if (!(rawObj instanceof Number n)) {
                    throw typeError(operation, "{\"raw\": number}", jsonValue);
                }
                double raw = n.doubleValue();
                ensureSubscribed(value);
                value.setRaw(raw);
                return verified(() -> Math.abs(value.getRaw() - raw) < Math.max(EPSILON, Math.abs(raw) * 1e-6));
            }
            if (!(jsonValue instanceof Number n)) {
                throw typeError(operation, "number in [0,1] or {\"raw\": number}", jsonValue);
            }
            double normalized = n.doubleValue();
            if (normalized < 0.0 || normalized > 1.0) {
                throw new BitwigApiException(ErrorCode.INVALID_RANGE, operation,
                    "Normalized value must be within [0,1]; got " + normalized
                    + ". Use {\"raw\": n} for raw-unit writes.");
            }
            ensureSubscribed(value);
            // set(double) is subject to the user's take-over strategy and may
            // silently no-op (live E2E finding, Task 8); setImmediately(double)
            // (API v4, not deprecated) always applies the write, which is what
            // a programmatic bw_set caller expects.
            value.setImmediately(normalized);
            return verified(() -> Math.abs(value.get() - normalized) < EPSILON);
        }
        if (target instanceof SettableIntegerValue value) {
            if (!(jsonValue instanceof Number n)) {
                throw typeError(operation, "integer", jsonValue);
            }
            int i = n.intValue();
            ensureSubscribed(value);
            value.set(i);
            return verified(() -> value.get() == i);
        }
        if (target instanceof SettableEnumValue value) {
            if (!(jsonValue instanceof String s)) {
                throw typeError(operation, "string (enum value)", jsonValue);
            }
            ensureSubscribed(value);
            value.set(s);
            return verified(() -> s.equals(value.get()));
        }
        if (target instanceof SettableStringValue value) {
            if (!(jsonValue instanceof String s)) {
                throw typeError(operation, "string", jsonValue);
            }
            ensureSubscribed(value);
            value.set(s);
            return verified(() -> s.equals(value.get()));
        }
        if (target instanceof SettableColorValue value) {
            if (!(jsonValue instanceof Map<?, ?> map)
                    || !(map.get("red") instanceof Number r)
                    || !(map.get("green") instanceof Number g)
                    || !(map.get("blue") instanceof Number b)) {
                throw typeError(operation, "{\"red\",\"green\",\"blue\"[,\"alpha\"]} floats 0..1", jsonValue);
            }
            float red = r.floatValue(), green = g.floatValue(), blue = b.floatValue();
            ensureSubscribed(value);
            if (map.get("alpha") instanceof Number a) {
                float alpha = a.floatValue();
                value.set(red, green, blue, alpha);
                return verified(() -> Math.abs(value.red() - red) < EPSILON
                    && Math.abs(value.green() - green) < EPSILON
                    && Math.abs(value.blue() - blue) < EPSILON
                    && Math.abs(value.alpha() - alpha) < EPSILON);
            }
            value.set(red, green, blue);
            return verified(() -> Math.abs(value.red() - red) < EPSILON
                && Math.abs(value.green() - green) < EPSILON
                && Math.abs(value.blue() - blue) < EPSILON);
        }
        if (target instanceof SettableDoubleValue value) {
            if (!(jsonValue instanceof Number n)) {
                throw typeError(operation, "number", jsonValue);
            }
            double d = n.doubleValue();
            ensureSubscribed(value);
            value.set(d);
            return verified(() -> Math.abs(value.get() - d) < EPSILON);
        }

        throw new BitwigApiException(ErrorCode.INVALID_PARAMETER, operation,
            "Target is not settable (" + target.getClass().getSimpleName()
            + "). Readable values are read with bw_get; actions are invoked with bw_call.");
    }

    /**
     * Subscribes the target if it isn't already, so the cached value the
     * post-write verify() poll reads is actually kept live by the controller
     * thread — without this, an unsubscribed value's cache never updates
     * after set(), so verify() would either always time out to false or,
     * worse, fabricate success if the stale cached default happens to equal
     * the requested value. Uses the short fixed SETTLE_WAIT_* bounds (not
     * verifyAttempts/verifyDelayMs) — this wait has no early exit, so tying
     * it to the (much larger, production) verify bounds would turn every
     * first-touch write into an unconditional ~2.9 s sleep.
     */
    private void ensureSubscribed(Subscribable subscribable) {
        SubscribeSettle.ensureSubscribed(subscribable, SETTLE_WAIT_ATTEMPTS, SETTLE_WAIT_DELAY_MS);
    }

    /**
     * Polls condition up to verifyAttempts times. If the getter inside
     * condition hits Bitwig's init-time interest guard (Task 5a — the value
     * fell outside BridgeInterestMarker's bounded depth-2 sweep), the set
     * already happened (it runs before verified() is ever called in every
     * write() branch) — that must not be lost to an uncaught exception, so
     * this reports verified:false with an honest verify_note instead of
     * propagating. Any OTHER exception from the getter still propagates
     * unchanged, exactly as before this guard was added.
     */
    private Map<String, Object> verified(BooleanSupplier condition) {
        boolean ok = false;
        boolean guardError = false;
        for (int attempt = 0; attempt < verifyAttempts; attempt++) {
            try {
                if (condition.getAsBoolean()) {
                    ok = true;
                    break;
                }
            } catch (RuntimeException e) {
                if (!SubscribeSettle.isMarkInterestedGuardError(e)) {
                    throw e;
                }
                guardError = true;
                break; // the getter will keep throwing — no point polling further
            }
            if (attempt < verifyAttempts - 1) {
                try {
                    Thread.sleep(verifyDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verified", ok);
        if (guardError) {
            result.put("verify_note", "value not init-marked; set was applied but cannot be read back");
        } else if (!ok) {
            result.put("message", "Write was requested but the cached value did not reflect it within the timeout. Confirm with bw_get.");
        }
        return result;
    }

    private static BitwigApiException typeError(String operation, String expected, Object got) {
        return new BitwigApiException(ErrorCode.INVALID_PARAMETER_TYPE, operation,
            "Expected " + expected + ", got "
            + (got == null ? "null" : got.getClass().getSimpleName() + " (" + got + ")"));
    }
}
