package unprotesting.com.github.economy;

import lombok.Getter;

import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyFunctions {

  @Getter
  private static net.milkbowl.vault.economy.Economy economy;

  /**
   * Initializes the economy.
   * @param server The server.
   * @return Whether the economy was initialized or not.
   */
  public static boolean setupLocalEconomy(Server server) {
    return setupEconomy(server);
  }

  /**
   * Initializes the economy.
   * @param server The server.
   * @return Whether the economy was initialized or not.
   */
  private static boolean setupEconomy(Server server) {

    RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = server.getServicesManager()
        .getRegistration(net.milkbowl.vault.economy.Economy.class);

    if (rsp == null) {
      return false;
    }
    
    economy = rsp.getProvider();
    return economy != null;

  }

}
