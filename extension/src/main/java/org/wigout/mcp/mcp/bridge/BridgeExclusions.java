package org.wigout.mcp.mcp.bridge;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Scope predicate for the bridge: the physical-controller subsystem is
 * excluded (spec Decision 2). An agent has no hands — hardware bindings,
 * MIDI port plumbing, and the OSC module are refused and hidden.
 */
public final class BridgeExclusions {

    public static final String EXCLUSION_REASON = "excluded: physical-controller subsystem";

    private static final Set<String> EXACT = Set.of(
        "MidiIn", "MidiOut", "NoteInput", "PianoKeyboard", "MidiExpressions");

    private BridgeExclusions() {}

    /**
     * True if the simple type name belongs to the excluded subsystem.
     * contains("Hardwar") deliberately catches both Hardware* and the API's
     * typo'd RelativeHardwarControlBindable / AbsoluteHardwarControlBindable.
     */
    public static boolean isExcludedTypeName(String simpleName) {
        return simpleName.contains("Hardwar")
            || simpleName.startsWith("Osc")
            || EXACT.contains(simpleName);
    }

    /** True if the method's declaring type or return type is excluded. */
    public static boolean isExcludedMethod(Method m) {
        return isExcludedTypeName(m.getDeclaringClass().getSimpleName())
            || isExcludedTypeName(m.getReturnType().getSimpleName());
    }
}
