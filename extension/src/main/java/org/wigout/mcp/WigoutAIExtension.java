package org.wigout.mcp;

import com.bitwig.extension.controller.ControllerExtension;
import com.bitwig.extension.controller.api.ControllerHost;
import org.wigout.mcp.common.AppConstants;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.config.ConfigManager;
import org.wigout.mcp.config.PreferencesBackedConfigManager;
import org.wigout.mcp.config.ConfigChangeObserver;
import org.wigout.mcp.config.FileLocationsPreferences;
import org.wigout.mcp.config.UserConfigStore;
import org.wigout.mcp.mcp.McpServerManager;
import org.wigout.mcp.server.JettyServerManager;

import org.eclipse.jetty.servlet.ServletHolder;

/**
 * Main extension class for the Wigout AI extension.
 * Handles lifecycle events (init, exit) and owns the primary components.
 * Manages the Jetty server and servlet context for multiple servlets.
 */
public class WigoutAIExtension extends ControllerExtension implements ConfigChangeObserver {
    private static final String MCP_ENDPOINT_PATH = "/mcp";

    private Logger logger;
    private ConfigManager configManager;
    private McpServerManager mcpServerManager;
    private JettyServerManager jettyServerManager;
    private FileLocationsPreferences fileLocationsPreferences;

    /**
     * Creates a new WigoutAIExtension instance.
     *
     * @param definition The extension definition
     * @param host       The Bitwig ControllerHost
     */
    protected WigoutAIExtension(final WigoutAIExtensionDefinition definition, final ControllerHost host) {
        super(definition, host);
    }

    /**
     * Initialize the extension.
     * This is called when the extension is enabled in Bitwig Studio.
     */
    @Override
    public void init() {
        final ControllerHost host = getHost();

        // Initialize the logger
        logger = new Logger(host);

        // Initialize the config manager with Bitwig preferences integration
        configManager = new PreferencesBackedConfigManager(logger, host);

        // Initialize the per-user file-locations config (~/.wigout-ai/config.json)
        fileLocationsPreferences = new FileLocationsPreferences(logger, host, new UserConfigStore(logger));

        // Initialize the Jetty server manager
        jettyServerManager = new JettyServerManager(logger, configManager, (WigoutAIExtensionDefinition)getExtensionDefinition(), host);

        // Initialize and start the MCP server
        mcpServerManager = new McpServerManager(logger, configManager, (WigoutAIExtensionDefinition)getExtensionDefinition(), host, fileLocationsPreferences);

        // Register this extension as configuration change observers
        configManager.addObserver(this);

        // Start the Jetty server and MCP server
        startServer();

        // Log startup message
        logger.info(String.format("%s Extension Loaded - Version %s", AppConstants.APP_NAME, getExtensionDefinition().getVersion()));
    }

    /**
     * Starts the Jetty server and registers all servlets.
     */
    private void startServer() {
        try {
            // Create MCP servlet from the MCP server manager
            ServletHolder mcpServlet = mcpServerManager.createMcpServlet(MCP_ENDPOINT_PATH);

            // Start Jetty server with the MCP servlet
            jettyServerManager.startServer(mcpServlet, MCP_ENDPOINT_PATH);
        } catch (Exception e) {
            logger.error("Failed to create MCP servlet or start server", e);
        }
    }    /**
     * Stops the Jetty server and all servlets.
     */
    private void stopServer() {
        jettyServerManager.stopServer();
    }

    /**
     * Gracefully restarts the server with new configuration.
     */
    private void restartServer() {
        try {
            // Create MCP servlet from the MCP server manager
            ServletHolder mcpServlet = mcpServerManager.createMcpServlet(MCP_ENDPOINT_PATH);

            // Restart Jetty server with the MCP servlet
            jettyServerManager.restartServer(mcpServlet, MCP_ENDPOINT_PATH);
        } catch (Exception e) {
            logger.error("Failed to create MCP servlet or restart server", e);
        }
    }    /**
     * Called when the MCP server host changes.
     * Triggers a graceful restart of the entire server.
     *
     * @param oldHost The previous host value
     * @param newHost The new host value
     */
    @Override
    public void onHostChanged(String oldHost, String newHost) {
        logger.info(AppConstants.APP_NAME + " Extension: Host changed from '" + oldHost + "' to '" + newHost + "', restarting server");
        restartServer();
    }

    /**
     * Called when the MCP server port changes.
     * Triggers a graceful restart of the entire server.
     *
     * @param oldPort The previous port value
     * @param newPort The new port value
     */
    @Override
    public void onPortChanged(int oldPort, int newPort) {
        logger.info(AppConstants.APP_NAME + " Extension: Port changed from " + oldPort + " to " + newPort + ", restarting server");
        restartServer();
    }

    /**
     * Clean up when the extension is closed.
     * This is called when the extension is disabled in Bitwig Studio or when Bitwig
     * Studio is closed.
     */
    @Override
    public void exit() {
        if (logger != null) {
            logger.info(AppConstants.APP_NAME + " Extension shutting down");
        }

        // Stop the server (which includes MCP server)
        stopServer();
    }

    /**
     * Called when GUI updates should be performed.
     */
    @Override
    public void flush() {
        // No GUI updates needed for now
    }
}
