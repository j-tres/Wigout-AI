package org.wigout.mcp.common.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Curated catalog of native Bitwig devices for deterministic insertion by
 * name (InsertionPoint.insertBitwigDevice needs the device UUID).
 *
 * UUIDs are harvested from .bwpreset headers on this machine — see
 * scripts/scan-device-uuids.ps1. To add a device: save any preset for it in
 * Bitwig, rerun the scan script, and add an add(...) line here.
 */
public final class DeviceCatalog {

    /** One catalog row. */
    public record Entry(String name, String uuid, String category, String description) {}

    private static final Map<String, Entry> BY_NAME = new LinkedHashMap<>();

    private static void add(String name, String uuid, String category, String description) {
        BY_NAME.put(name.toLowerCase(Locale.ROOT), new Entry(name, uuid, category, description));
    }

    static {
        // Verified during design (parsed from this machine's preset library):
        add("Polymer", "8f58138b-03aa-4e9d-83bd-a038c99a4ed5", "Synth", "Bitwig's modular subtractive synthesizer");
        add("Poly Grid", "a33bba66-8cd4-4f89-aee5-68bf67f70a54", "The Grid", "Polyphonic modular synthesis environment");
        // Entries below come from the Task-1 scan output (scripts/scan-device-uuids.ps1).
        // Add one line per curated device: add(Name, uuid, Category, short description).
        add("Arpeggiator", "4d407a2b-c91b-4e4c-9a89-c53c19fe6251", "Note FX", "Generates rhythmic note patterns from held chords");
        add("Chain", "c86d21fb-d544-4daf-a1bf-57de22aa320c", "Container", "Groups devices into a single container");
        add("Drum Machine", "8ea97e45-0255-40fd-bc7e-94419741e9d1", "Drum Kit", "Multi-pad drum sampler/synth container");
        add("FX Grid", "d641f61b-d4db-4006-930e-cdd7aeb3e9d7", "The Grid", "Modular audio effects processing environment");
        add("Instrument Layer", "5024be2e-65d6-4d40-bbfe-8b2ea993c445", "Container", "Layers multiple instruments under one set of controls");
        add("Instrument Selector", "9588fbcf-721a-438b-8555-97e4231f7d2c", "Container", "Switches between multiple instrument chains");
        add("Note Grid", "264d6f4e-5067-46c9-a4fa-a75a295d9e01", "The Grid", "Modular note/MIDI processing environment");
        add("Phase-4", "252723bf-68a6-4ee6-81f8-95ba4d0fb467", "Synth", "Four-operator phase modulation synthesizer");
        add("Polysynth", "a9ffacb5-33e9-4fc7-8621-b1af31e410ef", "Synth", "Classic subtractive polyphonic synthesizer");
    }

    private DeviceCatalog() {} // Prevent instantiation

    /**
     * Looks up a catalog entry by display name (case-insensitive, trimmed).
     */
    public static Optional<Entry> lookup(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /** All catalog entries in insertion order. */
    public static List<Entry> entries() {
        return List.copyOf(BY_NAME.values());
    }

    /** Comma-joined display names, for error messages. */
    public static String availableNames() {
        return String.join(", ", BY_NAME.values().stream().map(Entry::name).toList());
    }
}
