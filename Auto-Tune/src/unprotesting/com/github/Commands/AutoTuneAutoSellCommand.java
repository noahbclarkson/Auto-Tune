package unprotesting.com.github.Commands;

import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneAutoSellCommand implements CommandExecutor{


    @Override
	public boolean onCommand(CommandSender sender, Command command, String shop, String[] args) {
		if (command.getName().equalsIgnoreCase("autosell")) {
			if (!(sender instanceof Player)) {
				Main.sendMessage(sender, "&cPlayers only.");
				return true;
			}
			Player p = (Player) sender;
			if (Config.getMenuRows() == 6) {
				AutoTuneGUIShopUserCommand.SBPanePos = 2;
			}
			if (args.length == 0) {
				if (p.hasPermission("at.autosell") || p.isOp()) {
                    if (!Config.isAutoSellEnabled()){
                        p.sendMessage(ChatColor.RED + "Auto-Selling is Disabled");
                        return true;
                    }
					AutoTuneGUIShopUserCommand.loadGUISECTIONS(p, true);
                    return true;
				} else if (!(p.hasPermission("at.autosell")) && !(p.isOp())) {
					TextHandler.noPermssion(p);
                    return true;
				}
			}
			if (args.length == 1) {
                if (p.hasPermission("at.autosell") || p.isOp()) {
				} else if (!(p.hasPermission("at.autosell")) && !(p.isOp())) {
					TextHandler.noPermssion(p);
                    return true;
				}
				String inputSection = null;
				try {
					inputSection = args[0];
				} catch (ClassCastException ex) {
					p.sendMessage("Unknown shop format: " + args[0]);
					return false;
				} catch (ArrayIndexOutOfBoundsException ex) {
					return false;
				}
				for (int i = 0; i < Main.sectionedItems.length; i++) {
					if (Main.sectionedItems[i].name.toLowerCase().equals(inputSection)) {
						AutoTuneGUIShopUserCommand.loadGUIMAIN(p, Main.sectionedItems[i], true, true);
						return true;
					}
				}
			} else {
				return false;
			}
		}
		return false;
	}

    public static void changePlayerAutoSellSettings(Player player, String material){
		if (Config.isUsePermissionsForShop()){
			if (!player.hasPermission("at.sell." + material)){
				player.sendMessage(ChatColor.RED + "WARNING: You do not have permission to sell this item at this time!");
			}
		}
        UUID uuid = player.getUniqueId();
        Boolean autosellset = false;
        Main.playerDataConfig.contains(uuid + ".AutoSell");
        autosellset = true;
        if (autosellset == false){
            Main.playerDataConfig.createSection(uuid + ".AutoSell");
        }
        Boolean atonoff = Main.playerDataConfig.getBoolean(uuid + ".AutoSell" + "." + material);
        if (!(Main.playerDataConfig.contains(uuid + ".AutoSell" + "." + material))) {
             Main.playerDataConfig.createSection(uuid + ".AutoSell" + "." + material);
        }
        if (atonoff == false){
            Main.playerDataConfig.set(uuid + ".AutoSell" + "." + material, true);
        }
        if (atonoff == true){
            Main.playerDataConfig.set(uuid + ".AutoSell" + "." + material, false);
        }
        Main.saveplayerdata();
    }

    
}