package unprotesting.com.github.events.sync;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.FunctionsUtil;

public class AutosellUpdateEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public AutosellUpdateEvent(){
        YamlConfiguration config = Main.getDfiles().getPlayerData();
        for (Player player : Bukkit.getOnlinePlayers()){
            List<String> data = new ArrayList<String>();
            ConfigurationSection section = config.getConfigurationSection(player.getUniqueId().toString() + ".autosell");
            if (section == null){
                continue;
            }
            for (String key : section.getKeys(false)){
                if (section.getBoolean(key)){
                    data.add(key);
                }
            }
            if (data.size() > 1){
                ItemStack[] items = player.getInventory().getContents();
                for (ItemStack item : items){
                    if (item == null){
                        continue;
                    }
                    if (data.contains(item.getType().toString())){
                        player.getInventory().remove(item);
                        FunctionsUtil.sellCustomItem(player, item, true);
                    }
                }
            }
        }
    }
}