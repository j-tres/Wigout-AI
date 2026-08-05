package org.wigout.mcp.config;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Signal;
import org.wigout.mcp.common.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class FileLocationsPreferences {
    private static final String CATEGORY = "File Locations";
    private static final Map<String, String> LABELS = Map.of(
        "projects", "Projects Folder",
        "library", "Library Folder",
        "soundContent", "Sound Content Folder",
        "music", "Music Folder",
        "audioAnalysisCache", "Audio Analysis Cache Folder",
        "controllerScripts", "Controller Scripts Folder"
    );
    private static final String[] KEY_ORDER = {
        "projects", "library", "soundContent", "music", "audioAnalysisCache", "controllerScripts"
    };

    private final Logger logger;
    private final UserConfigStore configStore;
    private final Supplier<Map<String, String>> autoDetectScanner;
    private final Map<String, SettableStringValue> settings = new LinkedHashMap<>();
    private final UserConfig currentConfig;
    private final Object lock = new Object();

    public FileLocationsPreferences(Logger logger, ControllerHost host, UserConfigStore configStore) {
        this(logger, host, configStore, AutoDetect::scan);
    }

    FileLocationsPreferences(Logger logger, ControllerHost host, UserConfigStore configStore,
                              Supplier<Map<String, String>> autoDetectScanner) {
        this.logger = logger;
        this.configStore = configStore;
        this.autoDetectScanner = autoDetectScanner;
        this.currentConfig = configStore.load();

        Preferences preferences = host.getPreferences();
        for (String key : KEY_ORDER) {
            SettableStringValue setting = preferences.getStringSetting(
                LABELS.get(key), CATEGORY, 260, currentConfig.locations.getOrDefault(key, ""));
            settings.put(key, setting);
        }

        // config.json is the single source of truth: force it onto each setting
        // now, BEFORE observers are registered, so a value Bitwig persisted from
        // a previous session doesn't silently win over (and get echoed back into)
        // config.json.
        for (String key : KEY_ORDER) {
            settings.get(key).set(currentConfig.locations.getOrDefault(key, ""));
        }

        Signal autoDetectSignal = preferences.getSignalSetting(
            "Auto-detect", CATEGORY, "Fill empty File Locations fields from a best-effort scan (saves immediately; already-set fields are left alone)");

        setupChangeListeners(autoDetectSignal);
        logger.info("FileLocationsPreferences: initialized with " + currentConfig.locations.size() + " known location(s)");
    }

    private void setupChangeListeners(Signal autoDetectSignal) {
        for (Map.Entry<String, SettableStringValue> entry : settings.entrySet()) {
            String key = entry.getKey();
            entry.getValue().addValueObserver(newValue -> {
                String normalized = (newValue == null || newValue.isEmpty()) ? null : newValue;
                synchronized (lock) {
                    String existing = currentConfig.locations.get(key);
                    if (!java.util.Objects.equals(normalized, existing)) {
                        if (normalized == null) {
                            currentConfig.locations.remove(key);
                        } else {
                            currentConfig.locations.put(key, normalized);
                        }
                        configStore.save(currentConfig);
                        logger.info("FileLocationsPreferences: " + key + " changed to '" + normalized + "'");
                    }
                }
            });
        }
        autoDetectSignal.addSignalObserver(() -> {
            Map<String, String> candidates = autoDetectScanner.get();
            for (Map.Entry<String, String> candidate : candidates.entrySet()) {
                SettableStringValue setting = settings.get(candidate.getKey());
                if (setting != null && setting.get().isEmpty()) {
                    setting.set(candidate.getValue());
                }
            }
        });
    }

    public Map<String, String> currentLocations() {
        synchronized (lock) {
            return new LinkedHashMap<>(currentConfig.locations);
        }
    }
}
