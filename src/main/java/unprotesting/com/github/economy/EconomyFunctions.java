package unprotesting.com.github.economy;

import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import lombok.Getter;

//  Economy link to vault

public class EconomyFunctions {

    @Getter
    private static net.milkbowl.vault.economy.Economy economy;

    public static boolean setupLocalEconomy(Server server){
        return setupEconomy(server);
    }

    private static boolean setupEconomy(Server server) {
        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = server.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) {
          return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }
    
}
