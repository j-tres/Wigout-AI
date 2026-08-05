package org.wigout.mcp.mcp;

import org.wigout.mcp.WigoutAIExtensionDefinition;
import org.wigout.mcp.bitwig.BitwigApiFacade;
import org.wigout.mcp.bitwig.BridgeGraph;
import org.wigout.mcp.mcp.bridge.PathResolver;
import org.wigout.mcp.mcp.bridge.ValueReader;
import org.wigout.mcp.mcp.bridge.ArgCoercer;
import org.wigout.mcp.mcp.bridge.ValueWriter;
import org.wigout.mcp.mcp.bridge.SnapshotWalker;
import org.wigout.mcp.common.Logger;
import org.wigout.mcp.common.logging.StructuredLogger;
import org.wigout.mcp.common.AppConstants;
import org.wigout.mcp.config.ConfigManager;
import org.wigout.mcp.config.FileLocationsPreferences;
import org.wigout.mcp.features.TransportController;
import org.wigout.mcp.features.DeviceController;
import org.wigout.mcp.features.ClipSceneController;
import org.wigout.mcp.features.TrackConstructionController;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.servlet.ServletHolder;
import org.wigout.mcp.mcp.tool.StatusTool;
import org.wigout.mcp.mcp.tool.TransportTool;
import org.wigout.mcp.mcp.tool.DeviceParamTool;
import org.wigout.mcp.mcp.tool.ClipTool;
import org.wigout.mcp.mcp.tool.SceneTool;
import org.wigout.mcp.mcp.tool.ListTracksTool;
import org.wigout.mcp.mcp.tool.ListDevicesOnTrackTool;
import org.wigout.mcp.mcp.tool.GetTrackDetailsTool;
import org.wigout.mcp.mcp.tool.GetDeviceDetailsTool;
import org.wigout.mcp.mcp.tool.ListScenesTool;
import org.wigout.mcp.mcp.tool.GetClipsInSceneTool;
import org.wigout.mcp.mcp.tool.CreateTrackTool;
import org.wigout.mcp.mcp.tool.RenameTrackTool;
import org.wigout.mcp.mcp.tool.DeleteTrackTool;
import org.wigout.mcp.mcp.tool.InsertDeviceTool;
import org.wigout.mcp.mcp.tool.DeviceCatalogTool;
import org.wigout.mcp.mcp.tool.BwDescribeTool;
import org.wigout.mcp.mcp.tool.BwGetTool;
import org.wigout.mcp.mcp.tool.BwSetTool;
import org.wigout.mcp.mcp.tool.BwCallTool;
import org.wigout.mcp.mcp.tool.BwSnapshotTool;
import io.modelcontextprotocol.spec.McpSchema;
import com.bitwig.extension.controller.api.ControllerHost;
import org.wigout.mcp.mcp.tool.SceneByNameTool;

/**
 * Manages the MCP server for the Wigout AI extension.
 * Responsible for configuring and managing the MCP HTTP servlet
 * that uses the Server-Sent Events (SSE) transport with the MCP Java SDK.
 *
 * This implementation:
 * - Sets up the MCP Java SDK with SSE transport
 * - Implements standard MCP ping functionality
 * - Registers custom tools like the "status" tool and "transport_start" tool
 * - Configures the appropriate error handling
 * - Provides logging for MCP requests and responses
 * - Registers the MCP servlet with the provided ServletContextHandler
 */
public class McpServerManager {
    private final Logger logger;
    private final WigoutAIExtensionDefinition extensionDefinition;
    private final ControllerHost controllerHost;
    private final FileLocationsPreferences fileLocationsPreferences;

    private HttpServletStreamableServerTransportProvider transportProvider;

    // Reusable controllers - initialized once during first start
    private BitwigApiFacade bitwigApiFacade;
    private TransportController transportController;
    private DeviceController deviceController;
    private ClipSceneController clipSceneController;
    private TrackConstructionController trackConstructionController;
    private PathResolver pathResolver;
    private ValueReader valueReader;
    private ValueWriter valueWriter;
    private ArgCoercer argCoercer;
    private SnapshotWalker snapshotWalker;

    /**
     * Creates a new McpServerManager instance.
     *
     * @param logger             The logger to use for logging server events
     * @param configManager      The configuration manager (kept for API compatibility)
     * @param extensionDefinition The extension definition to get version information
     */
    public McpServerManager(Logger logger, ConfigManager configManager, WigoutAIExtensionDefinition extensionDefinition) {
        this(logger, configManager, extensionDefinition, null, null);
    }

    /**
     * Creates a new McpServerManager instance with a controller host.
     *
     * @param logger             The logger to use for logging server events
     * @param configManager      The configuration manager (kept for API compatibility)
     * @param extensionDefinition The extension definition to get version information
     * @param controllerHost     The Bitwig controller host, or null if not available
     */
    public McpServerManager(Logger logger, ConfigManager configManager, WigoutAIExtensionDefinition extensionDefinition, ControllerHost controllerHost) {
        this(logger, configManager, extensionDefinition, controllerHost, null);
    }

    /**
     * Creates a new McpServerManager instance with a controller host and file-locations preferences.
     *
     * @param logger             The logger to use for logging server events
     * @param configManager      The configuration manager (kept for API compatibility)
     * @param extensionDefinition The extension definition to get version information
     * @param controllerHost     The Bitwig controller host, or null if not available
     * @param fileLocationsPreferences The per-user file-locations config, or null if not available
     */
    public McpServerManager(Logger logger, ConfigManager configManager, WigoutAIExtensionDefinition extensionDefinition, ControllerHost controllerHost, FileLocationsPreferences fileLocationsPreferences) {
        this.logger = logger;
        this.extensionDefinition = extensionDefinition;
        this.controllerHost = controllerHost;
        this.fileLocationsPreferences = fileLocationsPreferences;
    }

