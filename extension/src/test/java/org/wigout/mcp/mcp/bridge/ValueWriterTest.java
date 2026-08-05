package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.*;
import org.wigout.mcp.common.bridge.SubscribeSettle;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValueWriterTest {

    private ValueWriter writer;

    @BeforeEach
    void setUp() {
        // Defensive: SubscribeSettle's event-thread runner is a process-wide
        // static (installed by BitwigApiFacade's constructor in other test
        // classes) — force inline registration regardless of test order.
        SubscribeSettle.install(null);
        writer = new ValueWriter(2, 0); // 2 verify attempts, no delay
    }

    @Test
    void testSetsBooleanVerified() throws Exception {
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(true); // post-set poll sees it

        Map<String, Object> result = writer.write(value, Boolean.TRUE);

        verify(value).set(true);
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testSetsStringUnverifiedWhenCacheNeverUpdates() throws Exception {
        SettableStringValue value = mock(SettableStringValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn("Old");

        Map<String, Object> result = writer.write(value, "New");

        verify(value).set("New");
        assertEquals(false, result.get("verified"));
        assertTrue(((String) result.get("message")).contains("timeout"));
    }

    @Test
    void testRangedNumberSetsNormalizedWithEpsilonVerify() throws Exception {
        // setImmediately (not set) — set(double) is subject to the user's
        // take-over strategy and can silently no-op (live E2E finding).
        SettableRangedValue value = mock(SettableRangedValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(0.75000001);

        Map<String, Object> result = writer.write(value, 0.75);

        verify(value).setImmediately(0.75);
        verify(value, never()).set(anyDouble());
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testRangedRawObjectSetsRaw() throws Exception {
        SettableRangedValue value = mock(SettableRangedValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.getRaw()).thenReturn(120.0);

        Map<String, Object> result = writer.write(value, Map.of("raw", 120.0));

        verify(value).setRaw(120.0);
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testNormalizedRangeValidation() {
        SettableRangedValue value = mock(SettableRangedValue.class);
        assertThrows(BitwigApiException.class, () -> writer.write(value, 1.5));
        verify(value, never()).setImmediately(anyDouble());
        verify(value, never()).set(anyDouble());
    }

    @Test
    void testSetsIntegerAndEnum() throws Exception {
        SettableIntegerValue intValue = mock(SettableIntegerValue.class);
        when(intValue.isSubscribed()).thenReturn(true);
        when(intValue.get()).thenReturn(7);
        assertEquals(true, writer.write(intValue, 7).get("verified"));
        verify(intValue).set(7);

        SettableEnumValue enumValue = mock(SettableEnumValue.class);
        when(enumValue.isSubscribed()).thenReturn(true);
        when(enumValue.get()).thenReturn("Latch");
        assertEquals(true, writer.write(enumValue, "Latch").get("verified"));
        verify(enumValue).set("Latch");
    }

    @Test
    void testNonSettableTargetIsError() {
        StringValue readOnly = mock(StringValue.class);
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> writer.write(readOnly, "x"));
        assertTrue(e.getMessage().contains("not settable"));
    }

    @Test
    void testWrongJsonTypeIsError() {
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> writer.write(value, "yes"));
        assertTrue(e.getMessage().contains("boolean"));
        verify(value, never()).set(anyBoolean());
    }

    @Test
    void testWriteNullThrowsBitwigApiException() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> writer.write(null, "x"));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
    }

    @Test
    void testSetsDouble() throws Exception {
        SettableDoubleValue value = mock(SettableDoubleValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(3.5);

        Map<String, Object> result = writer.write(value, 3.5);

        verify(value).set(3.5);
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testColorSetWithThreeComponentsVerifiesOnlyRgb() throws Exception {
        SettableColorValue value = mock(SettableColorValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.red()).thenReturn(1.0f);
        when(value.green()).thenReturn(0.5f);
        when(value.blue()).thenReturn(0.25f);
        // alpha is never stubbed away from its Mockito default (0.0f) — must not be checked.

        Map<String, Object> result = writer.write(value, Map.of("red", 1.0, "green", 0.5, "blue", 0.25));

        verify(value).set(1.0f, 0.5f, 0.25f);
        verify(value, never()).set(anyFloat(), anyFloat(), anyFloat(), anyFloat());
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testColorSetWithAlphaVerifiesAlphaToo() throws Exception {
        SettableColorValue value = mock(SettableColorValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.red()).thenReturn(1.0f);
        when(value.green()).thenReturn(0.5f);
        when(value.blue()).thenReturn(0.25f);
        when(value.alpha()).thenReturn(0.8f);

        Map<String, Object> result = writer.write(value, Map.of("red", 1.0, "green", 0.5, "blue", 0.25, "alpha", 0.8));

        verify(value).set(1.0f, 0.5f, 0.25f, 0.8f);
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testColorSetWithAlphaFailsVerifyWhenAlphaCacheStale() throws Exception {
        SettableColorValue value = mock(SettableColorValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.red()).thenReturn(1.0f);
        when(value.green()).thenReturn(0.5f);
        when(value.blue()).thenReturn(0.25f);
        when(value.alpha()).thenReturn(0.0f); // stale — never updated to the requested 0.8

        Map<String, Object> result = writer.write(value, Map.of("red", 1.0, "green", 0.5, "blue", 0.25, "alpha", 0.8));

        assertEquals(false, result.get("verified"));
    }

    @Test
    void testWriteSubscribesUnsubscribedTargetBeforeSet() throws Exception {
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        when(value.isSubscribed()).thenReturn(false);
        when(value.get()).thenReturn(true);

        writer.write(value, Boolean.TRUE);

        verify(value).subscribe();
    }

    @Test
    void testWriteDoesNotReactivateAlreadyActivatedTarget() throws Exception {
        // SubscribeSettle gates repeats via an identity registry, not
        // isSubscribed() — "already activated" means "already touched by an
        // earlier write to THIS instance", exercised here with two writes.
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        when(value.get()).thenReturn(true);

        writer.write(value, Boolean.TRUE); // first write: activates
        writer.write(value, Boolean.TRUE); // second write: already activated

        verify(value, times(1)).subscribe();
    }

    @Test
    void testColorWriteSubscribesTheColorValueItselfWhenUnsubscribed() throws Exception {
        SettableColorValue value = mock(SettableColorValue.class);
        when(value.isSubscribed()).thenReturn(false);
        when(value.red()).thenReturn(1.0f);
        when(value.green()).thenReturn(0.5f);
        when(value.blue()).thenReturn(0.25f);

        writer.write(value, Map.of("red", 1.0, "green", 0.5, "blue", 0.25));

        verify(value).subscribe();
    }

    @Test
    void testVerifyGuardErrorReturnsUnverifiedWithNoteInsteadOfThrowing() throws Exception {
        // Task 5a: if the verify poll's getter hits Bitwig's init-time
        // interest guard, the set already happened — do not lose that by
        // propagating an exception; report verified:false with an honest note.
        SettableStringValue value = mock(SettableStringValue.class);
        when(value.get()).thenThrow(new RuntimeException(
            "Either call markInterested() or add at least one observer in init in order to access the current value."));

        Map<String, Object> result = writer.write(value, "New");

        verify(value).set("New"); // the set itself must still happen
        assertEquals(false, result.get("verified"));
        assertEquals("value not init-marked; set was applied but cannot be read back", result.get("verify_note"));
        assertNull(result.get("message"));
    }
}
