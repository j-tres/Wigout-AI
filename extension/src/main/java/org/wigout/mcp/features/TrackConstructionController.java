package org.wigout.mcp.features;

import org.wigout.mcp.bitwig.BitwigApiFacade;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.error.BitwigApiException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Controller for project-construction operations: creating, renaming, and
 * deleting tracks, and inserting devices onto track device chains.
 *
 * All Bitwig mutations here are asynchronous and return nothing, and some
 * (device insertion by UUID) silently do nothing on bad input. Every mutation
 * is therefore followed by a bounded poll ("bounded verify") of the facade's
 * cached state, which Bitwig's controller thread updates concurrently. Results
 * carry a "verified" flag; false means the change could not be observed within
 * the timeout, never that it certainly failed.
 */
public class TrackConstructionController {

    // Device instantiation can exceed 500 ms (measured live in Bitwig 6.1b2),
    // so the ceiling is ~3 s; successful mutations return as soon as observed.
    private static final int DEFAULT_VERIFY_ATTEMPTS = 30;
    private static final long DEFAULT_VERIFY_DELAY_MS = 100;

    private final BitwigApiFacade bitwigApiFacade;
    private final Logger logger;
    private final int verifyAttempts;
    private final long verifyDelayMs;

    /**
     * Constructs a TrackConstructionController with default verify timing
     * (30 attempts x 100 ms).
     *
     * @param bitwigApiFacade The facade for Bitwig API interactions
     * @param logger The logger service for operation logging
     */
    public TrackConstructionController(BitwigApiFacade bitwigApiFacade, Logger logger) {
        this(bitwigApiFacade, logger, DEFAULT_VERIFY_ATTEMPTS, DEFAULT_VERIFY_DELAY_MS);
    }

    /** Package-visible for tests: custom verify timing. */
    TrackConstructionController(BitwigApiFacade bitwigApiFacade, Logger logger, int verifyAttempts, long verifyDelayMs) {
        this.bitwigApiFacade = bitwigApiFacade;
        this.logger = logger;
        this.verifyAttempts = verifyAttempts;
        this.verifyDelayMs = verifyDelayMs;
    }

    /**
     * Creates a track and verifies it appeared in the cached track list.
     *
     * @param type     "instrument", "audio", or "effect"
     * @param position index to insert at, or -1 to append (pinned by Bitwig)
     * @param name     optional name to apply after creation; null to skip
     * @return result map: track_index, track_name, verified — or verified=false + message
     */
    public Map<String, Object> createTrack(String type, int position, String name) throws BitwigApiException {
        logger.info("TrackConstructionController: Creating " + type + " track at position " + position);
        List<String> before = bitwigApiFacade.getExistingTrackNames();
        bitwigApiFacade.createTrack(type, position);

        boolean appeared = pollUntil(() -> bitwigApiFacade.getExistingTrackNames().size() == before.size() + 1);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!appeared) {
            result.put("verified", false);
            result.put("message", "Track creation was requested but could not be verified within the timeout. Check with list_tracks.");
            return result;
        }

        List<String> after = bitwigApiFacade.getExistingTrackNames();
        int newIndex = firstDivergenceIndex(before, after);
        boolean verified = true;

        if (name != null) {
            bitwigApiFacade.renameTrackByIndex(newIndex, name);
            verified = pollUntil(() -> {
                List<String> current = bitwigApiFacade.getExistingTrackNames();
                return newIndex < current.size() && name.equals(current.get(newIndex));
            });
        }

