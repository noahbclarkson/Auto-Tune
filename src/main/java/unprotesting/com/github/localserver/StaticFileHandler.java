package unprotesting.com.github.localserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;

import lombok.Cleanup;

import unprotesting.com.github.Main;

public class StaticFileHandler implements HttpHandler {

  private final String baseDir;

  /**
   * StaticFileHandler constructor.
   * @param baseDir The base directory of the static files.
   */
  public StaticFileHandler(String baseDir) {
    this.baseDir = baseDir;
  }

  /**
   * Handles the HTTP request.
   * @param ex The HTTP exchange.
   * @throws IOException If an error occurs.
   */
  @Override
  public void handle(HttpExchange ex) throws IOException {

    URI uri = ex.getRequestURI();
    String name = new File(uri.getPath()).getName();
    File path = new File(baseDir, name);
    @Cleanup OutputStream out = ex.getResponseBody();

    if (path.exists()) {

      ex.sendResponseHeaders(200, path.length());
      out.write(Files.readAllBytes(path.toPath()));

    } else {

      Main.getInstance().getLogger().warning("404 File not found: " + path.getAbsolutePath());
      ex.sendResponseHeaders(404, 0);

    }
    
  }

}