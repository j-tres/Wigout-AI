package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.Application;
import com.bitwig.extension.controller.api.InsertionPoint;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.Track;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArgCoercerTest {

    @Mock private PathResolver resolver;
    @Mock private Track track;

    private ArgCoercer coercer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        coercer = new ArgCoercer(resolver);
    }

    @Test
    void testCoercesPrimitives() throws Exception {
        Method createAudioTrack = Application.class.getMethod("createAudioTrack", int.class);
        Object[] args = coercer.coerce(createAudioTrack, List.of(3L)); // JSON numbers may arrive as Long
        assertEquals(3, args[0]);
    }

    @Test
    void testCoercesUuidFromString() throws Exception {
        Method insert = InsertionPoint.class.getMethod("insertBitwigDevice", UUID.class);
        Object[] args = coercer.coerce(insert, List.of("8f58138b-03aa-4e9d-83bd-a038c99a4ed5"));
        assertEquals(UUID.fromString("8f58138b-03aa-4e9d-83bd-a038c99a4ed5"), args[0]);
    }

    @Test
    void testCoercesStringPassthrough() throws Exception {
        Method insertFile = InsertionPoint.class.getMethod("insertFile", String.class);
        Object[] args = coercer.coerce(insertFile, List.of("C:/x.bwpreset"));
        assertEquals("C:/x.bwpreset", args[0]);
    }

    @Test
    void testCoercesApiObjectFromPathString() throws Exception {
        // InsertionPoint.copyTracks(Track...) is varargs — use a simpler case:
        // any method taking a Track. Application has none; use a synthetic check
        // through PathResolver: a String arg for a Track param resolves as a path.
        Method copyTracks = InsertionPoint.class.getMethod("copyTracks", Track[].class);
        when(resolver.resolve("tracks[2]")).thenReturn(new PathResolver.Resolution(track, "tracks[2]"));
        Object[] args = coercer.coerce(copyTracks, List.of(List.of("tracks[2]")));
        Track[] resolved = (Track[]) args[0];
        assertSame(track, resolved[0]);
    }

    @Test
    void testApiObjectPathResolvingToNullThrowsCleanMismatchNotNpe() throws Exception {
        // resolver.resolve(path).target() can legitimately be null (e.g. an
        // out-of-window bank index or an unresolved cursor) — the mismatch
        // branch must name the path in a clean BitwigApiException instead of
        // NPE-ing on resolved.getClass().
        Method copyTracks = InsertionPoint.class.getMethod("copyTracks", Track[].class);
        when(resolver.resolve("tracks[9]")).thenReturn(new PathResolver.Resolution(null, "tracks[9]"));

        BitwigApiException e = assertThrows(BitwigApiException.class,
            () -> coercer.coerce(copyTracks, List.of(List.of("tracks[9]"))));
        assertTrue(e.getMessage().contains("tracks[9]"));
        assertTrue(e.getMessage().contains("null"));
    }

    @Test
    void testArityMismatchThrows() throws Exception {
        Method createAudioTrack = Application.class.getMethod("createAudioTrack", int.class);
        assertThrows(BitwigApiException.class, () -> coercer.coerce(createAudioTrack, List.of()));
    }

    @Test
    void testTypeMismatchThrows() throws Exception {
        Method createAudioTrack = Application.class.getMethod("createAudioTrack", int.class);
        BitwigApiException e = assertThrows(BitwigApiException.class,
            () -> coercer.coerce(createAudioTrack, List.of("three")));
        assertTrue(e.getMessage().contains("int"));
    }

    @Test
    void testCoercesNumberParameterByAssignablePassthrough() throws Exception {
        // SettableRangedValue.set(Number, Number) takes abstract java.lang.Number
        // params — no type-specific branch names Number explicitly; the JSON
        // values (already Double/Integer, both Number subtypes) must pass
        // through unchanged rather than being rejected as a type mismatch.
        Method set = SettableRangedValue.class.getMethod("set", Number.class, Number.class);
        Object[] args = coercer.coerce(set, List.of(0.55, 1));
        assertEquals(0.55, args[0]);
        assertEquals(1, args[1]);
    }

    // ---- Task 1 (Cycle 2): type-aware overload selection ----

    interface Overloaded {
        void take(int i);
        void take(String s);
        void take(double d);
    }

    private List<java.lang.reflect.Method> methodsNamed(Class<?> cls, String name) {
        return java.util.Arrays.stream(cls.getMethods())
            .filter(m -> m.getName().equals(name)).toList();
    }

    @Test
    void testSelectsStringOverloadForStringArg() throws Exception {
        ArgCoercer.Selection sel = coercer.selectAndCoerce(
            methodsNamed(Overloaded.class, "take"), List.of("hello"), "bw_call");
        assertEquals(String.class, sel.method().getParameterTypes()[0]);
        assertEquals("hello", sel.args()[0]);
    }

    @Test
    void testSelectsExactNumericOverloadOverWidening() throws Exception {
        // JSON integers arrive as Long; int param scores widening(2), double widening(2),
        // but an exact-boxed match must win when present.
        interface Exact { void take(long l); void take(int i); }
        ArgCoercer.Selection sel = coercer.selectAndCoerce(
            methodsNamed(Exact.class, "take"), List.of(7L), "bw_call");
        assertEquals(long.class, sel.method().getParameterTypes()[0]);
    }

    @Test
    void testArityMismatchListsAvailableArities() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            coercer.selectAndCoerce(methodsNamed(Overloaded.class, "take"), List.of(1, 2), "bw_call"));
        assertTrue(e.getMessage().contains("Arities available"));
    }

    @Test
    void testNoCompatibleOverloadListsCandidateSignatures() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            coercer.selectAndCoerce(methodsNamed(Overloaded.class, "take"), List.of(true), "bw_call"));
        assertTrue(e.getMessage().contains("take(int)") || e.getMessage().contains("Candidates"));
    }

    @Test
    void testTieBreakIsCandidateOrderStable() throws Exception {
        // Both int and double are widening(2) for a Long arg when no exact match
        // exists — the FIRST candidate in the given list must win, repeatably.
        // (Fetch the list once: getMethods() order is itself unspecified.)
        interface Tie { void take(int i); void take(double d); }
        List<java.lang.reflect.Method> candidates = methodsNamed(Tie.class, "take");
        ArgCoercer.Selection first = coercer.selectAndCoerce(candidates, List.of(3L), "bw_call");
        assertEquals(candidates.get(0), first.method());
        for (int i = 0; i < 10; i++) {
            assertEquals(first.method(),
                coercer.selectAndCoerce(candidates, List.of(3L), "bw_call").method());
        }
    }

    @Test
    void testEmptyCandidateListIsStructuredError() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            coercer.selectAndCoerce(List.of(), List.of("x"), "bw_call"));
        assertEquals(ErrorCode.INVALID_PARAMETER, e.getErrorCode());
    }
}
