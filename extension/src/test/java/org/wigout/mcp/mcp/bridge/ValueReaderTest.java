package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.*;
import org.wigout.mcp.common.bridge.SubscribeSettle;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ValueReaderTest {

    private ValueReader reader;

    @BeforeEach
    void setUp() {
        // Defensive: SubscribeSettle's event-thread runner is a process-wide
        // static (installed by BitwigApiFacade's constructor in other test
        // classes) — force inline registration regardless of test order.
        SubscribeSettle.install(null);
        reader = new ValueReader(1, 0); // no waiting in tests
    }

    @Test
    void testReadsBooleanValueAndSubscribesOnDemand() {
        SettableBooleanValue value = mock(SettableBooleanValue.class);
        when(value.isSubscribed()).thenReturn(false);
        when(value.get()).thenReturn(true);

        Map<String, Object> result = reader.read(value);

        verify(value).subscribe();
        assertEquals(true, result.get("value"));
        assertEquals(true, result.get("subscribed_now"));
        assertEquals("boolean", result.get("kind"));
    }

    @Test
    void testReReadingSameInstanceSkipsReactivation() {
        // SubscribeSettle gates repeats via an identity registry, not
        // isSubscribed() (markInterested() must never be called twice on the
        // same live object) — so "already activated" means "already touched
        // by an earlier read of THIS instance", exercised here with two reads.
        StringValue value = mock(StringValue.class);
        when(value.get()).thenReturn("Drums");

        reader.read(value); // first touch: activates + settles

        Map<String, Object> result = reader.read(value); // second touch: already activated

        verify(value, times(1)).subscribe();
        assertEquals("Drums", result.get("value"));
        assertFalse(result.containsKey("subscribed_now"));
    }

    @Test
    void testReadsParameterWithRawAndDisplayed() {
        Parameter parameter = mock(Parameter.class);
        StringValue displayed = mock(StringValue.class);
        StringValue name = mock(StringValue.class);
        BooleanValue exists = mock(BooleanValue.class);
        when(parameter.isSubscribed()).thenReturn(true);
        when(displayed.isSubscribed()).thenReturn(true);
        when(name.isSubscribed()).thenReturn(true);
        when(exists.isSubscribed()).thenReturn(true);
        when(parameter.get()).thenReturn(0.5);
        when(parameter.getRaw()).thenReturn(-6.0);
        when(parameter.displayedValue()).thenReturn(displayed);
        when(displayed.get()).thenReturn("-6.0 dB");
        when(parameter.name()).thenReturn(name);
        when(name.get()).thenReturn("Volume");
        when(parameter.exists()).thenReturn(exists);
        when(exists.get()).thenReturn(true);

        Map<String, Object> result = reader.read(parameter);

        assertEquals("parameter", result.get("kind"));
        assertEquals(0.5, result.get("value"));
        assertEquals(-6.0, result.get("raw"));
        assertEquals("-6.0 dB", result.get("displayed"));
        assertEquals("Volume", result.get("name"));
        assertEquals(true, result.get("exists"));
    }

    @Test
    void testReadsEnumValue() {
        SettableEnumValue value = mock(SettableEnumValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn("Latch");

        Map<String, Object> result = reader.read(value);

        assertEquals("enum", result.get("kind"));
        assertEquals("Latch", result.get("value"));
    }

    @Test
    void testReadsBankSummary() {
        TrackBank bank = mock(TrackBank.class);
        IntegerValue itemCount = mock(IntegerValue.class);
        SettableIntegerValue scrollPosition = mock(SettableIntegerValue.class);
        when(bank.getSizeOfBank()).thenReturn(128);
        when(bank.itemCount()).thenReturn(itemCount);
        when(itemCount.isSubscribed()).thenReturn(true);
        when(itemCount.get()).thenReturn(5);
        when(bank.scrollPosition()).thenReturn(scrollPosition);
        when(scrollPosition.isSubscribed()).thenReturn(true);
        when(scrollPosition.get()).thenReturn(0);

        Map<String, Object> result = reader.read(bank);

        assertEquals("bank", result.get("kind"));
        assertEquals(128, result.get("size_of_bank"));
        assertEquals(5, result.get("item_count"));
        assertEquals(0, result.get("scroll_position"));
    }

    @Test
    void testNonValueObjectSuggestsDescribe() {
        Track track = mock(Track.class);
        BooleanValue exists = mock(BooleanValue.class);
        when(track.exists()).thenReturn(exists);
        when(exists.isSubscribed()).thenReturn(true);
        when(exists.get()).thenReturn(true);

        Map<String, Object> result = reader.read(track);

        assertEquals("object", result.get("kind"));
        assertEquals(true, result.get("exists"));
        assertTrue(((String) result.get("hint")).contains("bw_describe"));
        assertEquals("Track", result.get("type"));
    }

    @Test
    void testReadNullThrowsBitwigApiException() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> reader.read(null));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
    }

    @Test
    void testBankMarksSubscribedNowWhenAnySubscribableWasFreshlySubscribed() {
        TrackBank bank = mock(TrackBank.class);
        IntegerValue itemCount = mock(IntegerValue.class);
        SettableIntegerValue scrollPosition = mock(SettableIntegerValue.class);
        when(bank.getSizeOfBank()).thenReturn(128);
        when(bank.itemCount()).thenReturn(itemCount);
        when(itemCount.get()).thenReturn(5);
        when(bank.scrollPosition()).thenReturn(scrollPosition);
        when(scrollPosition.get()).thenReturn(0);

        reader.read(scrollPosition); // pre-activate scrollPosition only, before the bank read

        Map<String, Object> result = reader.read(bank);

        verify(itemCount, times(1)).subscribe(); // freshly activated during this bank read
        verify(scrollPosition, times(1)).subscribe(); // only from the pre-activation above
        assertEquals(true, result.get("subscribed_now"));
    }

    @Test
    void testBankOmitsSubscribedNowWhenAllAlreadySubscribed() {
        TrackBank bank = mock(TrackBank.class);
        IntegerValue itemCount = mock(IntegerValue.class);
        SettableIntegerValue scrollPosition = mock(SettableIntegerValue.class);
        when(bank.getSizeOfBank()).thenReturn(128);
        when(bank.itemCount()).thenReturn(itemCount);
        when(itemCount.get()).thenReturn(5);
        when(bank.scrollPosition()).thenReturn(scrollPosition);
        when(scrollPosition.get()).thenReturn(0);

        reader.read(itemCount); // pre-activate both, before the bank read
        reader.read(scrollPosition);

        Map<String, Object> result = reader.read(bank);

        assertFalse(result.containsKey("subscribed_now"));
    }

    @Test
    void testObjectMarksSubscribedNowWhenExistsWasFreshlySubscribed() {
        Track track = mock(Track.class);
        BooleanValue exists = mock(BooleanValue.class);
        when(track.exists()).thenReturn(exists);
        when(exists.isSubscribed()).thenReturn(false); // freshly subscribed
        when(exists.get()).thenReturn(true);

        Map<String, Object> result = reader.read(track);

        verify(exists).subscribe();
        assertEquals(true, result.get("subscribed_now"));
    }

    @Test
    void testReadsPlainRangedValue() {
        RangedValue value = mock(RangedValue.class);
        StringValue displayed = mock(StringValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(0.5);
        when(value.getRaw()).thenReturn(64.0);
        when(value.displayedValue()).thenReturn(displayed);
        when(displayed.isSubscribed()).thenReturn(true);
        when(displayed.get()).thenReturn("64");

        Map<String, Object> result = reader.read(value);

        assertEquals("ranged", result.get("kind"));
        assertEquals(0.5, result.get("value"));
        assertEquals(64.0, result.get("raw"));
        assertEquals("64", result.get("displayed"));
    }

    @Test
    void testReadsSettableRangedValueAsRanged() {
        SettableRangedValue value = mock(SettableRangedValue.class);
        StringValue displayed = mock(StringValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(0.25);
        when(value.getRaw()).thenReturn(32.0);
        when(value.displayedValue()).thenReturn(displayed);
        when(displayed.isSubscribed()).thenReturn(true);
        when(displayed.get()).thenReturn("32");

        Map<String, Object> result = reader.read(value);

        assertEquals("ranged", result.get("kind"));
        assertEquals(0.25, result.get("value"));
        assertEquals(32.0, result.get("raw"));
    }

    @Test
    void testReadsIntegerValue() {
        IntegerValue value = mock(IntegerValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(42);

        Map<String, Object> result = reader.read(value);

        assertEquals("integer", result.get("kind"));
        assertEquals(42, result.get("value"));
    }

    @Test
    void testReadsBeatTimeValue() {
        BeatTimeValue value = mock(BeatTimeValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(4.0);
        when(value.getFormatted()).thenReturn("2.1.1.00");

        Map<String, Object> result = reader.read(value);

        assertEquals("beat_time", result.get("kind"));
        assertEquals(4.0, result.get("value"));
        assertEquals("2.1.1.00", result.get("formatted"));
    }

    @Test
    void testReadsDoubleValue() {
        DoubleValue value = mock(DoubleValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.get()).thenReturn(3.5);

        Map<String, Object> result = reader.read(value);

        assertEquals("double", result.get("kind"));
        assertEquals(3.5, result.get("value"));
    }

    @Test
    void testReadsColorValue() {
        ColorValue value = mock(ColorValue.class);
        when(value.isSubscribed()).thenReturn(true);
        when(value.red()).thenReturn(1.0f);
        when(value.green()).thenReturn(0.5f);
        when(value.blue()).thenReturn(0.25f);
        when(value.alpha()).thenReturn(1.0f);

        Map<String, Object> result = reader.read(value);

        assertEquals("color", result.get("kind"));
        assertEquals(1.0f, result.get("red"));
        assertEquals(0.5f, result.get("green"));
        assertEquals(0.25f, result.get("blue"));
        assertEquals(1.0f, result.get("alpha"));
    }

    @Test
    void testMarkInterestedGuardErrorTranslatesToHonestBitwigApiException() {
        // Task 5a: init-time bulk marking is now the primary interest
        // mechanism; a value the marker's bounded depth-2 sweep never
        // reached will throw Bitwig's own guard error on get() — must be
        // translated into an honest, actionable BitwigApiException instead
        // of leaking a raw "...markInterested()..." RuntimeException.
        StringValue value = mock(StringValue.class);
        when(value.get()).thenThrow(new RuntimeException(
            "Either call markInterested() or add at least one observer in init in order to access the current value."));

        BitwigApiException e = assertThrows(BitwigApiException.class, () -> reader.read(value));

        assertEquals(ErrorCode.OPERATION_FAILED, e.getErrorCode());
        assertTrue(e.getMessage().contains("startup"));
        assertTrue(e.getMessage().contains("depth 2"));
        assertTrue(e.getMessage().toLowerCase().contains("cycle 1"));
    }

    @Test
    void testOpaqueFallbackUsesSimpleClassNameWhenNoApiInterfaceFound() {
        Object target = new Object();

        Map<String, Object> result = reader.read(target);

        assertEquals("opaque", result.get("kind"));
        assertEquals("Object", result.get("type"));
        assertTrue(((String) result.get("hint")).contains("bw_describe"));
    }

    // ---- Task 3 (Cycle 2): NoteStep + plain-return leaves ----

    @Test
    void testReadsNoteStep() throws Exception {
        var step = mock(com.bitwig.extension.controller.api.NoteStep.class);
        when(step.channel()).thenReturn(0);
        when(step.x()).thenReturn(4);
        when(step.y()).thenReturn(64);
        when(step.state()).thenReturn(com.bitwig.extension.controller.api.NoteStep.State.NoteOn);
        when(step.velocity()).thenReturn(0.79);
        when(step.duration()).thenReturn(0.25);

        Map<String, Object> r = reader.read(step);
        assertEquals("note_step", r.get("kind"));
        assertEquals("NoteOn", r.get("state"));
        assertEquals(0.79, (double) r.get("velocity"), 1e-9);
        assertEquals(64, r.get("y"));
    }

    @Test
    void testReadsPlainDouble() throws Exception {
        Map<String, Object> r = reader.read(0.5d);
        assertEquals("plain", r.get("kind"));
        assertEquals(0.5, r.get("value"));
    }

    @Test
    void testReadsPlainEnumAsName() throws Exception {
        Map<String, Object> r = reader.read(com.bitwig.extension.controller.api.NoteStep.State.Empty);
        assertEquals("plain", r.get("kind"));
        assertEquals("Empty", r.get("value"));
    }

    // ---- Task 4 (Cycle 2): NoteStepCache dump ----

    @Test
    void testReadsNoteStepCacheAsNotesDump() throws Exception {
        NoteStepCache cache = new NoteStepCache();
        var s = mock(com.bitwig.extension.controller.api.NoteStep.class);
        when(s.channel()).thenReturn(0);
        when(s.x()).thenReturn(0);
        when(s.y()).thenReturn(60);
        when(s.state()).thenReturn(com.bitwig.extension.controller.api.NoteStep.State.NoteOn);
        cache.onNoteStep(s);

        Map<String, Object> r = reader.read(cache);
        assertEquals("notes", r.get("kind"));
        assertEquals(1, r.get("count"));
        assertEquals(1, ((List<?>) r.get("notes")).size());
    }

    // ---- DirectParameter bridging: DirectParameterCache dump ----

    @Test
    void testReadsDirectParameterCacheAsDirectParametersDump() throws Exception {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1", "p2"});
        cache.onName("p1", "Cutoff");
        cache.onValue("p1", 0.5);
        cache.onValue("p2", Double.NaN); // "not accessible" — must read back as null, never NaN

        Map<String, Object> r = reader.read(cache);

        assertEquals("direct_parameters", r.get("kind"));
        assertEquals(2, r.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) r.get("parameters");
        assertEquals(2, parameters.size());

        Map<String, Object> p1 = parameters.get(0);
        assertEquals("p1", p1.get("id"));
        assertEquals("Cutoff", p1.get("name"));
        assertEquals(0.5, p1.get("value"));
        // Display values are never emitted — the display observer is not
        // registered (setObservedParameterIds halts the extension on Bitwig
        // 6.1b4, live finding #45).
        assertFalse(p1.containsKey("display"), "display must not be emitted (finding #45)");

        Map<String, Object> p2 = parameters.get(1);
        assertEquals("p2", p2.get("id"));
        assertNull(p2.get("value"), "NaN must read back as null, never NaN (invalid JSON)");
    }

    @Test
    void testReadsStringArrayValue() throws Exception {
        var value = mock(com.bitwig.extension.controller.api.StringArrayValue.class);
        when(value.get()).thenReturn(new String[] {"FX", "Sub"});

        Map<String, Object> r = reader.read(value);
        assertEquals("string_array", r.get("kind"));
        assertEquals(java.util.List.of("FX", "Sub"), r.get("value"));
    }
}
