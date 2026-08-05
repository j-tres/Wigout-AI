package org.wigout.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wigout.mcp.common.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class UserConfigStoreTest {

    @Test
    void loadReturnsEmptyDefaultsWhenFileMissing(@TempDir Path tempDir) {
        UserConfigStore store = new UserConfigStore(mock(Logger.class), tempDir.resolve("config.json"));

        UserConfig config = store.load();

        assertEquals(1, config.version);
        assertTrue(config.locations.isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsLocations(@TempDir Path tempDir) {
        Path configPath = tempDir.resolve("nested").resolve("config.json");
        UserConfigStore store = new UserConfigStore(mock(Logger.class), configPath);
        UserConfig config = new UserConfig();
        config.locations.put("projects", "C:/Music/Projects");

        store.save(config);

        assertTrue(Files.exists(configPath));
        UserConfig reloaded = store.load();
        assertEquals("C:/Music/Projects", reloaded.locations.get("projects"));
    }

    @Test
    void loadReturnsDefaultsWhenFileIsCorrupt(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, "{ not valid json");
        UserConfigStore store = new UserConfigStore(mock(Logger.class), configPath);

        UserConfig config = store.load();

        assertEquals(1, config.version);
        assertTrue(config.locations.isEmpty());
    }

    @Test
    void loadToleratesUnknownFieldsInsteadOfResettingToDefaults(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        Files.writeString(configPath, "{\"version\":1,\"locations\":{\"projects\":\"C:/Music/Projects\"},\"futureField\":\"unused\"}");
        UserConfigStore store = new UserConfigStore(mock(Logger.class), configPath);

        UserConfig config = store.load();

        assertEquals("C:/Music/Projects", config.locations.get("projects"));
    }
}
