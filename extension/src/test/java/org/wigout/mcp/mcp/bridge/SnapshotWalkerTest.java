package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.Clip;
import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.NoteStep;
import com.bitwig.extension.controller.api.Project;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableColorValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.StringArrayValue;
import com.bitwig.extension.controller.api.StringValue;
import com.bitwig.extension.controller.api.Track;
import com.bitwig.extension.controller.api.TrackBank;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extension.controller.api.Parameter;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnapshotWalkerTest {

    @Mock private BridgeGraph graph;
    @Mock private Transport transport;
    @Mock private TrackBank trackBank;

    private PathResolver resolver;
    private SnapshotWalker walker;

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
        walker = new SnapshotWalker(resolver, graph, new ValueReader());
    }

    @Test
    void testSubtreeEmitsReadableValuesAndSkipsGuardErrors() throws Exception {
        SettableBooleanValue playing = mock(SettableBooleanValue.class);
        when(playing.get()).thenReturn(true);
        Parameter tempo = mock(Parameter.class);
        when(tempo.get()).thenThrow(new RuntimeException(
            "Either call markInterested() or add at least one observer in init"));
        when(transport.isPlaying()).thenReturn(playing);
        when(transport.tempo()).thenReturn(tempo);

        Map<String, Object> out = walker.subtree("transport", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) out.get("values");
        assertEquals(true, values.get("transport.isPlaying"));
        assertFalse(values.containsKey("transport.tempo")); // guard-error → silently skipped
        assertNull(out.get("truncated"));
    }

    @Test
    void testSubtreeWalksBankItems() throws Exception {
        when(trackBank.getSizeOfBank()).thenReturn(2);
        for (int i = 0; i < 2; i++) {
            Track track = mock(Track.class);
            SettableStringValue name = mock(SettableStringValue.class);
            when(name.get()).thenReturn("T" + i);
            when(track.name()).thenReturn(name);
            when(trackBank.getItemAt(i)).thenReturn(track);
        }

        Map<String, Object> out = walker.subtree("tracks", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) out.get("values");
        assertEquals("T0", values.get("tracks[0].name"));
        assertEquals("T1", values.get("tracks[1].name"));
    }

    @Test
    void testBatchIsolatesPerPathErrors() {
        SettableBooleanValue playing = mock(SettableBooleanValue.class);
        when(playing.get()).thenReturn(true);
        when(transport.isPlaying()).thenReturn(playing);

        List<Map<String, Object>> entries =
            walker.batch(List.of("transport.isPlaying", "nope.nothing"));
        assertEquals(2, entries.size());
        assertNotNull(entries.get(0).get("value"));
        assertNull(entries.get(0).get("error"));
        assertNotNull(entries.get(1).get("error"));
    }

    @Test
    void testTruncationFlagAtEntryCap() throws Exception {
        when(trackBank.getSizeOfBank()).thenReturn(3);
        for (int i = 0; i < 3; i++) {
            Track track = mock(Track.class);
            SettableStringValue name = mock(SettableStringValue.class);
            when(name.get()).thenReturn("T" + i);
            when(track.name()).thenReturn(name);
            when(trackBank.getItemAt(i)).thenReturn(track);
        }
        SnapshotWalker tiny = new SnapshotWalker(resolver, graph, new ValueReader(), 2); // test cap ctor

        Map<String, Object> out = tiny.subtree("tracks", 2);
        assertEquals(Boolean.TRUE, out.get("truncated"));
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) out.get("values");
        assertEquals(2, values.size());
    }

    // --- fix-pass coverage: compact encodings, synthesized edges, depth semantics ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> subtreeValues(String path, int depth) {
        return (Map<String, Object>) walker.subtree(path, depth).get("values");
    }

    @Test
    void testColorValueEmitsRgbaList() {
        Track track = mock(Track.class);
        SettableColorValue color = mock(SettableColorValue.class);
        when(color.red()).thenReturn(0.25f);
        when(color.green()).thenReturn(0.5f);
        when(color.blue()).thenReturn(0.75f);
        when(color.alpha()).thenReturn(1.0f);
        when(track.color()).thenReturn(color);
        when(trackBank.getSizeOfBank()).thenReturn(1);
        when(trackBank.getItemAt(0)).thenReturn(track);

        Map<String, Object> values = subtreeValues("tracks", 2);
        assertEquals(List.of(0.25f, 0.5f, 0.75f, 1.0f), values.get("tracks[0].color"));
    }

    @Test
    void testStringArrayValueEmitsStringList() {
        CursorDevice cursorDevice = mock(CursorDevice.class);
        StringArrayValue slotNames = mock(StringArrayValue.class);
        when(slotNames.get()).thenReturn(new String[] {"FX1", "FX2"});
        when(cursorDevice.slotNames()).thenReturn(slotNames);
        when(graph.rootOrNull("cursorDevice")).thenReturn(cursorDevice);

        Map<String, Object> values = subtreeValues("cursorDevice", 1);
        assertEquals(List.of("FX1", "FX2"), values.get("cursorDevice.slotNames"));
    }

    @Test
    void testParameterEmitsValueAndDisplayed() {
        Parameter tempo = mock(Parameter.class);
        StringValue displayed = mock(StringValue.class);
        when(tempo.get()).thenReturn(0.5);
        when(tempo.displayedValue()).thenReturn(displayed);
        when(displayed.get()).thenReturn("120.000 BPM");
        when(transport.tempo()).thenReturn(tempo);

        Map<String, Object> values = subtreeValues("transport", 2);
        assertEquals(Map.of("value", 0.5, "displayed", "120.000 BPM"), values.get("transport.tempo"));
    }

    @Test
    void testParameterDisplayedGuardErrorKeepsValue() {
        Parameter tempo = mock(Parameter.class);
        StringValue displayed = mock(StringValue.class);
        when(tempo.get()).thenReturn(0.5);
        when(tempo.displayedValue()).thenReturn(displayed);
        when(displayed.get()).thenThrow(new RuntimeException(
            "Either call markInterested() or add at least one observer in init"));
        when(transport.tempo()).thenReturn(tempo);

        Map<String, Object> values = subtreeValues("transport", 2);
        assertEquals(Map.of("value", 0.5), values.get("transport.tempo")); // displayed omitted, value kept
    }

    @Test
    void testClipEmitsNotesEdge() {
        Clip clip = mock(Clip.class);
        when(graph.rootOrNull("cursorClip")).thenReturn(clip);
        NoteStepCache cache = new NoteStepCache();
        NoteStep step = mock(NoteStep.class);
        when(step.channel()).thenReturn(0);
        when(step.x()).thenReturn(4);
        when(step.y()).thenReturn(64);
        when(step.state()).thenReturn(NoteStep.State.NoteOn);
        cache.onNoteStep(step);
        when(graph.noteStepCache()).thenReturn(cache);

        Map<String, Object> values = subtreeValues("cursorClip", 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notes = (List<Map<String, Object>>) values.get("cursorClip.notes");
        assertEquals(1, notes.size());
        assertEquals("NoteOn", notes.get(0).get("state"));
        assertEquals(4, notes.get(0).get("x"));
    }

    @Test
    void testClipNotesThrowIsSkippedAndWalkContinues() {
        Clip clip = mock(Clip.class);
        BooleanValue exists = mock(BooleanValue.class);
        when(exists.get()).thenReturn(true);
        when(clip.exists()).thenReturn(exists);
        when(graph.rootOrNull("cursorClip")).thenReturn(clip);
        NoteStepCache cache = new NoteStepCache();
        NoteStep step = mock(NoteStep.class);
        when(step.state()).thenReturn(NoteStep.State.NoteOn);
        when(step.velocity()).thenThrow(new RuntimeException("boom")); // throws inside noteStepMap
        cache.onNoteStep(step);
        when(graph.noteStepCache()).thenReturn(cache);

        Map<String, Object> out = walker.subtree("cursorClip", 2); // must not throw
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) out.get("values");
        assertEquals(true, values.get("cursorClip.exists")); // collected before the edge — retained
        assertFalse(values.containsKey("cursorClip.notes")); // throwing cache read skipped silently
        assertNull(out.get("truncated"));
    }

    @Test
    void testTrackDevicesEdgeEmitsDeviceValues() {
        Track track = mock(Track.class);
        when(trackBank.getSizeOfBank()).thenReturn(1);
        when(trackBank.getItemAt(0)).thenReturn(track);
        DeviceBank deviceBank = mock(DeviceBank.class);
        Device device = mock(Device.class);
        StringValue name = mock(StringValue.class);
        when(name.get()).thenReturn("EQ+");
        when(device.name()).thenReturn(name);
        when(deviceBank.getSizeOfBank()).thenReturn(1);
        when(deviceBank.getItemAt(0)).thenReturn(device);
        when(graph.deviceBankForTrack(0)).thenReturn(deviceBank);

        Map<String, Object> values = subtreeValues("tracks", 2);
        assertEquals("EQ+", values.get("tracks[0].devices[0].name"));
    }

    @Test
    void testObjectToObjectValueMultiHopRespectsDepth() {
        Project project = mock(Project.class);
        Track rootGroup = mock(Track.class);
        SettableStringValue name = mock(SettableStringValue.class);
        when(name.get()).thenReturn("Root");
        when(rootGroup.name()).thenReturn(name);
        when(project.getRootTrackGroup()).thenReturn(rootGroup);
        when(graph.rootOrNull("project")).thenReturn(project);

        Map<String, Object> deep = subtreeValues("project", 2); // object → object → Value: 2 hops
        assertEquals("Root", deep.get("project.getRootTrackGroup.name"));

        Map<String, Object> shallow = subtreeValues("project", 1); // budget spent on hop 1
        assertFalse(shallow.containsKey("project.getRootTrackGroup.name"));
    }
}
