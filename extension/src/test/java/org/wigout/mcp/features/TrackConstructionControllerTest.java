package org.wigout.mcp.features;

import org.wigout.mcp.bitwig.BitwigApiFacade;
import org.wigout.mcp.common.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the TrackConstructionController class.
 */
public class TrackConstructionControllerTest {

    @Mock
    private BitwigApiFacade mockBitwigApiFacade;

    @Mock
    private Logger mockLogger;

    private TrackConstructionController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 3 verify attempts, zero delay: fast, deterministic tests
        controller = new TrackConstructionController(mockBitwigApiFacade, mockLogger, 3, 0);
    }

    @Test
    void testCreateTrackVerifiedAppend() throws Exception {
        // Snapshot ["Drums"], then the poll sees the new track appended.
        when(mockBitwigApiFacade.getExistingTrackNames())
            .thenReturn(List.of("Drums"), List.of("Drums", "Inst 2"));
        when(mockBitwigApiFacade.getTrackNameByIndex(1)).thenReturn("Inst 2");

        Map<String, Object> result = controller.createTrack("instrument", -1, null);

        verify(mockBitwigApiFacade).createTrack("instrument", -1);
        assertEquals(1, result.get("track_index"));
        assertEquals("Inst 2", result.get("track_name"));
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testCreateTrackVerifiedAtPosition() throws Exception {
        // Insert at index 0: first divergence between before/after lists is index 0.
        when(mockBitwigApiFacade.getExistingTrackNames())
            .thenReturn(List.of("Drums", "Bass"), List.of("Audio 1", "Drums", "Bass"));
        when(mockBitwigApiFacade.getTrackNameByIndex(0)).thenReturn("Audio 1");

        Map<String, Object> result = controller.createTrack("audio", 0, null);

        verify(mockBitwigApiFacade).createTrack("audio", 0);
        assertEquals(0, result.get("track_index"));
        assertEquals("Audio 1", result.get("track_name"));
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testCreateTrackWithNameRenames() throws Exception {
        when(mockBitwigApiFacade.getExistingTrackNames())
            .thenReturn(
                List.of("Drums"),                 // before snapshot
                List.of("Drums", "Inst 2"),       // appear-poll hit + after snapshot
                List.of("Drums", "Inst 2"),
                List.of("Drums", "Lead"));        // rename-poll hit
        when(mockBitwigApiFacade.getTrackNameByIndex(1)).thenReturn("Lead");

        Map<String, Object> result = controller.createTrack("instrument", -1, "Lead");

        verify(mockBitwigApiFacade).renameTrackByIndex(1, "Lead");
        assertEquals(1, result.get("track_index"));
        assertEquals("Lead", result.get("track_name"));
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testCreateTrackWithNameRenameUnverified() throws Exception {
        // Track appears, but the rename never lands within the poll budget:
        // stays "Inst 2" forever instead of becoming "Lead".
        when(mockBitwigApiFacade.getExistingTrackNames())
            .thenReturn(List.of("Drums"), List.of("Drums", "Inst 2"));
        when(mockBitwigApiFacade.getTrackNameByIndex(1)).thenReturn("Inst 2");

        Map<String, Object> result = controller.createTrack("instrument", -1, "Lead");

        verify(mockBitwigApiFacade).renameTrackByIndex(1, "Lead");
        assertEquals(1, result.get("track_index"));
        assertEquals(false, result.get("verified"));
        assertTrue(((String) result.get("message")).contains("rename"));
    }

    @Test
    void testCreateTrackUnverifiedWhenNothingAppears() throws Exception {
        // Cached state never changes: verify must time out, not spin forever.
        when(mockBitwigApiFacade.getExistingTrackNames()).thenReturn(List.of("Drums"));

        Map<String, Object> result = controller.createTrack("instrument", -1, null);

        verify(mockBitwigApiFacade).createTrack("instrument", -1);
        assertEquals(false, result.get("verified"));
        assertFalse(result.containsKey("track_index"));
        assertTrue(((String) result.get("message")).contains("list_tracks"));
    }

    /** Builds a device map the way BitwigApiFacade.getDevicesOnTrack does (typed Map<String,Object>). */
    private static Map<String, Object> device(int index, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("index", index);
        m.put("name", name);
        return m;
    }

    @Test
    void testRenameTrackByIndexVerified() throws Exception {
        when(mockBitwigApiFacade.getTrackNameByIndex(1)).thenReturn("Old");
        when(mockBitwigApiFacade.getExistingTrackNames())
            .thenReturn(List.of("Drums", "New"));

        Map<String, Object> result = controller.renameTrack(1, null, "New");

        verify(mockBitwigApiFacade).renameTrackByIndex(1, "New");
        assertEquals(1, result.get("track_index"));
        assertEquals("New", result.get("track_name"));
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testRenameTrackByNameResolvesIndex() throws Exception {
        when(mockBitwigApiFacade.findTrackIndexByName("Drums")).thenReturn(0);
        when(mockBitwigApiFacade.getExistingTrackNames())
            .thenReturn(List.of("Percussion", "Bass"));

        Map<String, Object> result = controller.renameTrack(null, "Drums", "Percussion");

        verify(mockBitwigApiFacade).renameTrackByIndex(0, "Percussion");
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testDeleteTrackVerified() throws Exception {
        when(mockBitwigApiFacade.getTrackNameByIndex(1)).thenReturn("Doomed");
        when(mockBitwigApiFacade.getExistingTrackNames())
            .thenReturn(List.of("Drums", "Doomed"), List.of("Drums"));

        Map<String, Object> result = controller.deleteTrack(1, null);

        verify(mockBitwigApiFacade).deleteTrackByIndex(1);
        assertEquals("Doomed", result.get("deleted_track"));
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testInsertDeviceByUuidVerified() throws Exception {
        String uuid = "8f58138b-03aa-4e9d-83bd-a038c99a4ed5";
        when(mockBitwigApiFacade.getTrackNameByIndex(0)).thenReturn("Synths");
        when(mockBitwigApiFacade.getDevicesOnTrack(0, null, null))
            .thenReturn(
                List.of(),                          // before snapshot
                List.of(device(0, "Polymer")),      // poll hit
                List.of(device(0, "Polymer")));     // final read

        Map<String, Object> result = controller.insertDevice(0, null, uuid, null);

        verify(mockBitwigApiFacade).insertBitwigDeviceAtEndOfChain(0, UUID.fromString(uuid));
        assertEquals(0, result.get("track_index"));
        assertEquals(List.of("Polymer"), result.get("devices"));
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testInsertPresetFileVerified() throws Exception {
        when(mockBitwigApiFacade.getTrackNameByIndex(2)).thenReturn("Bass");
        when(mockBitwigApiFacade.getDevicesOnTrack(2, null, null))
            .thenReturn(
                List.of(device(0, "EQ+")),
                List.of(device(0, "EQ+"), device(1, "Polymer")));

        Map<String, Object> result = controller.insertDevice(2, null, null, "C:/presets/fat.bwpreset");

        verify(mockBitwigApiFacade).insertFileAtEndOfChain(2, "C:/presets/fat.bwpreset");
        assertEquals(List.of("EQ+", "Polymer"), result.get("devices"));
        assertEquals(true, result.get("verified"));
    }

    @Test
    void testInsertDeviceUnverifiedReportsGuidance() throws Exception {
        // Device list never changes — e.g. UUID unknown to this Bitwig version.
        when(mockBitwigApiFacade.getTrackNameByIndex(0)).thenReturn("Synths");
        when(mockBitwigApiFacade.getDevicesOnTrack(0, null, null)).thenReturn(List.of());

        Map<String, Object> result = controller.insertDevice(0, null, "8f58138b-03aa-4e9d-83bd-a038c99a4ed5", null);

        assertEquals(false, result.get("verified"));
        assertTrue(((String) result.get("message")).toLowerCase().contains("uuid"));
    }
}
