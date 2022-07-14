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
     */
    public void setupLocalEconomy(@NotNull Server server) {
        RegisteredServiceProvider<Economy> rsp = server.getServicesManager()
                .getRegistration(Economy.class);

        if (rsp == null) {
            return;
        }

        economy = rsp.getProvider();
    }

}
