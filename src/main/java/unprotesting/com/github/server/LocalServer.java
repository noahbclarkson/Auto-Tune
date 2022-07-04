package unprotesting.com.github.server;

import lombok.Getter;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.Format;

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
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(Config.get().getPort());
    server.setConnectors(new Connector[] {connector});
    ServletHandler servletHandler = new ServletHandler();
    server.setHandler(servletHandler);
    servletHandler.addServletWithMapping(AutoTuneServlet.class, "/status");
    try {
      server.start();
    } catch (Exception e) {
      Format.getLog().severe("Failed to start local server!");
    }
  }

  void stop() {
    try {
      server.stop();
    } catch (Exception e) {
      Format.getLog().severe("Failed to stop local server!");
    }
  }
  
} 
