package org.wigout.mcp.mcp.bridge;

import com.bitwig.extension.controller.api.NoteStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoteStepCacheTest {

    private NoteStep step(int ch, int x, int y, NoteStep.State state) {
        NoteStep s = mock(NoteStep.class);
        when(s.channel()).thenReturn(ch);
        when(s.x()).thenReturn(x);
        when(s.y()).thenReturn(y);
        when(s.state()).thenReturn(state);
        return s;
    }

    @Test
    void testAddUpdateRemoveViaObserverCallback() {
        NoteStepCache cache = new NoteStepCache();
        NoteStep on = step(0, 4, 64, NoteStep.State.NoteOn);
        cache.onNoteStep(on);
        assertEquals(1, cache.size());
        assertSame(on, cache.find(0, 4, 64).orElseThrow());

        NoteStep updated = step(0, 4, 64, NoteStep.State.NoteOn);
        cache.onNoteStep(updated);
        assertEquals(1, cache.size());
        assertSame(updated, cache.find(0, 4, 64).orElseThrow());

        cache.onNoteStep(step(0, 4, 64, NoteStep.State.Empty));
        assertEquals(0, cache.size());
        assertTrue(cache.find(0, 4, 64).isEmpty());
    }

    @Test
    void testAllIsSortedByChannelThenXThenY() {
        NoteStepCache cache = new NoteStepCache();
        cache.onNoteStep(step(1, 0, 60, NoteStep.State.NoteOn));
        cache.onNoteStep(step(0, 8, 62, NoteStep.State.NoteOn));
        cache.onNoteStep(step(0, 4, 64, NoteStep.State.NoteOn));
        var all = cache.all();
        assertEquals(3, all.size());
        assertEquals(4, all.get(0).x());   // (0,4,64)
        assertEquals(8, all.get(1).x());   // (0,8,62)
        assertEquals(1, all.get(2).channel()); // (1,0,60)
    }

    @Test
    void testNoteSustainIsCachedToo() {
        // Sustain cells matter for read-back of long notes; only Empty means "no note here".
        NoteStepCache cache = new NoteStepCache();
        cache.onNoteStep(step(0, 5, 60, NoteStep.State.NoteSustain));
        assertEquals(1, cache.size());
    }
}
