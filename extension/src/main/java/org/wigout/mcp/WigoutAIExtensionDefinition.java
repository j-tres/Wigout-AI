package org.wigout.mcp;

import com.bitwig.extension.api.PlatformType;
import com.bitwig.extension.controller.ControllerExtensionDefinition;
import com.bitwig.extension.controller.api.ControllerHost;
import org.wigout.mcp.common.AppConstants;

import java.util.UUID;

/**
 * Definition class for the Wigout AI extension.
 * This defines metadata such as name, author, version, etc.
 */
public class WigoutAIExtensionDefinition extends ControllerExtensionDefinition {
    private static final UUID DRIVER_ID = UUID.fromString("50856f9e-04cf-4357-ac91-3e57ce7abe06");

    @Override
    public String getName() {
        return AppConstants.APP_NAME;
    }

    @Override
    public String getAuthor() {
        return AppConstants.APP_AUTHOR;
    }

    @Override
    public String getVersion() {
        return AppConstants.APP_VERSION;
    }

    @Override
    public UUID getId() {
        return DRIVER_ID;
    }

    @Override
    public String getHardwareVendor() {
        return "MCP";
    }

    @Override
    public String getHardwareModel() {
        return "Wigout AI";
    }

    @Override
    public int getRequiredAPIVersion() {
        return 25;
    }

    @Override
    public int getNumMidiInPorts() {
        return 0;
    }

    @Override
    public int getNumMidiOutPorts() {
        return 0;
    }

    @Override
    public String getHelpFilePath() {
        return "https://github.com/j-tres/wigout-ai/blob/main/README.md";
    }

    // This method may not be part of the current API version
    public boolean isUsingBitwigMidiAPI() {
        return false;
    }

    @Override
    public void listAutoDetectionMidiPortNames(
            final com.bitwig.extension.controller.AutoDetectionMidiPortNamesList list,
            final PlatformType platformType) {
        // No MIDI auto-detection needed
    }

    @Override
    public WigoutAIExtension createInstance(final ControllerHost host) {
        return new WigoutAIExtension(this, host);
    }
}
