package unprotesting.com.github.events.sync;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import lombok.Getter;
import unprotesting.com.github.Main;

public class UnlockUpdateEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    private static List<String> lockableItems;

    public UnlockUpdateEvent(){
        if (lockableItems == null){loadLockableItems();}
        for (Player player : Bukkit.getOnlinePlayers()){
            String uuid = player.getUniqueId().toString();
            ItemStack[] items = player.getInventory().getContents();
            for (ItemStack item : items){
                if (item == null){continue;}
                if (lockableItems.contains(item.getType().toString())){
                    addItem(item.getType().toString(), uuid);
                }
                for (Enchantment ench : item.getEnchantments().keySet()){
                    if (lockableItems.contains(ench.toString())){
                        addItem(ench.toString(), uuid);
                    }
                }
            }
        }
    }

    private void addItem(String item, String uuid){
        if (Main.getDataFiles().getShops().getConfigurationSection("shops")
        .getConfigurationSection(item).getString("collect-first-setting").equals("SERVER_WIDE")){
            uuid = "server";
        }
        YamlConfiguration config = Main.getDataFiles().getPlayerData();
        if (!config.contains(uuid + ".unlocked")){
            config.createSection(uuid + ".unlocked");
            config.set(uuid + ".unlocked." + item, true);
            Main.getDataFiles().setPlayerData(config);
            return;
        }
        else if (!config.contains(uuid + ".unlocked." + item)){
            config.createSection(uuid + ".unlocked." + item);
            config.set(uuid + ".unlocked." + item, true);
            Main.getDataFiles().setPlayerData(config);
            return;
        }
        return;
    }

    public static boolean isUnlocked(Player player, String item_name){
        if (lockableItems == null){loadLockableItems();}
        if (lockableItems.contains(item_name)){
            String uuid = player.getUniqueId().toString();
            if (Main.getDataFiles().getShops().getConfigurationSection("shops")
            .getConfigurationSection(item_name).getString("collect-first-setting").equals("SERVER_WIDE")){
                uuid = "server";
            }
            if (Main.getDataFiles().getPlayerData().contains(uuid + ".unlocked." + item_name)){
                return Main.getDataFiles().getPlayerData().getBoolean(uuid + ".unlocked." + item_name);
            }
            else {
                return false;
            }
        }
        return true;
    }

    private static void loadLockableItems(){
        lockableItems = new ArrayList<String>();
        ConfigurationSection shops = Main.getDataFiles().getShops().getConfigurationSection("shops");
        for (String key : shops.getKeys(false)){
            ConfigurationSection inner = shops.getConfigurationSection(key);
            if (!inner.getString("collect-first-setting", "NONE").equalsIgnoreCase("NONE")){
                addLockableItem(key);
            }
        }
    }

    private static void addLockableItem(String item){
        lockableItems.add(item);
    }
    


    
}
