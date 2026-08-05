package org.wigout.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AutoDetectTest {

    @Test
    void nonWindowsReturnsNoCandidates(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("Documents/Bitwig Studio/Projects"));

        Map<String, String> result = AutoDetect.scan("Mac OS X", tempDir.toString(), null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    void windowsWithoutOneDriveUsesPlainDocumentsWhenProjectsFolderExists(@TempDir Path tempDir) throws Exception {
        Path expected = Files.createDirectories(tempDir.resolve("Documents/Bitwig Studio/Projects"));

        Map<String, String> result = AutoDetect.scan("Windows 11", tempDir.toString(), null, null);

        assertEquals(expected.toString(), result.get("projects"));
    }

    @Test
    void windowsWithOneDriveRedirectPrefersRedirectedDocuments(@TempDir Path tempDir) throws Exception {
        Path oneDrive = tempDir.resolve("OneDrive");
        Path expected = Files.createDirectories(oneDrive.resolve("Documents/Bitwig Studio/Projects"));
        Files.createDirectories(tempDir.resolve("Documents/Bitwig Studio/Projects")); // plain fallback also exists

        Map<String, String> result = AutoDetect.scan("Windows 11", tempDir.toString(), oneDrive.toString(), null);

        assertEquals(expected.toString(), result.get("projects"));
    }

    @Test
    void windowsWithNoProjectsFolderReturnsNoCandidate(@TempDir Path tempDir) {
        Map<String, String> result = AutoDetect.scan("Windows 11", tempDir.toString(), null, null);

        assertTrue(result.isEmpty());
    }
}
