package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;

import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Section;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;

public class AutosellCommand extends AutoTuneShopFormat implements CommandExecutor {

  
  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, @NotNull String[] args) {
    
    if (sender instanceof Player) {
      return interpret((Player) sender, args);
    }

    Format.sendMessage(sender, "<red>You must be a player to use this command.");
    return true;
  }

  @Override
  protected void doShop(HumanEntity player, ChestGui gui, String shopName) {
    Shop shop = ShopUtil.getShop(shopName);

    if (shop.isEnchantment()) {
      Format.sendMessage(player, "<red>You cannot autosell enchanted items at the moment.");
      return;
    }

    Player user = (Player) player;
    UUID uuid = user.getUniqueId();
    ConfigurationSection autosell = Config.get().getAutosell();

    if (autosell.contains(uuid + "." + shopName)) {
      boolean value = autosell.getBoolean(uuid + "." + shopName);
      autosell.set(uuid + "." + shopName, !value);
    } else {
      autosell.set(uuid + "." + shopName, true);
    }
    
    Config.get().setAutosell(autosell);
    Section section = ShopUtil.getSection(shop.getSection());
    gui.getPanes().clear();
    getBackground(gui);
    gui.addPane(loadShopPane(user, gui, section));
    gui.addPane(getGdpPane(user, gui));
    gui.update();
  }

  @Override
  protected List<Component> applyLore(Player player, String shopName, int amount) {
    return getLore(player, shopName, Config.get().getAutosellLore(), amount);
  }
  
}
