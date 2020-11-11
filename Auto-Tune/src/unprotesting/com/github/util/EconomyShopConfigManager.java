package unprotesting.com.github.util;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;

import unprotesting.com.github.Main;

public class EconomyShopConfigManager {

    public static boolean otherEconomyPresent = false;

    static File worth;

    static PluginManager pm = Bukkit.getServer().getPluginManager();

    public static void checkForOtherEconomy() {
        if (Config.getEconomyShopConfig().toLowerCase().contains(("default"))) {
            Main.log("Using default Auto-Tune \'shops.yml\' file");
        } else {
            Main.log("Checking for economy settings for " + (Config.getEconomyShopConfig()).toLowerCase());
            if (Config.getEconomyShopConfig().toLowerCase().contains(("essentials"))) {
                if ((pm.getPlugin("Essentials") == null) && (pm.getPlugin("EssentialsX") == null)) {
                    Bukkit.getLogger().info("}---------------ERROR---------------{");
                    Bukkit.getLogger().info("Essentials must be installed!");
                    Bukkit.getLogger().info("Download it at: https://essentialsx.net/downloads.html");
                    Bukkit.getLogger().info("}---------------ERROR---------------{");
                    pm.disablePlugin(Main.getINSTANCE());
                    return;
                } else {
                    Main.log("Using Essentials shop file for shops");
                    worth = new File("plugins/Essentials/worth.yml");
                    if (!(worth.exists())) {
                        Main.log("worth.yml doesn't exist! Make sure it's present at \'plugins/Essentials/worth.yml\'");
                        return;
                    }
                    otherEconomyPresent = true;
                    return;
                }
            }
        }
    }

    public static void loadShopsFile(String type) throws IOException {
        if (type.contains("essentials")){
            Set<String> strSet =  Main.getShopConfig().getConfigurationSection("shops").getKeys(false);
            for (String str : strSet){
                Main.getShopConfig().set((("shops") + "." + (str)), null);
            }
            Main.saveEssentialsFiles();
            FileConfiguration worthConfig = new YamlConfiguration();
            worthConfig = Main.saveEssentialsFiles();
            Set<String> strSetWorth = worthConfig.getKeys(false);
            ConfigurationSection configSection = worthConfig.getConfigurationSection("worth");
            Map<String, Object> set = configSection.getValues(false);
            for (String str : set.keySet()){
                boolean next = false;
                Double value = 0.0;
                try{
                    value = (Double) set.get(str);
                }
                catch (ClassCastException e){
                    MemorySection memSection = (MemorySection)set.get(str);
                    Set<String> memSectionKeySet = memSection.getKeys(false);
                    for (String str1 : memSectionKeySet){
                        Material[] matSet2 = MaterialUtil.MatchInputAndIDToSet(str, str1);
                        for (Material mat2 : matSet2){
                            Main.log("Set " + mat2.toString() + " in file.");
                            Main.getShopConfig().getConfigurationSection("shops").set(mat2.toString() + ".price", (Double.parseDouble(memSection.get(str1).toString())));
                            next = true;
                        }
                    }
                }
                if (!next){
                    Material[] matSet = MaterialUtil.MatchInputToSet(str);
                    if (matSet != null){
                        for (Material mat : matSet){
                            Main.getShopConfig().getConfigurationSection("shops").set(mat.toString() + ".price", value);
                            Main.log("Set " + mat.toString() + " in file.");
                        }
                        continue;
                    }
                    else{
                        Main.log("Could not find Material value for: " + str);
                        continue;
                    } 
                }  
            }
            Main.saveEssentialsFiles();
            Main.getShopConfig().save(Main.getShopf());
        }
    }

}
