package unprotesting.com.github.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneSellCommand;

public class AutoSellEventHandler implements Runnable {

    private Main main = Main.getPlugin(Main.class);

    @Override
    public void run() {
        for(Player player : Main.getINSTANCE().getServer().getOnlinePlayers()){
            if (!(player.hasPermission("at.autosell")) && !(player.isOp())){
                continue;
            }
            UUID uuid = player.getUniqueId();
            ConfigurationSection config = main.playerDataConfig.getConfigurationSection(uuid + ".AutoSell");
            if (player.getInventory().getContents() != null && config != null){
            ConcurrentHashMap<Integer, ItemStack> itemstosell = new ConcurrentHashMap<Integer, ItemStack>();
            for (String material : config.getKeys(false)){
                if (player.getInventory().contains(Material.matchMaterial(material)) && main.playerDataConfig.getBoolean(uuid + ".AutoSell" + "." + material)==true){
                    Integer amount = getAmount(player, Material.matchMaterial(material));
                if (amount <= 64){
                    ItemStack tosell = new ItemStack(Material.matchMaterial(material), amount);
                    itemstosell.put(itemstosell.size(), tosell);
                    }
                else{
                    double x = amount/64;
                    for (;x>1;x--){
                    ItemStack tosell = new ItemStack(Material.matchMaterial(material), amount);
                    itemstosell.put(itemstosell.size(), tosell);
                    }
                    int i = (int)x*64;
                    ItemStack tosell1 = new ItemStack(Material.matchMaterial(material), i);
                    itemstosell.put(itemstosell.size(), tosell1);
                    }   
                }
                else{
                    continue;
                }
            }
            int index = 0;
            ItemStack[] tosellMain = new ItemStack[itemstosell.size()];
            for (Map.Entry<Integer, ItemStack> mapEntry : itemstosell.entrySet()) {
                tosellMain[index] = mapEntry.getValue();
                index++;
            }
            for (ItemStack is : tosellMain){
                player.getInventory().remove(is);
            }
            AutoTuneSellCommand.sellItems(player, tosellMain, true);
            }
        }
    } 

    public static int getAmount(Player player, Material mat)
{
        Inventory inventory = player.getInventory();
        ItemStack[] items = inventory.getContents();
        int has = 0;
        for (ItemStack item : items)
        {
            if ((item != null) && (item.getType() == mat) && (item.getAmount() > 0))
            {
                has += item.getAmount();
            }
        }
        return has;
    }
    
}