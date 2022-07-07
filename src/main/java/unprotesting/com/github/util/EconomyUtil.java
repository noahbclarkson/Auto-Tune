package unprotesting.com.github.util;

import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * The class for managing the economy.
 */
public class EconomyUtil {

  @Getter
  private static Economy economy;

  /**
   * Initializes the economy.
   *
   * @param server The server.
   * @return Whether the economy was initialized or not.
   */
  public static boolean setupLocalEconomy(Server server) {
    return setupEconomy(server);
  }

  private static boolean setupEconomy(Server server) {
    RegisteredServiceProvider<Economy> rsp = server.getServicesManager()
        .getRegistration(Economy.class);

    if (rsp == null) {
      return false;
    }

    economy = rsp.getProvider();
    return economy != null;
  }

}
