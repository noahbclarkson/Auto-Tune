package unprotesting.com.github.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.logging.Logging;

public class TradeCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String trade, String[] args) {
        if (sender instanceof Player){
            Player player = CommandUtil.closeInventory(sender);
            if (!(player.hasPermission("at.trade") || player.hasPermission("at.admin"))){CommandUtil.noPermssion(player);return true;}
            String[] arr = Main.getServerIPStrings();
            player.spigot().sendMessage(createTextComponent(ChatColor.YELLOW + "View Overall Item Prices", "Click to go to " + arr[0], arr[0]));
            player.spigot().sendMessage(createTextComponent(ChatColor.YELLOW + "View Recent Item Prices", "Click to go to " + arr[1], arr[1]));
            return true;
        }
        else{
            String[] arr = Main.getServerIPStrings();
            Logging.log("Go to: " + arr[0] + " for overall item prices.");
            Logging.log("Go to: " + arr[1] + " for recent item prices.");
            return true;
        }
    }

    @Deprecated
    private TextComponent createTextComponent(String base, String hover, String http_click){
        TextComponent message = new TextComponent(base);
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hover).create()));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, http_click));
        return message;
    }
    
}
