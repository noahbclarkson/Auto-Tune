package unprotesting.com.github.events;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.Format;

/**
 * The event for getting the IP of the server.
 */
public class IpCheckEvent extends Event {

  @Getter
  private static String ip;

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Get the IP of the server from http://checkip.amazonaws.com.
   *
   * @param isAsync Whether to run the check in a separate thread.
   */
  public IpCheckEvent(boolean isAsync) {
    super(isAsync);

    try {
      getIpString();
    } catch (IOException e) {
      Format.getLog().severe("Could not get IP!");
      Format.getLog().config(e.toString());
      ip = "http://autotune.xyz";
    }

  }

  private void getIpString() throws MalformedURLException, IOException {

    URL whatIsmMyIp = new URL("http://checkip.amazonaws.com");
    BufferedReader in = new BufferedReader(new InputStreamReader(whatIsmMyIp.openStream()));
    String hostIp = in.readLine();
    ip = "http://" + hostIp + ":" + Config.get().getPort() + "/trade.html";

  }

}
