package unprotesting.com.github;

import lombok.Getter;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import unprotesting.com.github.commands.AutosellCommand;
import unprotesting.com.github.commands.LoanCommand;
import unprotesting.com.github.commands.SellCommand;
import unprotesting.com.github.commands.ShopCommand;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.events.AutoTuneInventoryCheckEvent;
import unprotesting.com.github.events.AutosellProfitEvent;
import unprotesting.com.github.events.IpCheckEvent;
import unprotesting.com.github.events.LoanInterestEvent;
import unprotesting.com.github.events.TimePeriodEvent;
import unprotesting.com.github.events.TutorialEvent;
import unprotesting.com.github.util.EconomyUtil;

@Getter
public class AutoTune extends JavaPlugin {

  @Getter
  // The static instance of the plugin.
  private static AutoTune instance;

  @Override
  public void onEnable() {
    instance = this;
    EconomyUtil.setupLocalEconomy(Bukkit.getServer());
    Config.init();
    new Database();
    setupEvents();
    setupCommands();
    new Metrics(this, 9687);
    getLogger().info("Auto-Tune is now enabled!");
  }

  @Override
  public void onDisable() {
    Database.get().close();
    getLogger().info("Auto-Tune is now disabled!");
  }

  private void setupCommands() {
    getCommand("shop").setExecutor(new ShopCommand());
    getCommand("sell").setExecutor(new SellCommand());
    getCommand("autosell").setExecutor(new AutosellCommand());
    getCommand("loan").setExecutor(new LoanCommand());
  }

  private void setupEvents() {

    Bukkit.getScheduler().runTaskAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new IpCheckEvent(true)));

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new AutosellProfitEvent(true)),
        1200L, 1200L);

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new LoanInterestEvent(true)),
        1200L, 1200L);

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new TimePeriodEvent(true)),
        (long) (Config.get().getTimePeriod() * 1200L),
        (long) (Config.get().getTimePeriod() * 1200L));

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> 
        Bukkit.getPluginManager().callEvent(new TutorialEvent(true)),
        (long) (Config.get().getTutorialUpdate() * 20),
        (long) (Config.get().getTutorialUpdate() * 20));

    Bukkit.getScheduler().runTaskTimerAsynchronously(this, () ->
        Bukkit.getPluginManager().callEvent(new AutoTuneInventoryCheckEvent(true)),
        200L, 4L);

  }


  
}
