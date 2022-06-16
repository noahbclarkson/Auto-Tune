package unprotesting.com.github.localserver;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

public class LocalServer {

  private HttpServer server;
  private String base = "plugins/Auto-Tune/web";

  /**
   * Start the local server.
   */
  public LocalServer() {

    // If "web-server-enabled" is false, don't start the server.
    if (!Config.getConfig().isWebServerEnabled()) {
      return;
    }

    try {

      server = HttpServer.create(new InetSocketAddress(Config.getConfig().getPort()), 0);
      server.createContext("/", new StaticFileHandler(base));
      server.setExecutor(null);
      server.start();
      
      Main.getInstance().getLogger().info("Local server started on port " 
          + Config.getConfig().getPort());

    } catch (Exception e) {

      Main.getInstance().getLogger().warning(
          "Error Creating Server on port: " + Config.getConfig().getPort()
           + ". Please try restarting or changing your port.");

    }
  }

}