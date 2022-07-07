package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.data.PurchaseUtil;
import unprotesting.com.github.util.Format;

/**
 * The command for selling items.
 */
public class SellCommand implements CommandExecutor {

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, @NotNull String[] args) {

    if (sender instanceof Player) {
      return interpret(sender);
    }

    Format.sendMessage(sender, "<red>You must be a player to use this command.");
    return true;
  }

  private boolean interpret(CommandSender sender) {
    Player player = (Player) sender;
    player.getOpenInventory().close();
    ChestGui gui = new ChestGui(5, "Sell Panel");
    gui.setOnClose(event -> {
      for (ItemStack item : gui.getInventory().getContents()) {
        if (item == null) {
          continue;
        }
        PurchaseUtil.sellItemStack(item, player);
      }
    });
    gui.show((HumanEntity) sender);
    return true;
  }

}
