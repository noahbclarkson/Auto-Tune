package unprotesting.com.github.commands.util;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.kyori.adventure.text.Component;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.commands.objects.SectionItemData;
import unprotesting.com.github.config.Config;

public abstract class ShopFormat {

  protected boolean interpretCommand(CommandSender sender, String[] args, String permission) {

    Player player = CommandUtil.closeInventory(sender);
    int length = args.length;

    if (!(player.hasPermission(permission) || player.hasPermission("at.admin"))) {
      CommandUtil.noPermission(player);
      return true;
    }

    if (length > 1) {
      return false;
    }

    if (length == 0) {
      loadGui(sender);
      return true;
    }

    for (Section section : Main.getInstance().getCache().getSections()) {

      if (args[0].replace("-", "").replace(" ", "")
          .equalsIgnoreCase(section.getName().replace("-", "").replace(" ", ""))) {

        loadShopPane(sender, section);
        return true;

      }

    }

    return false;

  }

  protected void loadGui(CommandSender sender) {

    int highest = Section.getHighest(Main.getInstance().getCache().getSections());
    int lines = (highest / 9) + 2;
    ChestGui gui = new ChestGui(lines, Config.getConfig().getMenuTitle());
    Material mat = Material.BARRIER;

    if (!Config.getConfig().getBackground().equalsIgnoreCase("none")) {
      mat = Material.matchMaterial(Config.getConfig().getBackground());
    }

    gui = CommandUtil.getBackground(gui, lines, mat);
    gui.addPane(loadSectionsPane(sender, lines));
    gui.show((HumanEntity) (sender));

  }

  protected List<GuiItem> getListFromSection(Section section, CommandSender sender) {

    List<GuiItem> output = new ArrayList<GuiItem>();

    for (SectionItemData itemData : section.getItems()) {
      GuiItem item = getGuiItem(section, itemData, sender);
      if (item == null) {
        continue;
      }
      output.add(item);

    }
    return output;
  }

  protected void loadShopPane(CommandSender sender, Section section) {

    CommandUtil.closeInventory(sender);
    ChestGui gui = new ChestGui(6, Config.getConfig().getMenuTitle());
    PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
    List<GuiItem> items = getListFromSection(section, sender);
    List<OutlinePane> panes = new ArrayList<OutlinePane>();
    CommandUtil.loadGuiItemsIntoPane(items, gui, pages, panes, section.getBackground(), sender);

    if (section.isBack()) {
      gui.addPane(generateMenuBackPane(sender));
    }

    gui.update();
  }

  protected StaticPane generateMenuBackPane(CommandSender sender) {

    
    ItemStack item = new ItemStack(Material.ARROW);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Component.text((ChatColor.GRAY + "MENU")));

    meta.lore(Arrays.asList(new Component[] { Component.text(ChatColor.WHITE 
        + "Click to go back to the main menu")}));

    item.setItemMeta(meta);
    StaticPane output = new StaticPane(0, 0, 1, 1);

    GuiItem guiItem = new GuiItem(item, event -> {

      event.setCancelled(true);
      event.getWhoClicked().getOpenInventory().close();
      loadGui(sender);

    });

    output.addItem(guiItem, 0, 0);
    return output;
  }

  public abstract GuiItem getGuiItem(Section section,
      SectionItemData itemInput, CommandSender sender);

  public abstract StaticPane loadSectionsPane(CommandSender sender, int lines);

}
