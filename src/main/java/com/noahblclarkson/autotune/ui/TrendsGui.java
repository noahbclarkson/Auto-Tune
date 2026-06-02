package com.noahblclarkson.autotune.ui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.ColorsConfig;
import com.noahblclarkson.autotune.config.AutoTuneConfig.MaterialsConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TrendsGui {

    private final Player player;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final ConfigManager configManager;

    private ChestGui gui;
    private PaginatedPane trendPane;

    public TrendsGui(AutoTune plugin, Player player) {
        this.player = player;
        this.shopManager = plugin.getShopManager();
        this.marketEngine = plugin.getMarketEngine();
        this.configManager = plugin.getConfigManager();
    }

    public void open() {
        gui = new ChestGui(6, configManager.getConfig().gui().titles().trends());
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        List<ShopItem> allItems = shopManager.getAllItems();

        record ItemTrend(ShopItem item, MarketEngine.PriceTrend trend) {}

        List<ItemTrend> trends = new ArrayList<>();
        for (ShopItem item : allItems) {
            MarketEngine.PriceTrend trend = marketEngine.getPriceTrend(item.id());
            trends.add(new ItemTrend(item, trend));
        }

        trends.sort(Comparator.comparing((ItemTrend t) -> t.trend().percentChange()).reversed());

        trendPane = new PaginatedPane(0, 0, 9, 5);
        List<GuiItem> guiItems = new ArrayList<>();

        for (ItemTrend it : trends) {
            guiItems.add(createTrendItem(it.item(), it.trend()));
        }

        trendPane.populateWithGuiItems(guiItems);
        gui.addPane(trendPane);
        gui.addPane(createNavigationPane());
        gui.show(player);
    }

    private GuiItem createTrendItem(ShopItem shopItem, MarketEngine.PriceTrend trend) {
        ColorsConfig colors = configManager.getConfig().gui().colors();

        ItemStack display = new ItemStack(shopItem.material());
        ItemMeta meta = display.getItemMeta();

        TextColor nameColor = switch (trend.direction()) {
            case UP -> configManager.resolveColor(colors.trendUp());
            case DOWN -> configManager.resolveColor(colors.trendDown());
            case STABLE -> configManager.resolveColor(colors.itemName());
        };

        String arrow = switch (trend.direction()) {
            case UP -> "\u25B2 ";
            case DOWN -> "\u25BC ";
            case STABLE -> "\u25CF ";
        };

        meta.displayName(Component.text(arrow + shopItem.getDisplayNameOrMaterial(), nameColor)
                .decoration(TextDecoration.ITALIC, false));

        BigDecimal buyPrice = shopManager.getBuyPrice(shopItem);
        BigDecimal sellPrice = shopManager.getSellPrice(shopItem);

        TextColor mutedColor = configManager.resolveColor(colors.muted());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Trend: ", mutedColor)
                .append(Component.text(trend.label(), nameColor))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Buy: ", mutedColor)
                .append(Component.text(configManager.formatCurrency(buyPrice), configManager.resolveColor(colors.buyPrice())))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Sell: ", mutedColor)
                .append(Component.text(configManager.formatCurrency(sellPrice), configManager.resolveColor(colors.sellPrice())))
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        display.setItemMeta(meta);

        return new GuiItem(display, e -> {});
    }

    private StaticPane createNavigationPane() {
        ColorsConfig colors = configManager.getConfig().gui().colors();
        MaterialsConfig materials = configManager.getConfig().gui().materials();

        StaticPane pane = new StaticPane(0, 5, 9, 1);

        Material border = configManager.resolveMaterial(materials.border(), Material.BLACK_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            ItemStack glass = new ItemStack(border);
            ItemMeta glassMeta = glass.getItemMeta();
            glassMeta.displayName(Component.empty());
            glass.setItemMeta(glassMeta);
            pane.addItem(new GuiItem(glass, e -> {}), x, 0);
        }

        TextColor accentColor = configManager.resolveColor(colors.accent());

        ItemStack prev = new ItemStack(configManager.resolveMaterial(materials.previousPage(), Material.ARROW));
        ItemMeta prevMeta = prev.getItemMeta();
        prevMeta.displayName(Component.text("Previous Page", accentColor)
                .decoration(TextDecoration.ITALIC, false));
        prev.setItemMeta(prevMeta);
        pane.addItem(new GuiItem(prev, e -> {
            if (trendPane.getPage() > 0) {
                trendPane.setPage(trendPane.getPage() - 1);
                gui.update();
            }
        }), 0, 0);

        ItemStack next = new ItemStack(configManager.resolveMaterial(materials.nextPage(), Material.ARROW));
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.displayName(Component.text("Next Page", accentColor)
                .decoration(TextDecoration.ITALIC, false));
        next.setItemMeta(nextMeta);
        pane.addItem(new GuiItem(next, e -> {
            if (trendPane.getPage() < trendPane.getPages() - 1) {
                trendPane.setPage(trendPane.getPage() + 1);
                gui.update();
            }
        }), 8, 0);

        ItemStack close = new ItemStack(configManager.resolveMaterial(materials.close(), Material.BARRIER));
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("Close", configManager.resolveColor(colors.negative()))
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        pane.addItem(new GuiItem(close, e -> player.closeInventory()), 4, 0);

        return pane;
    }
}
