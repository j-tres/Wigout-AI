package org.wigout.mcp.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.common.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UserConfigStore {
    private final Logger logger;
    private final Path configPath;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public UserConfigStore(Logger logger) {
        this(logger, Path.of(System.getProperty("user.home"), ".wigout-ai", "config.json"));
    }

    UserConfigStore(Logger logger, Path configPath) {
        this.logger = logger;
        this.configPath = configPath;
    }

    public UserConfig load() {
        if (!Files.exists(configPath)) {
            return new UserConfig();
        }
        try {
            return objectMapper.readValue(configPath.toFile(), UserConfig.class);
        } catch (IOException e) {
            logger.error("UserConfigStore: failed to read " + configPath, e);
            return new UserConfig();
        }
    }

    public void save(UserConfig config) {
        try {
            Files.createDirectories(configPath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        } catch (IOException e) {
            logger.error("UserConfigStore: failed to write " + configPath, e);
        }
    }
}
