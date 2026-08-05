package org.wigout.mcp.common;

/**
 * Constants used throughout the Wigout AI application.
 */
public class AppConstants {
    /**
     * Default port for the MCP server.
     */
    public static final int DEFAULT_MCP_PORT = 61169;

    /**
     * The application name.
     */
    public static final String APP_NAME = "Wigout AI";

    /**
     * The application version.
     */
    public static final String APP_VERSION;

    /**
     * The application author.
     */
    public static final String APP_AUTHOR = "Jon Tresko";

    static {
        APP_VERSION = performVersionLoadingInternal();
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private AppConstants() {
        // This class should not be instantiated
    }

    private static String performVersionLoadingInternal() {
        String version = null;
        Package pkg = AppConstants.class.getPackage();
        if (pkg != null) {
            version = pkg.getImplementationVersion();
            if (version != null && !version.trim().isEmpty()) {
                return version;
            }
        }
        return "0.0.0-unknown";
    }
}