        result.put("track_index", newIndex);
        result.put("track_name", bitwigApiFacade.getTrackNameByIndex(newIndex));
        result.put("verified", verified);
        if (!verified) {
            result.put("message", "Track was created but the rename could not be verified within the timeout. Check with list_tracks.");
        }
        logger.info("TrackConstructionController: Created track at index " + newIndex + " (verified=" + verified + ")");
        return result;
    }

    /**
     * Renames a track (selected by index xor name) and verifies the cached
     * name changed.
     */
    public Map<String, Object> renameTrack(Integer trackIndex, String trackName, String newName) throws BitwigApiException {
        int index = resolveToIndex(trackIndex, trackName);
        logger.info("TrackConstructionController: Renaming track " + index + " to '" + newName + "'");
        bitwigApiFacade.renameTrackByIndex(index, newName);

        boolean verified = pollUntil(() -> {
            List<String> current = bitwigApiFacade.getExistingTrackNames();
            return index < current.size() && newName.equals(current.get(index));
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("track_index", index);
        result.put("track_name", newName);
        result.put("verified", verified);
        if (!verified) {
            result.put("message", "Rename was requested but could not be verified within the timeout. Check with list_tracks.");
        }
        return result;
    }

    /**
     * Deletes a track (selected by index xor name) and verifies the cached
     * track count dropped.
     */
    public Map<String, Object> deleteTrack(Integer trackIndex, String trackName) throws BitwigApiException {
        int index = resolveToIndex(trackIndex, trackName);
        String deletedName = bitwigApiFacade.getTrackNameByIndex(index);
        int beforeCount = bitwigApiFacade.getExistingTrackNames().size();
        logger.info("TrackConstructionController: Deleting track " + index + " ('" + deletedName + "')");
        bitwigApiFacade.deleteTrackByIndex(index);

        boolean verified = pollUntil(() -> bitwigApiFacade.getExistingTrackNames().size() == beforeCount - 1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted_track", deletedName);
        result.put("verified", verified);
        if (!verified) {
            result.put("message", "Deletion was requested but could not be verified within the timeout. Check with list_tracks.");
        }
        return result;
    }

    /**
     * Inserts a device at the end of a track's chain — by Bitwig-device UUID
     * or by .bwpreset file path (exactly one non-null; validated by the tool
     * layer) — and verifies the cached device list grew.
     */
    public Map<String, Object> insertDevice(Integer trackIndex, String trackName, String deviceUuid, String presetPath) throws BitwigApiException {
        int index = resolveToIndex(trackIndex, trackName);
        int beforeCount = bitwigApiFacade.getDevicesOnTrack(index, null, null).size();

        if (deviceUuid != null) {
            logger.info("TrackConstructionController: Inserting device " + deviceUuid + " on track " + index);
            bitwigApiFacade.insertBitwigDeviceAtEndOfChain(index, UUID.fromString(deviceUuid));
        } else {
            logger.info("TrackConstructionController: Inserting preset file '" + presetPath + "' on track " + index);
            bitwigApiFacade.insertFileAtEndOfChain(index, presetPath);
        }

        boolean verified = pollUntil(() -> bitwigApiFacade.getDevicesOnTrack(index, null, null).size() > beforeCount);

        List<String> deviceNames = new ArrayList<>();
        for (Map<String, Object> device : bitwigApiFacade.getDevicesOnTrack(index, null, null)) {
            deviceNames.add(String.valueOf(device.get("name")));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("track_index", index);
        result.put("devices", deviceNames);
        result.put("verified", verified);
        if (!verified) {
            result.put("message", deviceUuid != null
                ? "Insertion could not be verified within the timeout. Bitwig silently ignores unknown device UUIDs — the UUID may not match this Bitwig version. Check with list_devices_on_track."
                : "Insertion could not be verified within the timeout. Check that the preset file is valid, and confirm with list_devices_on_track.");
        }
        return result;
    }

    /**
     * Resolves the track selector (index xor name — tools guarantee exactly
     * one) to a validated bank index.
     */
    private int resolveToIndex(Integer trackIndex, String trackName) throws BitwigApiException {
        if (trackIndex != null) {
            bitwigApiFacade.getTrackNameByIndex(trackIndex); // validates index and existence
            return trackIndex;
        }
        return bitwigApiFacade.findTrackIndexByName(trackName);
    }

    /**
     * Index of the first position where the two lists differ — for a single
     * insertion this is the insertion index (equal-prefix lists diverge at
     * before.size(), i.e. an append).
     */
    private static int firstDivergenceIndex(List<String> before, List<String> after) {
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                return i;
            }
        }
        return before.size();
    }

    /**
     * Polls the condition up to verifyAttempts times, sleeping verifyDelayMs
     * between attempts. Returns true as soon as the condition holds.
     */
    private boolean pollUntil(BooleanSupplier condition) {
        for (int attempt = 0; attempt < verifyAttempts; attempt++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (attempt < verifyAttempts - 1) {
                try {
                    Thread.sleep(verifyDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }
}
