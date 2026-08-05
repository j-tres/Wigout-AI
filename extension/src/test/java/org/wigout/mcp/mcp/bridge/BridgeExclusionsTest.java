package org.wigout.mcp.mcp.bridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BridgeExclusionsTest {

    @Test
    void testHardwareNamesAreExcluded() {
        assertTrue(BridgeExclusions.isExcludedTypeName("HardwareSurface"));
        assertTrue(BridgeExclusions.isExcludedTypeName("HardwareActionBindable"));
        // The API's typo'd names lack the trailing 'e' — must still be caught.
        assertTrue(BridgeExclusions.isExcludedTypeName("RelativeHardwarControlBindable"));
        assertTrue(BridgeExclusions.isExcludedTypeName("AbsoluteHardwarControlBindable"));
        assertTrue(BridgeExclusions.isExcludedTypeName("MidiIn"));
        assertTrue(BridgeExclusions.isExcludedTypeName("MidiOut"));
        assertTrue(BridgeExclusions.isExcludedTypeName("NoteInput"));
        assertTrue(BridgeExclusions.isExcludedTypeName("OscModule"));
    }

    @Test
    void testProjectLevelNamesAreIncluded() {
        assertFalse(BridgeExclusions.isExcludedTypeName("Transport"));
        assertFalse(BridgeExclusions.isExcludedTypeName("Track"));
        assertFalse(BridgeExclusions.isExcludedTypeName("SettableRangedValue"));
        assertFalse(BridgeExclusions.isExcludedTypeName("PopupBrowser"));
    }
}
