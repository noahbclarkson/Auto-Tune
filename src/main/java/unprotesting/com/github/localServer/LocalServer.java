package unprotesting.com.github.localServer;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.logging.Logging;

//  Local static HTTP server for price-data etc.

public class LocalServer {

    private HttpServer server;
    private String base = "plugins/Auto-Tune/web";

    public LocalServer() throws IOException{
        if (Config.isWebServer()) {
            try{
                server = HttpServer.create(new InetSocketAddress(Config.getPort()), 0);
                server.createContext("/", new StaticFileHandler(base));
                server.setExecutor(null);
                server.start();
                Logging.log("Web server has started on port " + Config.getPort());
            }
            catch(NullPointerException | IllegalArgumentException | IllegalStateException | IOException e){
                Logging.error("Error Creating Server on port: " + Config.getPort() + ". Please try restarting or changing your port.");
            }
        }
    }
    
}
