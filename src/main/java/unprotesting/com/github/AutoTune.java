package unprotesting.com.github;

import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import unprotesting.com.github.commands.AutosellCommand;
import unprotesting.com.github.commands.LoanCommand;
import unprotesting.com.github.commands.SellCommand;
import unprotesting.com.github.commands.ShopCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.events.*;
import unprotesting.com.github.server.LocalServer;
import unprotesting.com.github.util.EconomyUtil;

/**
 * The main class of Auto-Tune.
 */
public class AutoTune extends JavaPlugin {

    // The static instance of the plugin.
    @Getter
    private static AutoTune instance;

    @Override
    public void onEnable() {
        instance = this;

        EconomyUtil.setupLocalEconomy(Bukkit.getServer());
        Config.init();

        setupEvents();
        setupCommands();

        new Database();
        new Metrics(this, 9687);

        LocalServer.initialize();
    }

    @Override
    public void onDisable() {
        Database.get().close();
        getLogger().info("Auto-Tune is now disabled!");
    }

    private void setupCommands() {
        new ShopCommand(this);
        new SellCommand(this);
        new AutosellCommand(this);
        if (Config.get().isEnableLoans()) {
            new LoanCommand(this);
        }
    }

    private void setupEvents() {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        PluginManager pluginManager = Bukkit.getPluginManager();
        Config config = Config.get();

        scheduler.runTaskAsynchronously(this,
                () -> pluginManager.callEvent(new IpCheckEvent(true)));

        scheduler.runTaskTimerAsynchronously(this,
                () -> pluginManager.callEvent(new AutosellProfitEvent(true)),
                1200L, 1200L);

        scheduler.runTaskTimerAsynchronously(this,
                () -> pluginManager.callEvent(new TimePeriodEvent(true)),
                (long) (config.getTimePeriod() * 1200L),
                (long) (config.getTimePeriod() * 1200L));

        scheduler.runTaskTimerAsynchronously(this,
                () -> pluginManager.callEvent(new TutorialEvent(true)),
                (long) (config.getTutorialUpdate() * 20),
                (long) (config.getTutorialUpdate() * 20));

        scheduler.runTaskTimerAsynchronously(this,
                () -> pluginManager.callEvent(new AutoTuneInventoryCheckEvent(true)),
                600L, 4L);

        if (config.isEnableLoans()) {
            scheduler.runTaskTimerAsynchronously(this,
                    () -> pluginManager.callEvent(new LoanInterestEvent(true)),
                    1200L, 1200L);
        }
    }

}
