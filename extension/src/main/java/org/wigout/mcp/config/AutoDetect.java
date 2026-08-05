package org.wigout.mcp.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AutoDetect {

    public static Map<String, String> scan() {
        return scan(System.getProperty("os.name", ""), System.getProperty("user.home"),
            System.getenv("OneDrive"), System.getenv("OneDriveConsumer"));
    }

    static Map<String, String> scan(String osName, String userHome, String oneDrive, String oneDriveConsumer) {
        Map<String, String> candidates = new LinkedHashMap<>();
        if (osName == null || !osName.toLowerCase(Locale.ROOT).contains("win")) {
            // macOS/Linux default Bitwig folder layout is unverified - no candidate
            // guessed rather than risking a silently-wrong suggestion (see
            // docs/superpowers/plans/2026-07-31-cross-platform-user-config.md, Global Constraints).
            return candidates;
        }
        String redirected = oneDrive != null ? oneDrive : oneDriveConsumer;
        Path documentsBase = null;
        if (redirected != null) {
            Path redirectedDocs = Path.of(redirected, "Documents");
            if (Files.isDirectory(redirectedDocs)) {
                documentsBase = redirectedDocs;
            }
        }
        if (documentsBase == null) {
            documentsBase = Path.of(userHome, "Documents");
        }
        Path projects = documentsBase.resolve("Bitwig Studio").resolve("Projects");
        if (Files.isDirectory(projects)) {
            candidates.put("projects", projects.toString());
        }
        return candidates;
    }
}
