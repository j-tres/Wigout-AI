package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.common.error.BitwigApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PathResolver against a mocked BridgeGraph.
 */
class PathResolverTest {

    @Mock private BridgeGraph graph;
    @Mock private Transport transport;
    @Mock private TrackBank trackBank;
    @Mock private Track track;
    @Mock private DeviceBank deviceBank;
    @Mock private Device device;
    @Mock private Parameter tempo;

    private PathResolver resolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("transport", transport);
        roots.put("tracks", trackBank);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("transport")).thenReturn(transport);
        when(graph.rootOrNull("tracks")).thenReturn(trackBank);
        resolver = new PathResolver(graph);
    }

    @Test
    void testResolvesRoot() throws Exception {
        PathResolver.Resolution r = resolver.resolve("transport");
        assertSame(transport, r.target());
        assertEquals("transport", r.canonicalPath());
    }

    @Test
    void testResolvesBankIndexViaGetItemAt() throws Exception {
        when(trackBank.getSizeOfBank()).thenReturn(8);
        when(trackBank.getItemAt(3)).thenReturn(track);

        PathResolver.Resolution r = resolver.resolve("tracks[3]");
        assertSame(track, r.target());
    }

    @Test
    void testResolvesZeroArgNavigation() throws Exception {
        when(trackBank.getSizeOfBank()).thenReturn(8);
        when(trackBank.getItemAt(3)).thenReturn(track);
        Parameter volume = mock(Parameter.class);
        when(track.volume()).thenReturn(volume);

        PathResolver.Resolution r = resolver.resolve("tracks[3].volume");
        assertSame(volume, r.target());
    }

    @Test
    void testDevicesSpecialEdge() throws Exception {
        when(trackBank.getSizeOfBank()).thenReturn(8);
        when(trackBank.getItemAt(2)).thenReturn(track);
        when(graph.deviceBankForTrack(2)).thenReturn(deviceBank);
        when(deviceBank.getSizeOfBank()).thenReturn(4);
        when(deviceBank.getItemAt(1)).thenReturn(device);

        PathResolver.Resolution r = resolver.resolve("tracks[2].devices[1]");
        assertSame(device, r.target());
        verify(graph).deviceBankForTrack(2);
    }

    @Test
    void testUnknownRootListsRoots() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> resolver.resolve("nonsense"));
        assertTrue(e.getMessage().contains("transport"));
        assertTrue(e.getMessage().contains("tracks"));
    }

    @Test
    void testUnknownSegmentListsMembers() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> resolver.resolve("transport.bogus"));
        // Error names the deepest resolvable prefix and suggests real members.
        assertTrue(e.getMessage().contains("transport"));
        assertTrue(e.getMessage().toLowerCase().contains("bogus"));
    }

    @Test
    void testIndexBeyondBankWindowGivesScrollGuidance() {
        when(trackBank.getSizeOfBank()).thenReturn(8);
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> resolver.resolve("tracks[99]"));
        assertTrue(e.getMessage().contains("scroll"));
    }

    @Test
    void testIndexOnNonBankIsError() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () -> resolver.resolve("transport[0]"));
        assertTrue(e.getMessage().contains("not indexable"));
    }

    @Test
    void testExcludedNavigationIsRefused() {
        // SettableBooleanValue.toggleAction() returns HardwareActionBindable —
        // excluded by return type. transport.isPlaying resolves via the REAL
        // Transport interface method, then .toggleAction must be refused.
        com.bitwig.extension.controller.api.SettableBooleanValue isPlaying =
            mock(com.bitwig.extension.controller.api.SettableBooleanValue.class);
        when(transport.isPlaying()).thenReturn(isPlaying);

        BitwigApiException e = assertThrows(BitwigApiException.class,
            () -> resolver.resolve("transport.isPlaying.toggleAction"));
        assertTrue(e.getMessage().contains("excluded"));
        verify(isPlaying, never()).toggleAction();
    }

    // ---- Task 2 (Cycle 2): call segments with literal args ----

    @Test
    void testCallSegmentWithIntLiterals() throws Exception {
        com.bitwig.extension.controller.api.PinnableCursorClip clip =
            mock(com.bitwig.extension.controller.api.PinnableCursorClip.class);
        com.bitwig.extension.controller.api.NoteStep step =
            mock(com.bitwig.extension.controller.api.NoteStep.class);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("cursorClip", clip);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("cursorClip")).thenReturn(clip);
        when(clip.getStep(0, 4, 64)).thenReturn(step);

        PathResolver.Resolution r = resolver.resolve("cursorClip.getStep(0, 4, 64)");
        assertSame(step, r.target());
        assertEquals("cursorClip.getStep(0,4,64)", r.canonicalPath());
    }

    @Test
    void testCallSegmentWithQuotedStringContainingDotAndComma() throws Exception {
        // Quote-aware splitting: the '.' and ',' inside the string must not split segments/args.
        com.bitwig.extension.controller.api.CursorDevice cd =
            mock(com.bitwig.extension.controller.api.CursorDevice.class);
        // selectFirstInSlot(String) returns void — navigating THROUGH it must error,
        // but parsing must get far enough to find the method (proves arg parsing).
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("cursorDevice", cd);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("cursorDevice")).thenReturn(cd);

        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            resolver.resolve("cursorDevice.selectFirstInSlot(\"FX.1,main\").name"));
        assertTrue(e.getMessage().contains("returns nothing")
            || e.getMessage().contains("void"), e.getMessage());
    }

    @Test
    void testCallSegmentBadLiteralIsActionable() {
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("transport", transport);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("transport")).thenReturn(transport);

        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            resolver.resolve("transport.foo(bar)"));
        assertTrue(e.getMessage().contains("literal"), e.getMessage());
    }

    @Test
    void testCallSegmentDeprecatedRefused() {
        // TrackBank.getTrack(int) carries runtime @Deprecated (verified live in Cycle 1 Task 6).
        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            resolver.resolve("tracks.getTrack(0)"));
        assertTrue(e.getMessage().contains("deprecated"));
    }

    // ---- Task 2 fix pass: parseArgLiterals grammar coverage ----

    @Test
    void testParseArgLiteralsCommaSpaceDoesNotLeakIntoStrings() throws Exception {
        // "a", "b" — the space after the comma must NOT become part of the second string.
        assertEquals(java.util.List.of("a", "b"),
            PathResolver.parseArgLiterals("\"a\", \"b\"", "test"));
    }

    @Test
    void testParseArgLiteralsOutsideSpacesDroppedInsideSpacesKept() throws Exception {
        assertEquals(java.util.List.of("a b"),
            PathResolver.parseArgLiterals(" \"a b\" ", "test"));
    }

    @Test
    void testParseArgLiteralsSingleQuoted() throws Exception {
        assertEquals(java.util.List.of("x"),
            PathResolver.parseArgLiterals("'x'", "test"));
    }

    @Test
    void testParseArgLiteralsEscapes() throws Exception {
        assertEquals(java.util.List.of("a\"b"),
            PathResolver.parseArgLiterals("\"a\\\"b\"", "test"));
        assertEquals(java.util.List.of("it's"),
            PathResolver.parseArgLiterals("'it\\'s'", "test"));
        assertEquals(java.util.List.of("back\\slash"),
            PathResolver.parseArgLiterals("\"back\\\\slash\"", "test"));
    }

    @Test
    void testParseArgLiteralsScalars() throws Exception {
        assertEquals(java.util.List.of(Boolean.TRUE), PathResolver.parseArgLiterals("true", "test"));
        assertEquals(java.util.List.of(-2.5d), PathResolver.parseArgLiterals("-2.5", "test"));
        assertEquals(java.util.List.of(-7L), PathResolver.parseArgLiterals("-7", "test"));
    }

    @Test
    void testParseArgLiteralsEmptyArgList() throws Exception {
        assertTrue(PathResolver.parseArgLiterals("", "test").isEmpty());
    }

    @Test
    void testParseArgLiteralsTrailingJunkAfterCloseQuote() {
        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            PathResolver.parseArgLiterals("\"a\"x", "test"));
        assertTrue(e.getMessage().contains("literal"), e.getMessage());
    }

    // ---- Task 4 (Cycle 2): notes/step synthesized edges ----

    @Test
    void testNotesEdgeOnClipReturnsCache() throws Exception {
        var clip = mock(com.bitwig.extension.controller.api.PinnableCursorClip.class);
        NoteStepCache cache = new NoteStepCache();
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("cursorClip", clip);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("cursorClip")).thenReturn(clip);
        when(graph.noteStepCache()).thenReturn(cache);

        assertSame(cache, resolver.resolve("cursorClip.notes").target());
    }

    @Test
    void testStepEdgePrefersCacheThenFallsBackToGetStep() throws Exception {
        var clip = mock(com.bitwig.extension.controller.api.PinnableCursorClip.class);
        var cached = mock(com.bitwig.extension.controller.api.NoteStep.class);
        when(cached.channel()).thenReturn(0);
        when(cached.x()).thenReturn(4);
        when(cached.y()).thenReturn(64);
        when(cached.state()).thenReturn(com.bitwig.extension.controller.api.NoteStep.State.NoteOn);
        NoteStepCache cache = new NoteStepCache();
        cache.onNoteStep(cached);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("cursorClip", clip);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("cursorClip")).thenReturn(clip);
        when(graph.noteStepCache()).thenReturn(cache);
        when(graph.noteGridWidth()).thenReturn(128);
        when(graph.noteGridHeight()).thenReturn(128);

        assertSame(cached, resolver.resolve("cursorClip.step(0,4,64)").target());

        var fresh = mock(com.bitwig.extension.controller.api.NoteStep.class);
        when(clip.getStep(0, 9, 60)).thenReturn(fresh);
        assertSame(fresh, resolver.resolve("cursorClip.step(0,9,60)").target());
        verify(clip).getStep(0, 9, 60);
    }

    @Test
    void testStepEdgeOutOfGridIsBoundsError() {
        var clip = mock(com.bitwig.extension.controller.api.PinnableCursorClip.class);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("cursorClip", clip);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("cursorClip")).thenReturn(clip);
        when(graph.noteStepCache()).thenReturn(new NoteStepCache());
        when(graph.noteGridWidth()).thenReturn(128);
        when(graph.noteGridHeight()).thenReturn(128);

        BitwigApiException e = assertThrows(BitwigApiException.class, () ->
            resolver.resolve("cursorClip.step(0,200,64)"));
        assertTrue(e.getMessage().contains("grid"), e.getMessage());
    }

    // ---- Task 6 (Cycle 2): generalized devices edge — drum pads, layers ----

    @Test
    void testDevicesEdgeOnDrumPadAndLayer() throws Exception {
        var padBank = mock(com.bitwig.extension.controller.api.DrumPadBank.class);
        var pad = mock(com.bitwig.extension.controller.api.DrumPad.class);
        var layerBank = mock(com.bitwig.extension.controller.api.DeviceLayerBank.class);
        var layer = mock(com.bitwig.extension.controller.api.DeviceLayer.class);
        var padDevices = mock(com.bitwig.extension.controller.api.DeviceBank.class);
        var layerDevices = mock(com.bitwig.extension.controller.api.DeviceBank.class);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("drumPads", padBank);
        roots.put("layers", layerBank);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("drumPads")).thenReturn(padBank);
        when(graph.rootOrNull("layers")).thenReturn(layerBank);
        when(padBank.getSizeOfBank()).thenReturn(16);
        when(padBank.getItemAt(3)).thenReturn(pad);
        when(layerBank.getSizeOfBank()).thenReturn(8);
        when(layerBank.getItemAt(1)).thenReturn(layer);
        when(graph.deviceBankForDrumPad(3)).thenReturn(padDevices);
        when(graph.deviceBankForLayer(1)).thenReturn(layerDevices);

        assertSame(padDevices, resolver.resolve("drumPads[3].devices").target());
        assertSame(layerDevices, resolver.resolve("layers[1].devices").target());
    }

    // ---- DirectParameter bridging: cursorDevice.directParameters synthesized edge ----

    @Test
    void testDirectParametersEdgeOnDeviceReturnsCache() throws Exception {
        var cursorDevice = mock(com.bitwig.extension.controller.api.CursorDevice.class);
        DirectParameterCache cache = new DirectParameterCache();
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("cursorDevice", cursorDevice);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("cursorDevice")).thenReturn(cursorDevice);
        when(graph.directParameterCache()).thenReturn(cache);

        assertSame(cache, resolver.resolve("cursorDevice.directParameters").target());
    }

    @Test
    void testDirectParametersEdgeIgnoredOnNonDeviceTarget() {
        // "directParameters" isn't a real Transport member — falls through to
        // the ordinary unknown-member error instead of the synthesized edge.
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("transport", transport);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("transport")).thenReturn(transport);

        BitwigApiException e = assertThrows(BitwigApiException.class,
            () -> resolver.resolve("transport.directParameters"));
        assertTrue(e.getMessage().contains("directParameters"));
        verify(graph, never()).directParameterCache();
    }

    // ---- Task 10 (Cycle 2): popup browser "items" edge ----

    @Test
    void testItemsEdgeOnBrowserColumn() throws Exception {
        var column = mock(com.bitwig.extension.controller.api.BrowserFilterColumn.class);
        var bank = mock(com.bitwig.extension.controller.api.BrowserFilterItemBank.class);
        var browser = mock(com.bitwig.extension.controller.api.PopupBrowser.class);
        when(browser.deviceColumn()).thenReturn(column);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("browser", browser);
        when(graph.roots()).thenReturn(roots);
        when(graph.rootOrNull("browser")).thenReturn(browser);
        when(graph.itemBankForColumn(column)).thenReturn(bank);

        assertSame(bank, resolver.resolve("browser.deviceColumn.items").target());
    }
}
