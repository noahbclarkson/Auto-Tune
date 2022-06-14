package unprotesting.com.github.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.CommandUtil;


public class TradeCommand implements CommandExecutor {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String trade, String[] args) {

    String[] arr = Main.getInstance().getServerIPs();

    if (sender instanceof Player) {

      Player player = CommandUtil.closeInventory(sender);

      if (!(player.hasPermission("at.trade") || player.hasPermission("at.admin"))) {

        CommandUtil.noPermission(player);
        return true;

      }

      player.spigot().sendMessage(createTextComponent(ChatColor.YELLOW 
          + "View Overall Item Prices", "Click to go to " + arr[0], arr[0]));

      return true;

    }

    Main.getInstance().getLogger().info("Go to: " + arr[0] + " for overall item prices.");
    return true;

  }

  @Deprecated
  private TextComponent createTextComponent(String base, String hover, String httpClick) {

    TextComponent message = new TextComponent(base);

    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
        new ComponentBuilder(hover).create()));

    message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, httpClick));
    return message;

  }

}
