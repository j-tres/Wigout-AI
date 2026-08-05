package org.wigout.mcp.mcp.tool;

import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.features.TrackConstructionController;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InsertDeviceTool.
 */
class InsertDeviceToolTest {

    private static final String POLYMER_UUID = "8f58138b-03aa-4e9d-83bd-a038c99a4ed5";

    @Mock
    private TrackConstructionController controller;
    @Mock
    private StructuredLogger structuredLogger;
    @Mock
    private Logger baseLogger;
    @Mock
    private StructuredLogger.TimedOperation timedOperation;
    @Mock
    private McpSyncServerExchange exchange;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-123");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = InsertDeviceTool.specification(controller, structuredLogger);
        McpSchema.CallToolRequest request = McpSchema.CallToolRequest.builder()
            .name("insert_device")
            .arguments(arguments)
            .build();
        return spec.callHandler().apply(exchange, request);
    }

    @Test
    void testSpecificationCreation() {
        McpServerFeatures.SyncToolSpecification spec = InsertDeviceTool.specification(controller, structuredLogger);
        assertEquals("insert_device", spec.tool().name());
    }

    @Test
    void testInsertByCatalogNameResolvesUuid() throws Exception {
        when(controller.insertDevice(0, null, POLYMER_UUID, null))
            .thenReturn(Map.of("devices", List.of("Polymer"), "verified", true));

        McpSchema.CallToolResult result = call(Map.of("track_index", 0, "device_name", "Polymer"));

        assertFalse(result.isError());
        verify(controller).insertDevice(0, null, POLYMER_UUID, null);
    }

    @Test
    void testInsertByRawUuid() throws Exception {
        when(controller.insertDevice(null, "Synths", POLYMER_UUID, null))
            .thenReturn(Map.of("devices", List.of("Polymer"), "verified", true));

        McpSchema.CallToolResult result = call(Map.of("track_name", "Synths", "device_uuid", POLYMER_UUID));

        assertFalse(result.isError());
        verify(controller).insertDevice(null, "Synths", POLYMER_UUID, null);
    }

    @Test
    void testInsertByPresetPath() throws Exception {
        Path preset = tempDir.resolve("fat-bass.bwpreset");
        Files.writeString(preset, "BtWg-stub");
        when(controller.insertDevice(eq(0), isNull(), isNull(), eq(preset.toString())))
            .thenReturn(Map.of("devices", List.of("Polymer"), "verified", true));

        McpSchema.CallToolResult result = call(Map.of("track_index", 0, "preset_path", preset.toString()));

        assertFalse(result.isError());
        verify(controller).insertDevice(0, null, null, preset.toString());
    }

    @Test
    void testUnknownCatalogNameIsErrorListingAvailable() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 0, "device_name", "Not A Device"));
        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("Polymer")); // error lists available catalog names
        verifyNoInteractions(controller);
    }

    @Test
    void testInvalidUuidIsError() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 0, "device_uuid", "not-a-uuid"));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testMissingPresetFileIsError() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 0, "preset_path", tempDir.resolve("nope.bwpreset").toString()));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testWrongExtensionIsError() throws Exception {
        Path notPreset = tempDir.resolve("song.wav");
        Files.writeString(notPreset, "x");
        McpSchema.CallToolResult result = call(Map.of("track_index", 0, "preset_path", notPreset.toString()));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testMultipleDeviceSelectorsIsError() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 0, "device_name", "Polymer", "device_uuid", POLYMER_UUID));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }

    @Test
    void testNoDeviceSelectorIsError() {
        McpSchema.CallToolResult result = call(Map.of("track_index", 0));
        assertTrue(result.isError());
        verifyNoInteractions(controller);
    }
}
