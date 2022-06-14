package unprotesting.com.github.commands.util;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Messages;

public class CommandUtil {

  /**
   * Checks if the sender is a player. If so, return true. 
   * If not, send a message to the sender and return false.
   * @param sender The CommandSender of the command.
   * @return True if the sender is a player, false if not.
   */
  public static boolean checkIfSenderPlayer(CommandSender sender) {

    if (!(sender instanceof Player)) {
      sender.sendMessage("This command is for players only.");
      return false;
    }

    return true;
  }

  public static void noPermission(Player p) {
    p.sendMessage(Messages.getMessages().getNoPermission());
  }

  /**
   * Closes the inventory of the sender if the sender is a player.
   */
  public static Player closeInventory(CommandSender sender) {

    Player player = (Player) sender;
    player.getOpenInventory().close();
    return player;

  }

  /**
   * Gets the arrow pane for a shop GUI.
   * @param page The page of the shop GUI.
   * @param displayName The display name of the arrow pane.
   * @param pane The pane of the arrow pane.
   * @param back Whether the arrow pane should be present. 
   *      I.e If back is false, the arrow pane will not be present.
   * @param gui The shop GUI.
   * @return The arrow pane.
   */
  public static StaticPane getArrowPane(int page, String displayName, PaginatedPane pane,
      boolean back, ChestGui gui) {

    StaticPane output = new StaticPane(0, 5, 1, 1);

    if (!back) {
      output = new StaticPane(8, 5, 1, 1);
    }

    ItemStack item = new ItemStack(Material.ARROW);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text(displayName, TextColor.color(255, 255, 255)));
    item.setItemMeta(meta);
    
    GuiItem guiItem = new GuiItem(item, event -> {

      event.setCancelled(true);
      pane.setPage(page);
      gui.addPane(pane);
      gui.update();

    });

    output.addItem(guiItem, 0, 0);
    return output;

  }

  /**
   * Load a list of GuiItems into a Pane.
   * @param items The list of GuiItems.
   * @param gui The GUI.
   * @param pages The number of pages.
   * @param panes The panes.
   * @param background The background material.
   * @param sender The CommandSender.
   */ 
  public static void loadGuiItemsIntoPane(List<GuiItem> items, ChestGui gui, PaginatedPane pages,
      List<OutlinePane> panes, Material background, CommandSender sender) {

    int page = 0;
    int k = 0;
    OutlinePane pane = new OutlinePane(1, 1, 7, 4);

    if (items.size() > 28) {
      pages.addPane(page, getArrowPane(page + 1, ChatColor.GRAY + "NEXT", pages, false, gui));
    }

    panes.add(pane);

    for (int i = 0; i < items.size(); i++) {

      pane = panes.get(panes.size() - 1);

      if (k > 27) {

        pane = new OutlinePane(1, 1, 7, 4);
        page++;
        pages.addPane(page, getArrowPane(page - 1, ChatColor.GRAY + "BACK", pages, true, gui));

        if (i + 28 < items.size()) {
          pages.addPane(page, getArrowPane(page + 1, ChatColor.GRAY + "NEXT", pages, false, gui));
        }

        panes.add(pane);
        k = -1;

      }

      pane.addItem(items.get(i));
      k++;

    }

    int i = 0;

    for (OutlinePane outlinePane : panes) {

      pages.addPane(i, outlinePane);
      i++;

    }

    gui.addPane(pages);
    gui = getBackground(gui, 6, background);
    gui.show((HumanEntity) (sender));

  }

  /**
   * Get the background pane for a GUI.
   * @param gui The GUI.
   * @param lines The number of lines.
   * @param backgroundItem The background material.
   */
  public static ChestGui getBackground(ChestGui gui, int lines, Material backgroundItem) {

    gui.setOnGlobalClick(event -> event.setCancelled(true));

    if (backgroundItem == null || backgroundItem.equals(Material.BARRIER)) {
      return gui;
    }

    ItemStack item = new ItemStack(backgroundItem);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text("|", Style.style(TextDecoration.OBFUSCATED)));
    item.setItemMeta(meta);
    OutlinePane background = new OutlinePane(0, 0, 9, lines, Priority.LOWEST);
    background.addItem(new GuiItem(item));
    background.setRepeat(true);
    gui.addPane(background);
    return gui;

  }

  /**
   * Get a players autosell setting.
   * @param player The player.
   * @param item The item to get the setting for.
   * @return True if the player is auto-selling the item, false if not.
   */
  public static boolean getPlayerAutoSellSetting(Player player, String item) {

    String uuid = player.getUniqueId().toString();
    return Main.getInstance().getDataFiles().getPlayerData().getBoolean(
        uuid + ".autosell." + item, false);

  }

}
