package org.wigout.mcp.features;

import org.wigout.mcp.bitwig.BitwigApiFacade;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.error.BitwigApiException;
import org.wigout.mcp.common.error.ErrorCode;

/**
 * Controller class for transport control features.
 * Bridges between MCP tools and Bitwig API operations for transport control.
 */
public class TransportController {
    private final BitwigApiFacade bitwigApiFacade;
    private final Logger logger;

    /**
     * Creates a new TransportController instance.
     *
     * @param bitwigApiFacade The facade for Bitwig API interactions
     * @param logger          The logger for logging operations
     */
    public TransportController(BitwigApiFacade bitwigApiFacade, Logger logger) {
        this.bitwigApiFacade = bitwigApiFacade;
        this.logger = logger;
    }

    /**
     * Starts the transport playback.
     *
     * @return A message indicating the operation result
     */
    public String startTransport() {
        try {
            logger.info("TransportController: Starting transport playback");
            bitwigApiFacade.startTransport();
            return "Transport playback started.";
        } catch (Exception e) {
            logger.info("TransportController: Error starting transport playback: " + e.getMessage());
            throw new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "startTransport", "Failed to start transport playback", e);
        }
    }

    /**
     * Stops the transport playback.
     *
     * @return A message indicating the operation result
     */
    public String stopTransport() {
        try {
            logger.info("TransportController: Stopping transport playback");
            bitwigApiFacade.stopTransport();
            return "Transport playback stopped.";
        } catch (Exception e) {
            logger.info("TransportController: Error stopping transport playback: " + e.getMessage());
            throw new BitwigApiException(ErrorCode.TRANSPORT_ERROR, "stopTransport", "Failed to stop transport playback", e);
        }
    }
}
