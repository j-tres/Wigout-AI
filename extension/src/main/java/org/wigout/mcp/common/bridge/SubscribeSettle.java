package org.wigout.mcp.common.bridge;

import com.bitwig.extension.controller.api.Subscribable;
import com.bitwig.extension.controller.api.Value;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.wigout.mcp.mcp.bridge.ReflectionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Shared "make get() legal" logic used by both {@link ValueReader} and
 * {@link ValueWriter}.
 *
 * Live-gate evidence, round 1: bare subscribe() does NOT unlock Value.get()
 * — Bitwig throws "Either call markInterested() or add at least one observer
 * in init in order to access the current value." subscribe()/isSubscribed()
 * only control whether observers get notified (Subscribable javadoc);
 * INTEREST, not subscription, is what gates get() (Value.markInterested
 * javadoc, BitwigAPI25.txt ~L12949). So for a Value<?> target we do THREE
 * best-effort, independent attempts:
 *   1. markInterested() directly — documented "can only be called once
 *      during the driver's init method", but live behavior outside init is
 *      unknown, so we try it anyway rather than trust the doc alone;
 *   2. reflectively add a no-op observer via a non-deprecated 1-arg
 *      addValueObserver(...) — "Adding an observer to a value will
 *      automatically mark this value as interested" per the same javadoc,
 *      making this the reliable path if markInterested truly is init-only;
 *   3. subscribe() — orthogonal to interest, but still needed so the cached
 *      value the reader/writer poll actually updates.
 * If every attempted mechanism throws, the target is unusable — surfaced as
 * BitwigApiException(BITWIG_API_ERROR) instead of silently pressing on with
 * a value that will fail on .get().
 *
 * Live-gate evidence, round 2: with round 1 deployed, registrations still
 * silently no-op — no mechanism threw, Bitwig's engine log showed no
 * controller errors, yet get() still failed. Diagnosis: THREAD AFFINITY. The
 * MCP tool call runs on a Jetty request thread, not Bitwig's control-surface
 * event thread; interest/observer/subscription registration apparently
 * requires the event thread and silently no-ops from any other thread. The
 * remedy is {@code ControllerHost.scheduleTask(Runnable, long)}: an
 * installable runner ({@link #install}) lets {@code BitwigApiFacade} (which
 * owns the host) redirect the three-mechanism registration onto the
 * controller's event thread, then request a flush so the subscription state
 * actually reaches the host. With no runner installed (unit tests, or if
 * init hasn't wired one yet) registration runs inline on the calling thread,
 * exactly as round 1.
 *
 * An identity-keyed registry (not equals()/hashCode(), which real Bitwig
 * objects or mocks may override) remembers which targets have already been
 * activated: markInterested() is documented "call once", so repeats must be
 * impossible regardless of what isSubscribed() reports afterward.
 */
public final class SubscribeSettle {

    /** Bounded wait for the controller-thread registration to complete before we settle/return. */
    private static final long CONTROLLER_THREAD_TIMEOUT_SECONDS = 1;

    private static final Set<Subscribable> ACTIVATED =
        Collections.newSetFromMap(Collections.synchronizedMap(new IdentityHashMap<>()));

    /** Null (default) = run registration inline; installed = dispatch it to the controller's event thread. */
    private static volatile Consumer<Runnable> eventThreadRunner;

    private static final InvocationHandler NO_OP_HANDLER = (proxy, method, args) -> {
        Class<?> returnType = method.getReturnType();
        if (returnType == boolean.class) {
            return Boolean.FALSE;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == double.class) {
            return 0.0;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        return null; // void, or any Object-returning method (equals/hashCode/toString etc.)
    };

    private SubscribeSettle() {}

    /**
     * Installs the runner used to dispatch registration onto Bitwig's
     * controller event thread — typically {@code r -> host.scheduleTask(() ->
     * { r.run(); host.requestFlush(); }, 0)}, wired once at extension init
     * where the host is available. Pass null to restore inline execution
     * (the default, and what unit tests use).
     */
    public static void install(Consumer<Runnable> runner) {
        eventThreadRunner = runner;
    }

    /**
     * Records candidate as already-activated in the shared identity registry
     * WITHOUT going through markInterested()/addValueObserver()/subscribe()
     * here — used by {@code BridgeInterestMarker} (Task 5a), which marks
     * interest directly at init (the only time markInterested() actually
     * works in Bitwig 6.1b2) and just needs later ensureSubscribed() calls
     * to see these values as already active: no false subscribed_now, and
     * no pointless runtime re-attempt of a mechanism that cannot succeed
     * outside init anyway.
     *
     * @return true if newly registered, false if candidate was already
     *         present (shared proxies / cycles reached via more than one
     *         path) or isn't a Subscribable at all.
     */
    public static boolean registerMarked(Object candidate) {
        return candidate instanceof Subscribable subscribable && ACTIVATED.add(subscribable);
    }

    /** True iff this exact instance was interest-marked/activated (identity, not equals). */
    public static boolean isMarked(Object candidate) {
        return candidate instanceof Subscribable subscribable && ACTIVATED.contains(subscribable);
    }

    /**
     * True if the exception's message indicates Bitwig's init-time interest
     * guard ("Either call markInterested() or add at least one observer in
     * init in order to access the current value.") — used by ValueReader/
     * ValueWriter to translate a raw guard-error RuntimeException into an
     * honest, actionable BitwigApiException instead of a confusing leak.
     */
    public static boolean isMarkInterestedGuardError(Throwable e) {
        return e != null && e.getMessage() != null && e.getMessage().contains("markInterested");
    }

    /**
     * Ensures the target is subscribed/interested so get() is legal and the
     * cache updates; returns true if this call newly activated it (false if
     * already activated by an earlier call on this exact instance).
     *
     * @throws BitwigApiException if no event-thread runner is installed,
     *         target is a Value<?>, and every interest mechanism
     *         (markInterested/observer/subscribe) threw. When a runner IS
     *         installed, registration runs asynchronously on the controller
     *         thread — a total failure there cannot be synchronously
     *         reported back to this (foreign, e.g. Jetty request) thread, so
     *         it is left for Bitwig's own scheduled-task error handling to
     *         surface instead of being swallowed silently.
     */
    public static boolean ensureSubscribed(Subscribable subscribable, int waitAttempts, long waitDelayMs) {
        // ACTIVATED.add's boolean return IS the atomic "already active" gate:
        // a separate contains()-then-add() would let two concurrent callers
        // both observe "not yet active" and both proceed to activate() the
        // same target.
        if (!ACTIVATED.add(subscribable)) {
            return false;
        }
        try {
            Consumer<Runnable> runner = eventThreadRunner;
            if (runner != null) {
                runOnEventThread(subscribable, runner);
            } else {
                activate(subscribable);
            }
        } catch (RuntimeException e) {
            // Total activation failure (every mechanism threw, or a
            // synchronous runner itself rejected the task): un-claim the
            // slot so a later retry is not silently treated as
            // already-activated (see testFailedActivationIsNotRegisteredSoARetryCanSucceed).
            ACTIVATED.remove(subscribable);
            throw e;
        }
        settle(waitAttempts, waitDelayMs);
        return true;
    }

    /** The three-mechanism (Value) or plain-subscribe (non-Value) registration, run wherever called from. */
    private static void activate(Subscribable subscribable) {
        if (subscribable instanceof Value<?> value) {
            activateValue(value);
        } else {
            subscribable.subscribe();
        }
    }

    /**
     * Dispatches activate() onto the installed runner (the controller's
     * event thread) and waits — bounded, never hanging, never throwing from
     * the wait itself — for it to finish before returning. registration is
     * considered "dispatched" (and the target therefore registered by the
     * caller) once runner.accept(task) returns; a dispatch-time failure
     * (e.g. the runner itself throwing) propagates normally and the target
     * is NOT registered, so a later retry can still succeed.
     */
    private static void runOnEventThread(Subscribable subscribable, Consumer<Runnable> runner) {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable task = () -> {
            try {
                activate(subscribable);
            } finally {
                latch.countDown();
            }
        };
        runner.accept(task);
        try {
            latch.await(CONTROLLER_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Runs all three best-effort interest mechanisms; throws only if all of them failed. */
    private static void activateValue(Value<?> value) {
        StringBuilder failures = new StringBuilder();
        boolean anySucceeded = false;

        try {
            value.markInterested();
            anySucceeded = true;
        } catch (RuntimeException e) {
            recordFailure(failures, "markInterested()", e);
        }

        try {
            if (tryAddNoOpObserver(value)) {
                anySucceeded = true;
            }
        } catch (RuntimeException e) {
            recordFailure(failures, "addValueObserver()", e);
        }

        try {
            value.subscribe();
            anySucceeded = true;
        } catch (RuntimeException e) {
            recordFailure(failures, "subscribe()", e);
        }

        if (!anySucceeded) {
            throw new BitwigApiException(ErrorCode.BITWIG_API_ERROR, "bw_get/bw_set",
                "Could not establish interest for " + value.getClass().getSimpleName()
                    + " — every mechanism failed: " + failures);
        }
    }

    /**
     * Finds a non-deprecated 1-arg addValueObserver on the target's public
     * API surface (via ReflectionUtil) and invokes it with a no-op Proxy
     * callback — per the Value javadoc, registering an observer
     * automatically marks the value as interested. Returns false (not an
     * error) if no such method exists; throws (as a RuntimeException, for
     * the caller's best-effort catch) if the method was found but invoking
     * it failed.
     */
    private static boolean tryAddNoOpObserver(Value<?> value) {
        Optional<Method> methodOpt = ReflectionUtil.findMethod(value, "addValueObserver", 1);
        if (methodOpt.isEmpty() || methodOpt.get().isAnnotationPresent(Deprecated.class)) {
            return false;
        }
        Method method = methodOpt.get();
        // Value<ObserverType>.addValueObserver(ObserverType) is generic and never
        // overridden by concrete subtypes (BooleanValue, IntegerValue, ...), so its
        // erasure — the only signature reflection sees on the Method object — is
        // the marker interface ValueChangedCallback, not e.g. BooleanValueChangedCallback.
        // BUT the real implementing class (Bitwig's own, and Mockito's mocks alike)
        // still expects/casts to the CONCRETE callback type at the call site — a
        // Proxy for the bare erased marker throws ClassCastException there. Resolve
        // the concrete type from the generic interface hierarchy instead; fall back
        // to the erased parameter type only if that resolution comes up empty.
        Class<?> callbackType = resolveObserverCallbackType(value.getClass());
        if (callbackType == null) {
            callbackType = method.getParameterTypes()[0];
        }
        Object noOpCallback = Proxy.newProxyInstance(
            callbackType.getClassLoader(), new Class<?>[] {callbackType}, NO_OP_HANDLER);
        try {
            method.invoke(value, noOpCallback);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("addValueObserver invocation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Walks the generic interface/superclass hierarchy (breadth-first) of
     * startClass looking for a parameterization of Value<ObserverType> and
     * returns the concrete ObserverType (e.g. BooleanValueChangedCallback for
     * a BooleanValue). Returns null if none is found (raw/unparameterized
     * usage, or a hierarchy shape this walk doesn't cover).
     */
    private static Class<?> resolveObserverCallbackType(Class<?> startClass) {
        Deque<Type> queue = new ArrayDeque<>();
        Set<Type> visited = new HashSet<>();
        queue.add(startClass);
        while (!queue.isEmpty()) {
            Type t = queue.poll();
            if (!visited.add(t)) {
                continue;
            }
            if (t instanceof ParameterizedType pt && pt.getRawType() == Value.class) {
                Type arg = pt.getActualTypeArguments()[0];
                if (arg instanceof Class<?> argClass) {
                    return argClass;
                }
            }
            Class<?> raw = rawClassOf(t);
            if (raw == null) {
                continue;
            }
            Collections.addAll(queue, raw.getGenericInterfaces());
            Type superclass = raw.getGenericSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
        }
        return null;
    }

    private static Class<?> rawClassOf(Type t) {
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    private static void recordFailure(StringBuilder into, String mechanism, Exception e) {
        if (into.length() > 0) {
            into.append("; ");
        }
        into.append(mechanism).append(": ")
            .append(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
    }

    private static void settle(int waitAttempts, long waitDelayMs) {
        for (int attempt = 0; attempt < waitAttempts - 1; attempt++) {
            try {
                Thread.sleep(waitDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
