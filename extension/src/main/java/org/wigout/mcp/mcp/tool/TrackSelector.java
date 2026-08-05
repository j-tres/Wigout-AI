package org.wigout.mcp.mcp.tool;

import java.util.Map;

/**
 * Shared parser for the track_index-xor-track_name selector convention used
 * by construction tools.
 */
record TrackSelector(Integer trackIndex, String trackName) {

    /**
     * Parses and validates the selector: exactly one of track_index (int >= 0)
     * or track_name (non-empty string) must be present.
     *
     * @throws IllegalArgumentException if the selector is missing, doubled, or malformed
     */
    static TrackSelector from(Map<String, Object> arguments) {
        Integer trackIndex = null;
        String trackName = null;

        if (arguments.containsKey("track_index")) {
            Object indexObj = arguments.get("track_index");
            // instanceof Number, not Integer: JSON-RPC deserialization may yield Long etc.
            if (!(indexObj instanceof Number indexNum)) {
                throw new IllegalArgumentException("Parameter 'track_index' must be an integer");
            }
            int index = indexNum.intValue();
            if (index < 0) {
                throw new IllegalArgumentException("Parameter 'track_index' must be >= 0");
            }
            trackIndex = index;
        }

        if (arguments.containsKey("track_name")) {
            Object nameObj = arguments.get("track_name");
            if (!(nameObj instanceof String name) || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Parameter 'track_name' must be a non-empty string");
            }
            trackName = name;
        }

        if ((trackIndex == null) == (trackName == null)) {
            throw new IllegalArgumentException("Provide exactly one of 'track_index' or 'track_name'");
        }

        return new TrackSelector(trackIndex, trackName);
    }
}
