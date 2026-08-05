package org.wigout.mcp.mcp.tool;

import com.bitwig.extension.controller.api.CursorDevice;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.Transport;
import com.bitwig.extension.controller.api.TrackBank;
import org.wigout.mcp.bitwig.BitwigApiFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.mcp.bridge.ArgCoercer;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ValueReader;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BwCallToolTest {

    @Mock private PathResolver resolver;
    @Mock private ValueReader reader;
    @Mock private StructuredLogger structuredLogger;
    @Mock private Logger baseLogger;
    @Mock private StructuredLogger.TimedOperation timedOperation;
    @Mock private McpSyncServerExchange exchange;
    @Mock private Transport transport;
    @Mock private TrackBank trackBank;
    @Mock private BitwigApiFacade facade;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ArgCoercer coercer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(structuredLogger.getBaseLogger()).thenReturn(baseLogger);
        when(structuredLogger.generateOperationId()).thenReturn("op-1");
        when(structuredLogger.startTimedOperation(anyString(), anyString(), any())).thenReturn(timedOperation);
        coercer = new ArgCoercer(resolver);
    }

    private McpSchema.CallToolResult call(Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec =
            BwCallTool.specification(resolver, coercer, reader, facade, structuredLogger);
        return spec.callHandler().apply(exchange, McpSchema.CallToolRequest.builder()
            .name("bw_call").arguments(arguments).build());
    }

    @Test
    void testInvokesVoidMethod() throws Exception {
        when(resolver.resolve("transport")).thenReturn(new PathResolver.Resolution(transport, "transport"));

        McpSchema.CallToolResult result = call(Map.of("path", "transport", "method", "play"));

        assertFalse(result.isError());
        verify(transport).play();
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertEquals("invoked", data.get("status").asText());
        assertFalse(data.get("verified").asBoolean()); // void = unconfirmed, honest
    }

    @Test
    void testInvokesWithCoercedArgs() throws Exception {
        when(resolver.resolve("tracks")).thenReturn(new PathResolver.Resolution(trackBank, "tracks"));

        McpSchema.CallToolResult result = call(Map.of("path", "tracks", "method", "scrollBy", "args", List.of(8)));

        assertFalse(result.isError());
        verify(trackBank).scrollBy(8);
    }

    @Test
    void testDirectParameterWriteOnCursorDeviceRefreshesAndConfirms() throws Exception {
        CursorDevice cursorDevice = mock(CursorDevice.class);
        when(resolver.resolve("cursorDevice")).thenReturn(new PathResolver.Resolution(cursorDevice, "cursorDevice"));
        when(facade.getCursorDevice()).thenReturn(cursorDevice);
        when(facade.refreshDirectParametersAfterWrite("CONTENTS/PID2")).thenReturn(0.5005);

        McpSchema.CallToolResult result = call(Map.of("path", "cursorDevice",
            "method", "setDirectParameterValueNormalized", "args", List.of("CONTENTS/PID2", 500, 1000)));

        assertFalse(result.isError());
        verify(cursorDevice).setDirectParameterValueNormalized(eq("CONTENTS/PID2"), any(), any());
        verify(facade).refreshDirectParametersAfterWrite("CONTENTS/PID2");
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertTrue(data.get("verified").asBoolean(), "successful refresh confirms the write");
        assertEquals(0.5005, data.get("value").asDouble(), 1e-9);
    }

    @Test
    void testDirectParameterWriteReportsUnconfirmedWhenRefreshFails() throws Exception {
        CursorDevice cursorDevice = mock(CursorDevice.class);
        when(resolver.resolve("cursorDevice")).thenReturn(new PathResolver.Resolution(cursorDevice, "cursorDevice"));
        when(facade.getCursorDevice()).thenReturn(cursorDevice);
        when(facade.refreshDirectParametersAfterWrite(anyString())).thenReturn(null); // couldn't bounce/settle

        McpSchema.CallToolResult result = call(Map.of("path", "cursorDevice",
            "method", "setDirectParameterValueNormalized", "args", List.of("CONTENTS/PID2", 500, 1000)));

        assertFalse(result.isError());
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertFalse(data.get("verified").asBoolean(), "failed refresh must not fabricate confirmation");
        assertTrue(data.get("note").asText().contains("reselect"), "honest recovery hint");
    }

    @Test
    @SuppressWarnings("deprecation") // referencing getTrack in verify() would trip -Werror otherwise
    void testDeprecatedMethodIsRefused() throws Exception {
        when(resolver.resolve("tracks")).thenReturn(new PathResolver.Resolution(trackBank, "tracks"));

        McpSchema.CallToolResult result = call(Map.of("path", "tracks", "method", "getTrack", "args", List.of(0)));

        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("deprecated"));
        assertTrue(text.contains("getItemAt"));
        verify(trackBank, never()).getTrack(anyInt());
    }

    @Test
    void testReadBackGuardErrorReturnsInvokedStatusWithReadNote() throws Exception {
        // The invocation itself succeeds (isPlaying() returns a live Value),
        // but the returned Value wasn't init-marked, so ValueReader.read()
        // translates Bitwig's guard error into an OPERATION_FAILED
        // BitwigApiException — bw_call must report the successful invocation
        // rather than failing the whole call over an unreadable result.
        when(resolver.resolve("transport")).thenReturn(new PathResolver.Resolution(transport, "transport"));
        SettableBooleanValue isPlayingValue = mock(SettableBooleanValue.class);
        when(transport.isPlaying()).thenReturn(isPlayingValue);
        when(reader.read(isPlayingValue)).thenThrow(new BitwigApiException(ErrorCode.OPERATION_FAILED, "bw_get",
            "This value was not registered during extension startup (Bitwig requires init-time interest marking)."));

        McpSchema.CallToolResult result = call(Map.of("path", "transport", "method", "isPlaying"));

        assertFalse(result.isError());
        JsonNode data = objectMapper.readTree(((McpSchema.TextContent) result.content().get(0)).text()).get("data");
        assertEquals("invoked", data.get("status").asText());
        assertFalse(data.has("result"));
        assertTrue(data.get("read_note").asText().contains("could not be"));
        assertTrue(data.get("read_note").asText().contains("not registered during extension startup"));
    }

    @Test
    void testUnknownMethodPointsToBwDescribe() throws Exception {
        when(resolver.resolve("transport")).thenReturn(new PathResolver.Resolution(transport, "transport"));

        McpSchema.CallToolResult result = call(Map.of("path", "transport", "method", "zzz"));

        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("zzz"));
        assertTrue(text.contains("bw_describe"));
    }

    @Test
    void testExcludedMethodIsRefused() throws Exception {
        // Bank.scrollForwardsAction() returns HardwareActionBindable — excluded
        // by return type per the scope constraint.
        when(resolver.resolve("tracks")).thenReturn(new PathResolver.Resolution(trackBank, "tracks"));
        McpSchema.CallToolResult result = call(Map.of("path", "tracks", "method", "scrollForwardsAction"));

        assertTrue(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.contains("excluded"));
        verify(trackBank, never()).scrollForwardsAction();
    }

    // ---- Task 10 (Cycle 2): closed-browser guard on commit/cancel ----

    @Test
    void testCommitOnClosedBrowserIsRefusedWithRecipeHint() throws Exception {
        var browser = mock(com.bitwig.extension.controller.api.PopupBrowser.class);
        var exists = mock(com.bitwig.extension.controller.api.BooleanValue.class);
        when(browser.exists()).thenReturn(exists);
        when(exists.get()).thenReturn(false);
        when(resolver.resolve("browser")).thenReturn(new PathResolver.Resolution(browser, "browser"));

        McpSchema.CallToolResult result = call(Map.of("path", "browser", "method", "commit"));
        assertTrue(result.isError());
        assertTrue(((McpSchema.TextContent) result.content().get(0)).text().contains("browse"));
    }
}
