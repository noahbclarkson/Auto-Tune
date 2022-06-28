package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.EconomyDataUtil;
import unprotesting.com.github.data.Section;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;

public abstract class AutoTuneShopFormat {

  private static OutlinePane background;

  protected boolean interpret(CommandSender sender, String[] args) {

    if (args.length > 1) {
      return false;
    }

    ChestGui gui = new ChestGui(6, "Shop");
    gui.setOnGlobalClick(event -> event.setCancelled(true));
    getBackground(gui);

    if (args.length == 0) {
      gui.addPane(loadSectionsPane((Player) sender, gui));
    }

    if (args.length == 1) {
      Section section = ShopUtil.getSection(args[0]);
      if (section == null) {
        Format.sendMessage((Player) sender, Config.get().getInvalidShopSection());
        return true;
      }
      gui.addPane(loadShopPane((Player) sender, gui, section));
    }
    
    gui.show((HumanEntity) sender);
    return true;
  }

  private StaticPane loadSectionsPane(Player player, ChestGui gui) {
    StaticPane pane = new StaticPane(0, 0, 9, 6, Priority.HIGHEST);

    for (String sectionName : ShopUtil.getSectionNames()) {
      Section section = ShopUtil.getSection(sectionName);
      GuiItem item = new GuiItem(section.getItem(), event -> {
        event.setCancelled(true);
        gui.getPanes().clear();
        getBackground(gui);
        gui.addPane(loadShopPane(player, gui, section));
        gui.addPane(getGdpPane(player, gui));
        gui.update();
      });
      pane.addItem(item, section.getPosX(), section.getPosY());
    }

    return pane;
  }

  protected PaginatedPane loadShopPane(Player player, ChestGui gui, Section section) {
    PaginatedPane pages = new PaginatedPane(0, 0, 9, 6, Priority.HIGHEST);
    Map<String, Shop> shops = section.getShops();
    List<String> shopNames = new ArrayList<>(shops.keySet());
    shopNames.sort((s1, s2) -> s1.compareToIgnoreCase(s2));
    int page = 0;
    List<GuiItem> itemsOnPage = new ArrayList<>();

    for (String shopName : shopNames) {
      ItemStack item = shops.get(shopName).isEnchantment() ? new ItemStack(Material.ENCHANTED_BOOK)
          : new ItemStack(Material.matchMaterial(shopName));
      if (shops.get(shopName).isEnchantment()) {
        Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(shopName));
        item.editMeta(meta -> meta.displayName(enchantment.displayName(1)));
      }
      item.lore(applyLore(player, shopName, 1));
      itemsOnPage.add(new GuiItem(item, event -> {
        event.setCancelled(true);
        doShop(event.getWhoClicked(), gui, shopName);
        gui.update();
      }));

      if (itemsOnPage.size() == 28 || itemsOnPage.size() + page * 28 == shops.size()) {
        OutlinePane pane = new OutlinePane(1, 1, 7, 4, Priority.HIGHEST);

        for (GuiItem guiItem : itemsOnPage) {
          pane.addItem(guiItem);
        }

        if (page != 0) {
          pages.addPane(page, getPageSelector(gui, pages, page - 1, 0));
        }

        if (itemsOnPage.size() == 28 && itemsOnPage.size() + page * 28 != shops.size()) {
          pages.addPane(page, getPageSelector(gui, pages, page + 1, 8));
        }

        pages.addPane(page, pane);
        pages.addPane(page, getBackToSectionsPane(player, gui));
        page++;
        itemsOnPage.clear();
      }
    }
    
