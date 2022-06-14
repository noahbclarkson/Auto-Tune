package unprotesting.com.github.events.async;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import lombok.Getter;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

public class IpCheckEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Checks the IP of the server.
   */
  public IpCheckEvent(boolean isAsync) {

    super(isAsync);
    Main.getInstance().setServerIPs(getIP());

  }

  /**
   * Gets the server IPs.
   * @return The server IPs.
   */
  private String[] getIP() {

    String hostIP;

    try {

      URL whatIsmMyIp = new URL("http://checkip.amazonaws.com");
      BufferedReader in = new BufferedReader(new InputStreamReader(whatIsmMyIp.openStream()));
      hostIP = in.readLine().trim();

    } catch (IOException e) {

      Main.getInstance().getLogger().severe("Could not get the server IPs.");
      String error = "http://autotune.xyz";
      return new String[] { error, error };

    }

    String base = "http://" + hostIP + ":" + Config.getConfig().getPort();
    String[] output = { base + "/trade.html" };
    return output;

  }

}
