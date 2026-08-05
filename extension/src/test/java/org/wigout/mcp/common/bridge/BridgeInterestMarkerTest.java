package org.wigout.mcp.common.bridge;

import com.bitwig.extension.controller.api.*;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BridgeInterestMarker reflectively bulk-marks the bridge's readable surface
 * at init (the only time Bitwig 6.1b2 actually honors markInterested()).
 * Depth 2 means: a root/bank-item's own zero-arg Value-returning members are
 * marked, and each of THOSE values' own such members are marked too — but no
 * deeper.
 */
class BridgeInterestMarkerTest {

    @Test
    void testMarksParameterAndItsDepth2Members() {
        Transport transport = mock(Transport.class);
        Parameter tempo = mock(Parameter.class);
        StringValue displayed = mock(StringValue.class);
        StringValue name = mock(StringValue.class);
        BooleanValue exists = mock(BooleanValue.class);
        when(transport.tempo()).thenReturn(tempo);
        when(tempo.displayedValue()).thenReturn(displayed);
        when(tempo.name()).thenReturn(name);
        when(tempo.exists()).thenReturn(exists);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("transport", transport));

        BridgeInterestMarker.markAll(graph);

        verify(tempo).markInterested();
        verify(displayed).markInterested();
        verify(name).markInterested();
        verify(exists).markInterested();
    }

    @Test
    void testDepthBoundStopsAtTwoLevelsFromRoot() {
        // root -> tempo() [level 1, marked] -> value() [level 2, marked]
        // -> getOrigin() [level 3, must NOT be marked]
        Transport transport = mock(Transport.class);
        Parameter tempo = mock(Parameter.class);
        SettableRangedValue tempoValue = mock(SettableRangedValue.class);
        DoubleValue tempoOrigin = mock(DoubleValue.class);
        when(transport.tempo()).thenReturn(tempo);
        when(tempo.value()).thenReturn(tempoValue);
        when(tempoValue.getOrigin()).thenReturn(tempoOrigin);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("transport", transport));

        BridgeInterestMarker.markAll(graph);

        verify(tempo).markInterested();
        verify(tempoValue).markInterested();
        verify(tempoOrigin, never()).markInterested();
    }

    @Test
    @SuppressWarnings("deprecation") // intentional: referencing getTempo() to prove it's never called
    void testDeprecatedZeroArgValueMemberIsNeverInvoked() {
        // Transport.getTempo() is @Deprecated ("Use tempo() instead") —
        // must never be reflectively invoked, matching the API-25-only policy.
        Transport transport = mock(Transport.class);
        Parameter tempo = mock(Parameter.class);
        when(transport.tempo()).thenReturn(tempo);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("transport", transport));

        BridgeInterestMarker.markAll(graph);

        verify(transport).tempo();
        verify(transport, never()).getTempo();
    }

    @Test
    void testBankRootMarksOwnMembersAndAllWindowItems() {
        TrackBank trackBank = mock(TrackBank.class);
        IntegerValue itemCount = mock(IntegerValue.class);
        SettableIntegerValue scrollPosition = mock(SettableIntegerValue.class);
        when(trackBank.itemCount()).thenReturn(itemCount);
        when(trackBank.scrollPosition()).thenReturn(scrollPosition);
        when(trackBank.getSizeOfBank()).thenReturn(2);

        Track track0 = mock(Track.class);
        Track track1 = mock(Track.class);
        SettableStringValue name0 = mock(SettableStringValue.class);
        SettableStringValue name1 = mock(SettableStringValue.class);
        when(track0.name()).thenReturn(name0);
        when(track1.name()).thenReturn(name1);
        when(trackBank.getItemAt(0)).thenReturn(track0);
        when(trackBank.getItemAt(1)).thenReturn(track1);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("tracks", trackBank));
        when(graph.rootOrNull("tracks")).thenReturn(trackBank);

        BridgeInterestMarker.markAll(graph);

        verify(itemCount).markInterested();
        verify(scrollPosition).markInterested();
        verify(name0).markInterested();
        verify(name1).markInterested();
    }

    @Test
    void testNullUnstubbedMembersAreSkippedWithoutThrowing() {
        // No stubbing at all — every zero-arg Value-returning getter on
        // transport returns Mockito's default null.
        Transport transport = mock(Transport.class);
        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("transport", transport));

        assertDoesNotThrow(() -> BridgeInterestMarker.markAll(graph));
    }

    @Test
    void testSharedValueReachableViaTwoRootsIsMarkedOnce() {
        Parameter shared = mock(Parameter.class);

        Transport transportA = mock(Transport.class);
        when(transportA.tempo()).thenReturn(shared);
        // Contrived: a second, distinct root whose own tempo()-equivalent
        // happens to expose the SAME underlying proxy — validates the
        // registry regardless of how many paths lead to one live object.
        Transport transportB = mock(Transport.class);
        when(transportB.tempo()).thenReturn(shared);

        BridgeGraph graph = mock(BridgeGraph.class);
        Map<String, Object> roots = new LinkedHashMap<>();
        roots.put("transportA", transportA);
        roots.put("transportB", transportB);
        when(graph.roots()).thenReturn(roots);

        BridgeInterestMarker.markAll(graph);

        verify(shared, times(1)).markInterested();
    }

    @Test
    void testMarksDeviceBankOwnMembersAndItemsForEachTrackIndex() {
        // Previously uncovered: the per-track DeviceBank branch responsible
        // for ~94% of the worst-case marking volume in production (128
        // tracks x 128 device-bank window slots).
        TrackBank trackBank = mock(TrackBank.class);
        when(trackBank.getSizeOfBank()).thenReturn(2);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("tracks", trackBank));
        when(graph.rootOrNull("tracks")).thenReturn(trackBank);

        DeviceBank deviceBank0 = mock(DeviceBank.class);
        IntegerValue itemCount0 = mock(IntegerValue.class);
        when(deviceBank0.itemCount()).thenReturn(itemCount0);
        when(deviceBank0.getSizeOfBank()).thenReturn(2);
        Device device0a = mock(Device.class);
        Device device0b = mock(Device.class);
        SettableStringValue deviceName0a = mock(SettableStringValue.class);
        SettableStringValue deviceName0b = mock(SettableStringValue.class);
        when(device0a.name()).thenReturn(deviceName0a);
        when(device0b.name()).thenReturn(deviceName0b);
        when(deviceBank0.getItemAt(0)).thenReturn(device0a);
        when(deviceBank0.getItemAt(1)).thenReturn(device0b);
        when(graph.deviceBankForTrack(0)).thenReturn(deviceBank0);

        DeviceBank deviceBank1 = mock(DeviceBank.class);
        IntegerValue itemCount1 = mock(IntegerValue.class);
        when(deviceBank1.itemCount()).thenReturn(itemCount1);
        when(deviceBank1.getSizeOfBank()).thenReturn(2);
        Device device1a = mock(Device.class);
        Device device1b = mock(Device.class);
        SettableStringValue deviceName1a = mock(SettableStringValue.class);
        SettableStringValue deviceName1b = mock(SettableStringValue.class);
        when(device1a.name()).thenReturn(deviceName1a);
        when(device1b.name()).thenReturn(deviceName1b);
        when(deviceBank1.getItemAt(0)).thenReturn(device1a);
        when(deviceBank1.getItemAt(1)).thenReturn(device1b);
        when(graph.deviceBankForTrack(1)).thenReturn(deviceBank1);

        BridgeInterestMarker.markAll(graph);

        // The device banks' own Value members (itemCount, per markObject on the bank itself).
        verify(itemCount0).markInterested();
        verify(itemCount1).markInterested();
        // Every window item's Value members, for BOTH track indices.
        verify(deviceName0a).markInterested();
        verify(deviceName0b).markInterested();
        verify(deviceName1a).markInterested();
        verify(deviceName1b).markInterested();
    }

    @Test
    void testDeviceBankMarkingSkipsANullDeviceBankWithoutAbortingOtherTrackIndices() {
        TrackBank trackBank = mock(TrackBank.class);
        when(trackBank.getSizeOfBank()).thenReturn(3);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("tracks", trackBank));
        when(graph.rootOrNull("tracks")).thenReturn(trackBank);

        DeviceBank deviceBank0 = mock(DeviceBank.class);
        when(deviceBank0.getSizeOfBank()).thenReturn(1);
        Device device0 = mock(Device.class);
        SettableStringValue deviceName0 = mock(SettableStringValue.class);
        when(device0.name()).thenReturn(deviceName0);
        when(deviceBank0.getItemAt(0)).thenReturn(device0);
        when(graph.deviceBankForTrack(0)).thenReturn(deviceBank0);

        // Index 1: no device bank at all (e.g. a misconfigured/absent slot) —
        // must be skipped silently, not abort the sweep for index 2.
        when(graph.deviceBankForTrack(1)).thenReturn(null);

        DeviceBank deviceBank2 = mock(DeviceBank.class);
        when(deviceBank2.getSizeOfBank()).thenReturn(1);
        Device device2 = mock(Device.class);
        SettableStringValue deviceName2 = mock(SettableStringValue.class);
        when(device2.name()).thenReturn(deviceName2);
        when(deviceBank2.getItemAt(0)).thenReturn(device2);
        when(graph.deviceBankForTrack(2)).thenReturn(deviceBank2);

        assertDoesNotThrow(() -> BridgeInterestMarker.markAll(graph));

        verify(deviceName0).markInterested();
        verify(deviceName2).markInterested();
    }

    @Test
    void testMarkAllReturnsCountOfNewlyMarkedValues() {
        Transport transport = mock(Transport.class);
        Parameter tempo = mock(Parameter.class);
        StringValue displayed = mock(StringValue.class);
        when(transport.tempo()).thenReturn(tempo);
        when(tempo.displayedValue()).thenReturn(displayed);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of("transport", transport));

        int count = BridgeInterestMarker.markAll(graph);

        assertTrue(count >= 2, "expected at least tempo + displayedValue to be counted, got " + count);
    }

    // ---- Task 6 (Cycle 2): auxiliary (per-pad/per-layer) device banks ----

    @Test
    void testMarksAuxiliaryDeviceBanks() {
        // Follow the existing test-fixture pattern in this class for building a
        // mocked graph whose roots return Value-bearing members; assert that a
        // DeviceBank present only in auxiliaryDeviceBanks() gets its items'
        // values marked (counter increases when it is added).
        var aux = mock(com.bitwig.extension.controller.api.DeviceBank.class);
        var device = mock(com.bitwig.extension.controller.api.Device.class);
        var name = mock(com.bitwig.extension.controller.api.StringValue.class);
        when(aux.getSizeOfBank()).thenReturn(1);
        when(aux.getItemAt(0)).thenReturn(device);
        when(device.name()).thenReturn(name);
        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of());
        when(graph.auxiliaryDeviceBanks()).thenReturn(List.of(aux));

        int marked = BridgeInterestMarker.markAll(graph);
        assertTrue(marked >= 1);
        verify(name).markInterested();
    }

    // ---- Task 10 (Cycle 2): popup browser columns + their init-created item banks ----

    @Test
    void testMarksBrowserColumnsAndItemBanks() {
        // Follows the aux-device-bank fixture shape above: a column (reached
        // only via graph.browserColumns(), not a root) and its item bank
        // (graph.browserItemBanks()) must both get their own Value members —
        // and the bank's window items — marked.
        var column = mock(com.bitwig.extension.controller.api.BrowserFilterColumn.class);
        var columnName = mock(com.bitwig.extension.controller.api.StringValue.class);
        when(column.name()).thenReturn(columnName);

        var bank = mock(com.bitwig.extension.controller.api.BrowserFilterItemBank.class);
        var item = mock(com.bitwig.extension.controller.api.BrowserFilterItem.class);
        var itemName = mock(com.bitwig.extension.controller.api.StringValue.class);
        when(bank.getSizeOfBank()).thenReturn(1);
        when(bank.getItemAt(0)).thenReturn(item);
        when(item.name()).thenReturn(itemName);

        BridgeGraph graph = mock(BridgeGraph.class);
        when(graph.roots()).thenReturn(Map.of());
        when(graph.browserColumns()).thenReturn(List.of(column));
        when(graph.browserItemBanks()).thenReturn(List.of(bank));

        int marked = BridgeInterestMarker.markAll(graph);

        assertTrue(marked >= 2);
        verify(columnName).markInterested();
        verify(itemName).markInterested();
    }
}
