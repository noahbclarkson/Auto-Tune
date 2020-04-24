package unprotesting.com.github;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import java.security.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private Economy econ;
    private Permissions perms;
    private Chat chat;

    @Override
    public void onEnable(){
        if (!setupEconomy()) {
            this.getLogger().severe("Disabled due to no Vault dependency found!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.setupPermissions();
        this.setupChat();
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private boolean setupChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        chat = rsp.getProvider();
        return chat != null;
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permissions> rsp = getServer().getServicesManager().getRegistration(Permissions.class);
        perms = rsp.getProvider();
        return perms != null;
    }

    public Economy getEconomy() {
        return econ;
    }

    public Permissions getPermissions() {
        return perms;
    }

    public Chat getChat() {
        return chat;
    }

}
 
