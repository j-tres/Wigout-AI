package org.wigout.mcp.mcp.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectParameterCacheTest {

    @Test
    void testIdArrayReplacementPrunesStaleEntries() {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1", "p2"});
        cache.onName("p1", "Cutoff");
        cache.onName("p2", "Resonance");
        cache.onValue("p1", 0.5);
        cache.onValue("p2", 0.25);

        // Cursor device changed — a smaller, disjoint id set is reported.
        cache.onIds(new String[] {"q1"});

        assertEquals(1, cache.size());
        assertEquals(java.util.List.of("q1"), cache.ids());
        assertNull(cache.nameOf("p1"), "stale id's name must be pruned");
        assertNull(cache.valueOf("p1"), "stale id's value must be pruned");
        assertNull(cache.nameOf("p2"));
        assertNull(cache.valueOf("p2"));
    }

    @Test
    void testIdArrayReplacementKeepsSurvivingEntries() {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1", "p2"});
        cache.onName("p1", "Cutoff");
        cache.onValue("p1", 0.5);

        // p1 survives the update, p2 is dropped.
        cache.onIds(new String[] {"p1"});

        assertEquals("Cutoff", cache.nameOf("p1"));
        assertEquals(0.5, cache.valueOf("p1"));
    }

    @Test
    void testNameAndValueUpdatesAreReadable() {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1"});
        cache.onName("p1", "Cutoff");
        cache.onValue("p1", 0.75);

        assertEquals("Cutoff", cache.nameOf("p1"));
        assertEquals(0.75, cache.valueOf("p1"));
    }

    @Test
    void testNaNValueIsStoredAsAbsent() {
        // The API doc: NaN means "value not accessible" — must read back as
        // null (never the Double NaN object, which is not valid JSON).
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1"});
        cache.onValue("p1", 0.5);
        assertEquals(0.5, cache.valueOf("p1"));

        cache.onValue("p1", Double.NaN);
        assertNull(cache.valueOf("p1"));
    }

    @Test
    void testUnknownIdValueIsNullNotThrowing() {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1"});
        assertNull(cache.nameOf("p1"));
        assertNull(cache.valueOf("p1"));
    }

    @Test
    void testOrderingIsPreservedAsReportedByIdObserver() {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"z", "a", "m"});
        assertEquals(java.util.List.of("z", "a", "m"), cache.ids());
    }

    @Test
    void testNullIdsInArrayAreDroppedDefensively() {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1", null, "p2"});
        assertEquals(java.util.List.of("p1", "p2"), cache.ids());
    }

    @Test
    void testGenerationBumpsOnEachIdUpdate() {
        DirectParameterCache cache = new DirectParameterCache();
        long g0 = cache.generation();
        cache.onIds(new String[] {"p1"});
        long g1 = cache.generation();
        cache.onIds(new String[] {"p2"});
        long g2 = cache.generation();
        assertEquals(g0 + 1, g1, "id update bumps generation");
        assertEquals(g0 + 2, g2, "each id update bumps generation (post-write bounce = +2)");
        // name/value updates must NOT bump generation (only device changes do)
        cache.onName("p2", "x");
        cache.onValue("p2", 0.5);
        assertEquals(g2, cache.generation(), "name/value updates do not bump generation");
    }

    @Test
    void testNullIdArrayResultsInEmptyCache() {
        DirectParameterCache cache = new DirectParameterCache();
        cache.onIds(new String[] {"p1"});
        assertEquals(1, cache.size());

        cache.onIds(null);
        assertEquals(0, cache.size());
        assertTrue(cache.ids().isEmpty());
    }
}
