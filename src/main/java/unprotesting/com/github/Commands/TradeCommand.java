package unprotesting.com.github.Commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import unprotesting.com.github.Commands.Util.CommandUtil;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Logging.Logging;

public class TradeCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String trade, String[] args) {
        if (sender instanceof Player){
            Player player = (Player) sender;
            if (!(player.hasPermission("at.trade") || player.isOp())) {CommandUtil.noPermssion(player);return true;}
            String[] arr = getServerIPString();
            player.spigot().sendMessage(createTextComponent(ChatColor.YELLOW + "View Overall Item Prices", "Click to go to " + arr[0], arr[0]));
            player.spigot().sendMessage(createTextComponent(ChatColor.YELLOW + "View Recent Item Prices", "Click to go to " + arr[1], arr[1]));
            return true;
        }
        else{
            String[] arr = getServerIPString();
            Logging.log("Go to: " + arr[0] + " for overall item prices.");
            Logging.log("Go to: " + arr[1] + " for recent item prices.");
            return true;
        }
    }

    private String[] getServerIPString(){
        String hostIP;
        try {
            URL url_name = new URL("http://bot.whatismyipaddress.com");
            BufferedReader sc = new BufferedReader(new InputStreamReader(url_name.openStream()));
            hostIP = sc.readLine().trim();
        } catch (IOException e) {
            e.printStackTrace();
            String offical_website = "http://autotune.xyz";
            return new String[] {offical_website, offical_website};
        }
        String base = "http://" + hostIP + ":" + Config.getPort();
        String[] output = {base + "/trade.html", base + "trade-short.html"};
        return output;
    }

    @Deprecated
    private TextComponent createTextComponent(String base, String hover, String http_click){
        TextComponent message = new TextComponent(base);
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hover).create()));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, http_click));
        return message;
    }
    
}
