package unprotesting.com.github;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;
import com.opencsv.CSVReader;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.Economy;

public class Main extends JavaPlugin implements Listener {

    private static final Logger log = Logger.getLogger("Minecraft");
    private static Economy econ;
    private static JavaPlugin plugin;
    File resources = new File("plugins/Auto-Tune/", "resources.yml");
    File playerdata = new File("plugins/Auto-Tune/", "playerdata.yml");
    FileConfiguration resourcesconfig = YamlConfiguration.loadConfiguration(resources);
    File cfile;
    FileConfiguration config = this.getConfig();
    public static final String BASEDIR = "plugins/Auto-Tune/Trade";
    public static final String BASEDIRMAIN = "plugins/Auto-Tune/data.csv";
    public FileConfiguration playerDataConfig;
    public final String playerdatafilename = "playerdata.yml";

    @Override
    public void onDisable() {
        cancelAllTasks(this);
        log.info(String.format("[%s] Disabled Version %s", getDescription().getName(), getDescription().getVersion()));
    }

    private void cancelAllTasks(Main main) {
    }

    @Override
    public void onEnable() {
        Bukkit.getServer().getPluginManager().registerEvents(new JoinEventHandler(), this);
        config.addDefault("Port", 8321);
        config.addDefault("Check-Time", 60);
        config.addDefault("Volatility", 1);
        config.options().copyDefaults(true);
        saveConfig();
        try {
            resourcesconfig.save(resources);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int PORT = getConfig().getInt("Port");
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/static", new StaticFileHandler(BASEDIR));
            server.setExecutor(null);
            server.start();
            log.info("[Auto Tune] Web server has started on port " + PORT);

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        
        playerDataConfig = YamlConfiguration.loadConfiguration(playerdata);
        saveplayerdata();

        this.getCommand("at").setExecutor(new AutoTuneCommand());
        if (!setupEconomy()) {
            log.severe(
                    String.format("Disabled Auto-Tune due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }



    public boolean onCommand(CommandSender sender, Command testcmd, String trade, String[] help) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            String hostIP = "";
            try
        { 
            URL url_name = new URL("http://bot.whatismyipaddress.com"); 
  
            BufferedReader sc = 
            new BufferedReader(new InputStreamReader(url_name.openStream())); 
  
            // reads system IPAddress 
            hostIP = sc.readLine().trim(); 

            int PORT = getConfig().getInt("Port");
            InetAddress address = InetAddress.getLocalHost();
            String hostName = address.getHostName();
            this.getCommand("at").setExecutor(new AutoTuneCommand());
            TextComponent message = new TextComponent(ChatColor.YELLOW + "" + ChatColor.BOLD + player.getDisplayName() + ", go to http://" + hostIP + ":" + PORT + "/static/index.html");
            message.setHoverEvent( new HoverEvent( HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Click to begin trading online").create() ) );
            message.setClickEvent( new ClickEvent( ClickEvent.Action.OPEN_URL, "http://" + hostIP + ":" + PORT + "/static/index.html") );
            player.spigot().sendMessage(message);
            player.sendMessage(ChatColor.ITALIC + "Hostname : "+ hostName);
        } 
        catch (Exception e) 
        { 
            hostIP = "Cannot Execute Properly"; 
        } 
            return true;
        }
        return false;
    }


    void saveplayerdata(){
        try {
            YamlConfiguration.loadConfiguration(playerdata);
            playerDataConfig.save(playerdata);
        } catch (IOException e) {
            plugin.getLogger().warning("Unable to save " + playerdatafilename); // shouldn't really happen, but save throws the
                                                                      // exception
        }

    }

}

