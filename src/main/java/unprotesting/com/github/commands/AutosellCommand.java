package unprotesting.com.github.commands;

import java.text.DecimalFormat;
import java.util.Arrays;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.commands.util.ShopFormat;
import unprotesting.com.github.config.Config;

public class AutosellCommand extends ShopFormat implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String autosell, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender, args, "at.autosell");
    }

    public StaticPane loadSectionsPane(CommandSender sender, int lines){
        StaticPane navigationPane = new StaticPane(0, 0, 9, lines);
        for (Section section : Main.getCache().getSECTIONS()){
            if (section.isEnchantmentSection()){
                continue;
            }
            int x = section.getPosition() % 9;
            int y = section.getPosition() / 9;
            ItemStack item = new ItemStack(section.getImage());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(section.getDisplayName());
            meta.setLore(Arrays.asList(new String[]{ChatColor.WHITE + "Click to change " + section.getName() + " autosell settings."}));
            item.setItemMeta(meta);
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                loadShopPane(sender, section);
            });
            navigationPane.addItem(gItem, x, y);
        }
        return navigationPane;
    }

    public GuiItem getGUIItem(Section section, String s_item, String displayName, Player player, CommandSender sender, DecimalFormat df){
        ItemStack item = new ItemStack(Material.matchMaterial(s_item));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        String lore;
        boolean setting = CommandUtil.getPlayerAutoSellSetting(player, item.getType().toString());
        if (setting){
            lore = ChatColor.GREEN + "Click to turn off auto-selling!";
        }
        else {
            lore = ChatColor.RED + "Click to turn on auto-selling!";
        }
        meta.setLore(Arrays.asList(new String[]{lore, ChatColor.WHITE + "Sell-Price: "
         + ChatColor.GOLD + Config.getCurrencySymbol() + df.format(Main.getCache().getItemPrice(item.getType().toString(), true))}));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
            changePlayerAutoSellSetting(player, item.getType().toString());
            player.getOpenInventory().close();
            loadShopPane(sender, section);
        });
        return gItem;
    }

    private void changePlayerAutoSellSetting(Player player, String item){
        String uuid = player.getUniqueId().toString();
        YamlConfiguration config = Main.getDataFiles().getPlayerData();
        if (!config.contains(uuid + ".autosell")){
            config.createSection(uuid + ".autosell");
            config.set(uuid + ".autosell." + item, true);
            Main.getDataFiles().setPlayerData(config);
            return;
        }
        else if (!config.contains(uuid + ".autosell." + item)){
            config.createSection(uuid + ".autosell." + item);
            config.set(uuid + ".autosell." + item, true);
            Main.getDataFiles().setPlayerData(config);
            return;
        }
        else{
            boolean setting = false;
            setting = config.getBoolean(uuid + ".autosell." + item, false);
            config.set(uuid + ".autosell." + item, !setting);
        }
    }
    
}
