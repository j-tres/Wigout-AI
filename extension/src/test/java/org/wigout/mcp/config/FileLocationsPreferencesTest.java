package org.wigout.mcp.config;

import com.bitwig.extension.callback.NoArgsCallback;
import com.bitwig.extension.callback.StringValueChangedCallback;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Preferences;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Signal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.wigout.mcp.common.Logger;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileLocationsPreferencesTest {

    @Mock private Logger mockLogger;
    @Mock private ControllerHost mockHost;
    @Mock private Preferences mockPreferences;
    @Mock private UserConfigStore mockConfigStore;
    @Mock private SettableStringValue mockProjectsSetting;
    @Mock private SettableStringValue mockLibrarySetting;
    @Mock private SettableStringValue mockSoundContentSetting;
    @Mock private SettableStringValue mockMusicSetting;
    @Mock private SettableStringValue mockAudioAnalysisCacheSetting;
    @Mock private SettableStringValue mockControllerScriptsSetting;
    @Mock private Signal mockAutoDetectSignal;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockHost.getPreferences()).thenReturn(mockPreferences);

        UserConfig config = new UserConfig();
        config.locations.put("projects", "C:/Music/Projects");
        when(mockConfigStore.load()).thenReturn(config);

        when(mockPreferences.getStringSetting(eq("Projects Folder"), eq("File Locations"), anyInt(), anyString()))
            .thenReturn(mockProjectsSetting);
        when(mockPreferences.getStringSetting(eq("Library Folder"), eq("File Locations"), anyInt(), anyString()))
            .thenReturn(mockLibrarySetting);
        when(mockPreferences.getStringSetting(eq("Sound Content Folder"), eq("File Locations"), anyInt(), anyString()))
            .thenReturn(mockSoundContentSetting);
        when(mockPreferences.getStringSetting(eq("Music Folder"), eq("File Locations"), anyInt(), anyString()))
            .thenReturn(mockMusicSetting);
        when(mockPreferences.getStringSetting(eq("Audio Analysis Cache Folder"), eq("File Locations"), anyInt(), anyString()))
            .thenReturn(mockAudioAnalysisCacheSetting);
        when(mockPreferences.getStringSetting(eq("Controller Scripts Folder"), eq("File Locations"), anyInt(), anyString()))
            .thenReturn(mockControllerScriptsSetting);
        when(mockPreferences.getSignalSetting(eq("Auto-detect"), eq("File Locations"), anyString()))
            .thenReturn(mockAutoDetectSignal);
    }

    @Test
    void constructorLoadsConfigAndMirrorsProjectsIntoItsSetting() {
        new FileLocationsPreferences(mockLogger, mockHost, mockConfigStore);

        verify(mockConfigStore).load();
        verify(mockPreferences).getStringSetting("Projects Folder", "File Locations", 260, "C:/Music/Projects");
    }

    @Test
    @SuppressWarnings("unchecked")
    void editingASettingSavesUpdatedConfig() {
        FileLocationsPreferences prefs = new FileLocationsPreferences(mockLogger, mockHost, mockConfigStore);
        ArgumentCaptor<StringValueChangedCallback> captor = ArgumentCaptor.forClass(StringValueChangedCallback.class);
        verify(mockProjectsSetting).addValueObserver(captor.capture());

        captor.getValue().valueChanged("D:/NewProjects");

        ArgumentCaptor<UserConfig> savedConfig = ArgumentCaptor.forClass(UserConfig.class);
        verify(mockConfigStore).save(savedConfig.capture());
        assertEquals("D:/NewProjects", savedConfig.getValue().locations.get("projects"));
        assertEquals("D:/NewProjects", prefs.currentLocations().get("projects"));
    }

    @Test
    void constructorForcesConfigValuesOntoSettingsEvenIfBitwigHadItsOwnPersistedValue() {
        new FileLocationsPreferences(mockLogger, mockHost, mockConfigStore);

        verify(mockProjectsSetting).set("C:/Music/Projects");
    }

    @Test
    void editingASettingToEmptyRemovesItFromConfigInsteadOfPersistingEmptyString() {
        FileLocationsPreferences prefs = new FileLocationsPreferences(mockLogger, mockHost, mockConfigStore);
        ArgumentCaptor<StringValueChangedCallback> captor = ArgumentCaptor.forClass(StringValueChangedCallback.class);
        verify(mockProjectsSetting).addValueObserver(captor.capture());

        captor.getValue().valueChanged("");

        ArgumentCaptor<UserConfig> savedConfig = ArgumentCaptor.forClass(UserConfig.class);
        verify(mockConfigStore).save(savedConfig.capture());
        assertFalse(savedConfig.getValue().locations.containsKey("projects"));
        assertNull(prefs.currentLocations().get("projects"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void autoDetectSignalFillsOnlyBlankSettings() {
        when(mockProjectsSetting.get()).thenReturn("");
        FileLocationsPreferences prefs = new FileLocationsPreferences(
            mockLogger, mockHost, mockConfigStore, () -> Map.of("projects", "D:/Detected/Projects"));
        ArgumentCaptor<NoArgsCallback> captor = ArgumentCaptor.forClass(NoArgsCallback.class);
        verify(mockAutoDetectSignal).addSignalObserver(captor.capture());

        captor.getValue().call();

        verify(mockProjectsSetting).set("D:/Detected/Projects");
    }

    @Test
    @SuppressWarnings("unchecked")
    void autoDetectSignalDoesNotOverwriteAnAlreadyConfiguredSetting() {
        when(mockProjectsSetting.get()).thenReturn("C:/Music/Projects");
        FileLocationsPreferences prefs = new FileLocationsPreferences(
            mockLogger, mockHost, mockConfigStore, () -> Map.of("projects", "D:/Detected/Projects"));
        ArgumentCaptor<NoArgsCallback> captor = ArgumentCaptor.forClass(NoArgsCallback.class);
        verify(mockAutoDetectSignal).addSignalObserver(captor.capture());

        captor.getValue().call();

        verify(mockProjectsSetting, never()).set("D:/Detected/Projects");
    }
}
