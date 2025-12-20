package unprotesting.com.github.util;

import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

/**
 * The class for managing the economy.
 */
@UtilityClass
public class EconomyUtil {

    @Getter
    private static Economy economy;

    /**
     * Initializes the economy.
     *
     * @param server The server.
     * @return true if economy successfully hooked, false otherwise
     */
    public boolean setupLocalEconomy(Server server) {
        if (server.getPluginManager().getPlugin("Vault") == null) {
            return false; // Vault not installed
        }

        RegisteredServiceProvider<Economy> rsp = server.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false; // No economy plugin hooked
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean isReady() {
        return economy != null;
    }

}
