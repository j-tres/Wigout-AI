package org.wigout.mcp.common.bridge;

import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.Subscribable;
import org.wigout.mcp.common.error.BitwigApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SubscribeSettle keeps a STATIC identity registry (mirrors production:
 * markInterested() must never be called twice on the same live object) —
 * each test below uses a FRESH mock instance so results from other tests
 * never leak in via the registry.
 *
 * The event-thread runner (install()) is ALSO a process-wide static, so it
 * is reset to null (inline registration) both before and after every test
 * here — regardless of what BitwigApiFacadeTest or any other test class did
 * or left behind, and regardless of test execution order.
 */
class SubscribeSettleTest {

    @BeforeEach
    void resetRunnerBefore() {
        SubscribeSettle.install(null);
    }

    @AfterEach
    void resetRunnerAfter() {
        SubscribeSettle.install(null);
    }

    @Test
    void testValueTargetActivatesAllThreeMechanisms() {
        SettableBooleanValue value = mock(SettableBooleanValue.class);

        boolean result = SubscribeSettle.ensureSubscribed(value, 1, 0);

        assertTrue(result);
        verify(value).markInterested();
        verify(value).subscribe();
        // 1-arg addValueObserver is inherited from Value<ObserverType>, erased
        // to ValueChangedCallback — reflectively invoked with a no-op Proxy.
        verify(value).addValueObserver(any());
    }

    @Test
    void testMarkInterestedThrowingDoesNotAbortTheOtherMechanisms() {
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        doThrow(new RuntimeException("can only be called once during init")).when(value).markInterested();

        boolean result = SubscribeSettle.ensureSubscribed(value, 1, 0);

        assertTrue(result);
        verify(value).markInterested();
        verify(value).subscribe();
        verify(value).addValueObserver(any());
    }

    @Test
    void testSecondCallOnSameInstanceDoesNothing() {
        SettableBooleanValue value = mock(SettableBooleanValue.class);

        boolean first = SubscribeSettle.ensureSubscribed(value, 1, 0);
        boolean second = SubscribeSettle.ensureSubscribed(value, 1, 0);

        assertTrue(first);
        assertFalse(second);
        verify(value, times(1)).markInterested();
        verify(value, times(1)).subscribe();
        verify(value, times(1)).addValueObserver(any());
    }

    @Test
    void testNonValueSubscribableGetsPlainSubscribe() {
        Subscribable subscribable = mock(Subscribable.class);

        boolean result = SubscribeSettle.ensureSubscribed(subscribable, 1, 0);

        assertTrue(result);
        verify(subscribable).subscribe();
    }

    @Test
    void testAllMechanismsFailingThrowsBitwigApiException() {
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        doThrow(new RuntimeException("no init")).when(value).markInterested();
        doThrow(new RuntimeException("no observer")).when(value).addValueObserver(any());
        doThrow(new RuntimeException("no subscribe")).when(value).subscribe();

        BitwigApiException e = assertThrows(BitwigApiException.class,
            () -> SubscribeSettle.ensureSubscribed(value, 1, 0));
        assertTrue(e.getMessage().contains("markInterested"));
        assertTrue(e.getMessage().contains("subscribe"));
    }

    @Test
    void testFailedActivationIsNotRegisteredSoARetryCanSucceed() {
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        doThrow(new RuntimeException("no init")).when(value).markInterested();
        doThrow(new RuntimeException("no observer")).when(value).addValueObserver(any());
        doThrow(new RuntimeException("no subscribe")).when(value).subscribe();

        assertThrows(BitwigApiException.class, () -> SubscribeSettle.ensureSubscribed(value, 1, 0));

        // Fix the mock so a retry can succeed — must not be treated as "already activated".
        doNothing().when(value).subscribe();
        boolean retried = SubscribeSettle.ensureSubscribed(value, 1, 0);
        assertTrue(retried);
    }

    @Test
    void testSynchronousInstalledRunnerPreservesExistingBehavior() {
        // A runner that just runs the task immediately, on the calling
        // thread — dispatch is trivially synchronous, so this must behave
        // identically to the no-runner-installed (inline) path.
        SubscribeSettle.install(Runnable::run);
        SettableBooleanValue value = mock(SettableBooleanValue.class);

        boolean result = SubscribeSettle.ensureSubscribed(value, 1, 0);

        assertTrue(result);
        verify(value).markInterested();
        verify(value).subscribe();
        verify(value).addValueObserver(any());
    }

    @Test
    void testIsMarkedReflectsRegistry() {
        var value = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        assertFalse(SubscribeSettle.isMarked(value));
        assertTrue(SubscribeSettle.registerMarked(value));
        assertTrue(SubscribeSettle.isMarked(value));
        assertFalse(SubscribeSettle.isMarked("not a subscribable"));
    }

    @Test
    void testRunnerThatNeverRunsTheTaskDoesNotHang() {
        // Simulates a broken/disposed scheduler: the task is dispatched but
        // never actually executed. ensureSubscribed must still return within
        // the bounded latch wait (1s), never hang, and never throw from the
        // wait itself.
        SubscribeSettle.install(task -> { /* never invoked */ });
        SettableBooleanValue value = mock(SettableBooleanValue.class);

        long startNanos = System.nanoTime();
        boolean result = SubscribeSettle.ensureSubscribed(value, 1, 0);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        // Dispatch itself succeeded, so per unchanged registry semantics this
        // call is still "freshly activated" even though the task never ran.
        assertTrue(result);
        assertTrue(elapsedMs < 5000,
            "expected the bounded latch wait to return well under 5s, took " + elapsedMs + "ms");
        verify(value, never()).markInterested();
        verify(value, never()).subscribe();
    }
}