    return pages;
  }

  private StaticPane getBackToSectionsPane(Player player, ChestGui gui) {
    StaticPane pane = new StaticPane(0, 0, 1, 1, Priority.HIGHEST);
    ItemStack item = new ItemStack(Material.ARROW);
    item.editMeta(meta ->
        meta.displayName(Format.getComponent("<b><white>Back to menu").asComponent()));
    pane.addItem(new GuiItem(item, event -> {
      event.setCancelled(true);
      gui.getPanes().clear();
      getBackground(gui);
      gui.addPane(loadSectionsPane(player, gui));
      gui.addPane(getGdpPane(player, gui));
      gui.update();
    }), 0, 0);
    return pane;
  }

  private StaticPane getPageSelector(ChestGui gui, PaginatedPane pages, int page, int x) {
    StaticPane pane = new StaticPane(x, 5, 1, 1, Priority.HIGHEST);
    ItemStack item = new ItemStack(Material.ARROW);
    item.editMeta(meta ->
        meta.displayName(Format.getComponent(
        "<b><white>Go to page " + page + 1).asComponent()));
    pane.addItem(new GuiItem(item, event -> {
      event.setCancelled(true);
      pages.setPage(page);
      gui.update();
    }), 0, 0);
    return pane;
  }

  protected StaticPane getBackToShop(Player player, ChestGui gui, String sectionName) {
    StaticPane pane = new StaticPane(0, 0, 1, 1, Priority.HIGHEST);
    ItemStack item = new ItemStack(Material.ARROW);
    Section section = ShopUtil.getSection(sectionName);
    item.editMeta(meta ->
        meta.displayName(section.getItem().displayName()));
    pane.addItem(new GuiItem(item, event -> {
      event.setCancelled(true);
      gui.getPanes().clear();
      getBackground(gui);
      gui.addPane(loadShopPane(player, gui, section));
      gui.addPane(getGdpPane(player, gui));
      gui.update();
    }), 0, 0);
    return pane;
  }

  protected StaticPane getGdpPane(Player player, ChestGui gui) {
    ItemStack item = new ItemStack(Material.GOLD_INGOT);
    TagResolver r = getGdpTagResolver();
    item.editMeta(meta ->
        Format.getComponent(Config.get().getShopGdpLore().get(0), r));
    List<Component> lore = new ArrayList<>();

    for (int i = 1; i < Config.get().getShopGdpLore().size(); i++) {
      lore.add(Format.getComponent(Config.get().getShopGdpLore().get(i), r));
    }

    item.lore(lore);
    StaticPane pane = new StaticPane(8, 0, 1, 1, Priority.HIGHEST);
    pane.addItem(new GuiItem(item, event -> {
      event.setCancelled(true);
    }), 0, 0);
    return pane;
  }

  protected static void getBackground(ChestGui gui) {

    if (background != null) {
      gui.addPane(background);
      return;
    }

    GuiItem item = getBackgroundItem();

    if (item == null) {
      return;
    }

    OutlinePane pane = new OutlinePane(0, 0, 9, 6, Priority.LOWEST);
    pane.addItem(item);
    pane.setRepeat(true);
    gui.addPane(pane);
    background = pane;
  }

  protected static GuiItem getBackgroundItem() {
    Material material = Material.matchMaterial(Config.get().getBackground());

    if (material == null) {
      return null;
    }

    ItemStack item = new ItemStack(material);
    item.editMeta(meta -> 
        meta.displayName(Component.text("|", Style.style(TextDecoration.OBFUSCATED))));
    return new GuiItem(item);
  }

  protected TagResolver getGdpTagResolver() {
    TagResolver.Builder builder = TagResolver.builder();
    double gdp = EconomyDataUtil.getGdp();
    double balance = EconomyDataUtil.getBalance();
    int capita = EconomyDataUtil.getPopulation();
    double loss = EconomyDataUtil.getLoss();
    double debt = EconomyDataUtil.getDebt();
    double inflation = EconomyDataUtil.getInflation();
    builder.resolver(Placeholder.parsed("gdp", Format.currency(gdp)));
    builder.resolver(Placeholder.parsed("balance", Format.currency(balance)));
    builder.resolver(Placeholder.parsed("population", Format.number(capita)));
    builder.resolver(Placeholder.parsed("loss", Format.currency(loss)));
    builder.resolver(Placeholder.parsed("debt", Format.currency(debt)));
    builder.resolver(Placeholder.parsed("inflation", Format.percent(inflation)));
    builder.resolver(Placeholder.parsed("gdp-per-capita", Format.currency(gdp / capita)));
    builder.resolver(Placeholder.parsed("balance-per-capita", Format.currency(balance / capita)));
    builder.resolver(Placeholder.parsed("loss-per-capita", Format.currency(loss / capita)));
    builder.resolver(Placeholder.parsed("debt-per-capita", Format.currency(debt / capita)));
    return builder.build();
  }

  protected List<Component> getLore(Player player, String shopName, List<String> lore, int amount) {
    Shop shop = ShopUtil.getShop(shopName);
    boolean autosellSetting = false;
    if (Config.get().getAutosell().get(player.getUniqueId() + "." + shopName) != null) {
      autosellSetting = Config.get().getAutosell().getBoolean(
          player.getUniqueId() + "." + shopName);
    }
    List<Component> loreComponents = new ArrayList<>();
    TagResolver resolver = TagResolver.resolver(
        Placeholder.parsed("price", Format.currency(shop.getPrice())),
        Placeholder.parsed("sell-price", Format.currency(shop.getSellPrice())),
        Placeholder.parsed("total-price", Format.currency(amount * shop.getPrice())),
        Placeholder.parsed("total-sell-price", Format.currency(amount * shop.getSellPrice())),
        Placeholder.parsed("amount", Format.number(amount)),
        Placeholder.parsed("buys-left", Format.number(ShopUtil.getBuysLeft(player, shopName))),
        Placeholder.parsed("sells-left", Format.number(ShopUtil.getSellsLeft(player, shopName))),
        Placeholder.parsed("max-buys", Format.number(shop.getMaxBuys())),
        Placeholder.parsed("max-sells", Format.number(shop.getMaxSells())),
        Placeholder.parsed("change", Format.percent(shop.getChange())),
        Placeholder.parsed("collect-first-setting", shop.getSetting().getSetting().toString()),
        Placeholder.parsed("autosell-setting", autosellSetting ? "enabled" : "disabled"));

    for (String line : lore) {
      loreComponents.add(Format.getComponent(line, resolver));
    }
      
    return loreComponents;
  }

  protected abstract void doShop(HumanEntity player, ChestGui gui, String shopName);

  protected abstract List<Component> applyLore(Player player, String shopName, int amount);

}
