package org.wigout.mcp.common.data;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the DeviceCatalog class.
 */
class DeviceCatalogTest {

    @Test
    void testLookupIsCaseInsensitiveAndTrims() {
        Optional<DeviceCatalog.Entry> entry = DeviceCatalog.lookup("  polymer ");
        assertTrue(entry.isPresent());
        assertEquals("Polymer", entry.get().name());
        assertEquals("8f58138b-03aa-4e9d-83bd-a038c99a4ed5", entry.get().uuid());
    }

    @Test
    void testLookupUnknownReturnsEmpty() {
        assertTrue(DeviceCatalog.lookup("Not A Device").isEmpty());
        assertTrue(DeviceCatalog.lookup(null).isEmpty());
    }

    @Test
    void testEntriesNotEmptyAndAllUuidsParse() {
        assertFalse(DeviceCatalog.entries().isEmpty());
        for (DeviceCatalog.Entry entry : DeviceCatalog.entries()) {
            assertDoesNotThrow(() -> UUID.fromString(entry.uuid()),
                "Invalid UUID for " + entry.name());
            assertFalse(entry.name().isBlank());
        }
    }

    @Test
    void testAvailableNamesListsEntries() {
        String names = DeviceCatalog.availableNames();
        assertTrue(names.contains("Polymer"));
        assertTrue(names.contains(", "));
    }
}