    /**
     * Gets the Bitwig controller host.
     *
     * @return The controller host
     * @throws IllegalStateException if the controller host is not available
     */
    public ControllerHost getHost() {
        if (controllerHost == null) {
            throw new IllegalStateException("Controller host is not available");
        }
        return controllerHost;
    }

    /**
     * Creates and returns the MCP servlet.
     * Configures the server with the SSE transport and registers
     * the standard ping functionality and available tools.
     *
     * @param endpointPath The endpoint path for the MCP servlet
     * @return The configured MCP servlet
     * @throws Exception if servlet creation fails
     */
    public ServletHolder createMcpServlet(String endpointPath) throws Exception {
        // 1. Instantiate ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();

        // 2. Instantiate HttpServletStreamableServerTransportProvider
        this.transportProvider = HttpServletStreamableServerTransportProvider
            .builder()
            .objectMapper(objectMapper)
            .mcpEndpoint(endpointPath)
            .build();

        // 3. Configure the MCP server with tools
        // Note: Direct request/response logging with onRequest/onResponse methods is not
        // supported by the MCP Java SDK. The StatusTool implementation handles its own
        // logging. If more detailed logging is needed, we should investigate alternative
        // approaches with the MCP SDK.

        // Initialize controllers only once during first start to avoid Bitwig API restrictions
        if (bitwigApiFacade == null) {
            logger.info("McpServerManager: Initializing Bitwig API controllers");
            bitwigApiFacade = new BitwigApiFacade(getHost(), logger);
            transportController = new TransportController(bitwigApiFacade, logger);
            deviceController = new DeviceController(bitwigApiFacade, logger);
            clipSceneController = new ClipSceneController(bitwigApiFacade, logger);
            trackConstructionController = new TrackConstructionController(bitwigApiFacade, logger);
            pathResolver = new PathResolver(bitwigApiFacade.getBridgeGraph());
            valueReader = new ValueReader();
            valueWriter = new ValueWriter();
            argCoercer = new ArgCoercer(pathResolver);
            snapshotWalker = new SnapshotWalker(pathResolver, bitwigApiFacade.getBridgeGraph(), valueReader);
        } else {
            logger.info("McpServerManager: Reusing existing Bitwig API controllers");
        }

        // Create StructuredLogger for tools that have been migrated to unified error handling
        StructuredLogger structuredLogger = new StructuredLogger(logger, "MCP-Tools");

        McpServer.sync(this.transportProvider)
            .serverInfo(AppConstants.APP_NAME, extensionDefinition.getVersion())
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .resources(false, false)
                .logging()
                .build())
            .tools(
                StatusTool.specification(this.extensionDefinition, bitwigApiFacade, structuredLogger),
                TransportTool.transportStartSpecification(transportController, structuredLogger),
                TransportTool.transportStopSpecification(transportController, structuredLogger),
                ClipTool.launchClipSpecification(clipSceneController, structuredLogger),
                SceneTool.launchSceneByIndexSpecification(clipSceneController, structuredLogger),
                SceneByNameTool.launchSceneByNameSpecification(clipSceneController, structuredLogger),
                DeviceParamTool.getSelectedDeviceParametersSpecification(deviceController, structuredLogger),
                DeviceParamTool.setSelectedDeviceParameterSpecification(deviceController, structuredLogger),
                DeviceParamTool.setMultipleDeviceParametersSpecification(deviceController, structuredLogger),
                GetDeviceDetailsTool.getDeviceDetailsSpecification(deviceController, structuredLogger),
                ListTracksTool.specification(bitwigApiFacade, structuredLogger),
                ListDevicesOnTrackTool.specification(bitwigApiFacade, structuredLogger),
                GetTrackDetailsTool.specification(bitwigApiFacade, structuredLogger),
                ListScenesTool.specification(bitwigApiFacade, structuredLogger),
                GetClipsInSceneTool.getClipsInSceneSpecification(clipSceneController, structuredLogger),
                CreateTrackTool.specification(trackConstructionController, structuredLogger),
                RenameTrackTool.specification(trackConstructionController, structuredLogger),
                DeleteTrackTool.specification(trackConstructionController, structuredLogger),
                InsertDeviceTool.specification(trackConstructionController, structuredLogger),
                DeviceCatalogTool.specification(structuredLogger),
                BwDescribeTool.specification(pathResolver, bitwigApiFacade.getBridgeGraph(), structuredLogger),
                BwGetTool.specification(pathResolver, valueReader, structuredLogger),
                BwSetTool.specification(pathResolver, valueWriter, structuredLogger),
                BwCallTool.specification(pathResolver, argCoercer, valueReader, bitwigApiFacade, structuredLogger),
                BwSnapshotTool.specification(snapshotWalker, pathResolver, valueReader, structuredLogger)
            )
            .resources(
                ApiMapResource.apiIndexSpecification(),
                ApiMapResource.rootsSpecification(bitwigApiFacade.getBridgeGraph()),
                ConfigLocationsResource.configLocationsSpecification(fileLocationsPreferences)
            )
            .build();

        // 4. Return the MCP servlet
        return new ServletHolder(this.transportProvider);
    }
}
