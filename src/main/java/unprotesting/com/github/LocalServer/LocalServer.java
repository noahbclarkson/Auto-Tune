package unprotesting.com.github.LocalServer;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Logging.Logging;

//  Local static HTTP server for price-data etc.

public class LocalServer {

    private HttpServer server;
    private String base = "plugins/Auto-Tune/web";

    public LocalServer() throws IOException{
        if (Config.isWebServer()) {
            server = HttpServer.create(new InetSocketAddress(Config.getPort()), 0);
            server.createContext("/", new StaticFileHandler(base));
            server.setExecutor(null);
            server.start();
            Logging.log("Web server has started on port " + Config.getPort());
            Logging.log("Error Creating Server on port: " + Config.getPort() + ". Please try restarting or changing your port.");
        }
    }
    
}
