package unprotesting.com.github.Economy;

import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

import lombok.Getter;
import net.milkbowl.vault.economy.Economy;

//  Economy link to vault

public class EconomyFunctions {

    @Getter
    public static Economy economy;

    public static boolean setupLocalEconomy(Server server){
        return setupEconomy(server);
    }

    private static boolean setupEconomy(Server server) {
        RegisteredServiceProvider<Economy> rsp = server.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) {
          return false;
        }
        economy = rsp.getProvider();

        return economy != null;
    }
    
}
