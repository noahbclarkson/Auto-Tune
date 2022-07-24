package unprotesting.com.github.server;

import lombok.Getter;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ResourceHandler;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.Format;

/**
 * The class for starting the web server.
 */
public class LocalServer {

    @Getter
    private static LocalServer instance;

    @Getter
    private Server server;

    public static void initialize() {
        instance = new LocalServer();
        instance.start();
    }

    /**
     * Start the integrated web server.
     */
    public void start() {
        server = new Server();
        try (ServerConnector connector = new ServerConnector(server)) {
            connector.setPort(Config.get().getPort());
            server.setConnectors(new Connector[] { connector });
        }
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirAllowed(true);
        resourceHandler.setResourceBase(
                AutoTune.getInstance().getDataFolder().getAbsolutePath() + "/web");
        server.setHandler(resourceHandler);

        try {
            server.start();
        } catch (Exception e) {
            Format.getLog().severe("Failed to start local server!");
            Format.getLog().config(e.toString());
        }
    }

    /**
     * Stop the integrated web server.
     */
    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            Format.getLog().severe("Failed to stop local server!");
            Format.getLog().config(e.toString());
        }
    }

}
