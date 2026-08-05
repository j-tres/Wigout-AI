package org.wigout.mcp.bitwig;

import com.bitwig.extension.controller.api.*;
import org.wigout.mcp.mcp.bridge.DirectParameterCache;
import org.wigout.mcp.mcp.bridge.NoteStepCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of root API objects for the bridge. Everything here is created
 * during ControllerExtension init (Bitwig forbids API-object creation later),
 * so the graph is fixed for the extension's lifetime. Breadth comes from the
 * shared banks; depth (layers, drum pads, browser columns, clip notes) comes
 * from pinnable cursors, wired in Cycle 2.
 */
public class BridgeGraph { // non-final: tests mock it with Mockito

    private final Map<String, Object> roots;
    private final List<DeviceBank> trackDeviceBanks;
    private final List<DeviceBank> padDeviceBanks;
    private final List<DeviceBank> layerDeviceBanks;
    private final NoteStepCache noteStepCache;
    private final int noteGridWidth;
    private final int noteGridHeight;
    private final DirectParameterCache directParameterCache;
    private final IdentityHashMap<Object, Bank<?>> browserItemBanks;
    private final List<Object> browserColumns;

    /**
     * Built by BitwigApiFacade at init with the objects it already owns plus
     * the bridge-only roots it creates alongside.
     */
    BridgeGraph(Transport transport,
                Application application,
                Project project,
                Arranger arranger,
                Mixer mixer,
                Groove groove,
                MasterTrack masterTrack,
                PopupBrowser popupBrowser,
                TrackBank trackBank,
                SceneBank sceneBank,
                CursorTrack cursorTrack,
                CursorDevice cursorDevice,
                PinnableCursorClip cursorClip,
                RemoteControlsPage cursorRemoteControls,
                RemoteControlsPage projectRemoteControls,
                List<DeviceBank> trackDeviceBanks,
                DrumPadBank drumPadBank,
                DeviceLayerBank layerBank,
                DeviceBank slotDeviceBank,
                List<DeviceBank> padDeviceBanks,
                List<DeviceBank> layerDeviceBanks,
                NoteStepCache noteStepCache,
                int noteGridWidth,
                int noteGridHeight,
                DirectParameterCache directParameterCache,
                IdentityHashMap<Object, Bank<?>> browserItemBanks,
                List<Object> browserColumns) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("transport", transport);
        map.put("application", application);
        map.put("project", project);
        map.put("arranger", arranger);
        map.put("mixer", mixer);
        map.put("groove", groove);
        map.put("masterTrack", masterTrack);
        map.put("browser", popupBrowser);
        map.put("tracks", trackBank);
        map.put("scenes", sceneBank);
        map.put("cursorTrack", cursorTrack);
        map.put("cursorDevice", cursorDevice);
        map.put("cursorClip", cursorClip);
        map.put("drumPads", drumPadBank);
        map.put("layers", layerBank);
        map.put("slotDevices", slotDeviceBank);
        map.put("cursorRemoteControls", cursorRemoteControls);
        map.put("projectRemoteControls", projectRemoteControls);
        this.roots = Collections.unmodifiableMap(map);
        this.trackDeviceBanks = trackDeviceBanks;
        this.padDeviceBanks = padDeviceBanks;
        this.layerDeviceBanks = layerDeviceBanks;
        this.noteStepCache = noteStepCache;
        this.noteGridWidth = noteGridWidth;
        this.noteGridHeight = noteGridHeight;
        this.directParameterCache = directParameterCache;
        this.browserItemBanks = browserItemBanks;
        this.browserColumns = browserColumns;
    }

    /** Ordered root name → live API object. */
    public Map<String, Object> roots() {
        return roots;
    }

    public Object rootOrNull(String name) {
        return roots.get(name);
    }

    /**
     * The per-track DeviceBank created at init — the special edge behind
     * "tracks[i].devices" (Track has no zero-arg device-list getter).
     */
    public Object deviceBankForTrack(int trackIndex) {
        return trackDeviceBanks.get(trackIndex);
    }

    /**
     * The per-drum-pad DeviceBank created at init (cursor-anchored, Cycle 2
     * Task 6) — the special edge behind "drumPads[i].devices".
     */
    public Object deviceBankForDrumPad(int padIndex) {
        return padDeviceBanks.get(padIndex);
    }

    /**
     * The per-layer DeviceBank created at init (cursor-anchored, Cycle 2
     * Task 6) — the special edge behind "layers[i].devices".
     */
    public Object deviceBankForLayer(int layerIndex) {
        return layerDeviceBanks.get(layerIndex);
    }

    /** Nested per-pad/per-layer device banks — swept by BridgeInterestMarker, not roots. */
    public List<DeviceBank> auxiliaryDeviceBanks() {
        List<DeviceBank> all = new ArrayList<>(padDeviceBanks);
        all.addAll(layerDeviceBanks);
        return all;
    }

    /** Observer-fed cache of the launcher cursor clip's note steps (Cycle 2). */
    public NoteStepCache noteStepCache() { return noteStepCache; }

    /** Width (x, steps) of the launcher cursor clip's note grid. */
    public int noteGridWidth() { return noteGridWidth; }

    /** Height (y, keys) of the launcher cursor clip's note grid. */
    public int noteGridHeight() { return noteGridHeight; }

    /** Observer-fed cache of the cursor device's DirectParameter-by-ID surface (full plugin-parameter enumeration). */
    public DirectParameterCache directParameterCache() { return directParameterCache; }

    /**
     * The init-created item bank behind "<column>.items" (Cycle 2 Task 10) —
     * BrowserFilterColumn/BrowserResultsColumn have no zero-arg item-bank
     * getter and createItemBank is init-only. Keyed by the column proxy's
     * identity; null for anything that isn't a browser column.
     */
    public Object itemBankForColumn(Object column) { return browserItemBanks.get(column); }

    /** The popup browser's filter + results columns, in declaration order. */
    public List<Object> browserColumns() { return browserColumns; }

    /** Item banks created at init for every browser column — swept by BridgeInterestMarker, not roots. */
    public Collection<Bank<?>> browserItemBanks() { return browserItemBanks.values(); }
}
