package com.noahblclarkson.autotune.ui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.ColorsConfig;
import com.noahblclarkson.autotune.config.AutoTuneConfig.MaterialsConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.ItemRepository;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.PriceHistory;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Two-level in-game price history viewer.
 *
 * Level 1 — Item Browser: paginated list of all items with current prices and trend
 *            indicators. Click any item to drill into its history chart.
 *
 * Level 2 — Item Detail:  mini bar chart using colored stained glass panes to show
 *            price direction over time, plus stats (current/high/low/volume/spread).
 *            Timeframe selector (1H / 24H / 7D / 30D) controls how much history is shown.
 *            Back button returns to the browser.
 *
 * The chart renders as up to 9 columns (time periods) × 2 rows.
 * Each column has 1-2 colored blocks stacked from the bottom:
 *   GREEN  = price rose vs prior period
 *   RED    = price fell vs prior period
 *   YELLOW = price unchanged
 * Row height (1 or 2 blocks) encodes magnitude of the price change.
 *
 * Legend is shown at the bottom of the chart pane.
 */
public class MarketHistoryGui {

    // How many history entries to show in the chart
    private static final int CHART_COLUMNS = 9;

    /**
     * Timeframe for history queries. Each variant knows how far back to query
     * and what label to display in the GUI button.
     */
    public enum Timeframe {
        HOUR(  "1H",  60),
        DAY(   "24H", 60 * 24),
        WEEK(  "7D",  60 * 24 * 7),
        MONTH( "30D", 60 * 24 * 30);

        private final String label;
        private final int minutes;

        Timeframe(String label, int minutes) {
            this.label = label;
            this.minutes = minutes;
        }

        public String label() { return label; }

        /** Number of history rows to fetch (capped at CHART_COLUMNS) */
        public int fetchLimit() { return Math.min(minutes / 5, CHART_COLUMNS); }

        /** Instant marking the start of this timeframe window */
        public Instant windowStart() {
            return Instant.now().minusSeconds(minutes * 60L);
        }
    }

    private final AutoTune plugin;
    private final Player player;
    private final ShopManager shopManager;
    private final MarketEngine marketEngine;
    private final ConfigManager configManager;
    private final ItemRepository itemRepository;
    private ChestGui gui;  // current open GUI; used for gui.update() in nav lambdas
    private Timeframe selectedTimeframe = Timeframe.DAY;  // default timeframe

    public MarketHistoryGui(AutoTune plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.shopManager = plugin.getShopManager();
        this.marketEngine = plugin.getMarketEngine();
        this.configManager = plugin.getConfigManager();
        this.itemRepository = plugin.getDatabaseManager().getJdbi()
                .onDemand(ItemRepository.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Level 1 — Item Browser
    // ─────────────────────────────────────────────────────────────────────────

    public void openBrowser() {
        gui = new ChestGui(6,
                configManager.getConfig().gui().titles().marketHistory());
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        List<ShopItem> allItems = shopManager.getAllItems();
        allItems.sort(Comparator.comparing(ShopItem::section));

        PaginatedPane itemPane = new PaginatedPane(0, 0, 9, 5);
        List<GuiItem> guiItems = new ArrayList<>();

        for (ShopItem item : allItems) {
            guiItems.add(createBrowserItem(item));
        }

        itemPane.populateWithGuiItems(guiItems);
        gui.addPane(itemPane);
        gui.addPane(createBrowserNav(itemPane));
        gui.show(player);
    }

    private GuiItem createBrowserItem(ShopItem item) {
        ColorsConfig colors = configManager.getConfig().gui().colors();
        MarketEngine.PriceTrend trend = marketEngine.getPriceTrend(item.id());

        ItemStack display = new ItemStack(item.material());
        ItemMeta meta = display.getItemMeta();

        TextColor trendColor = switch (trend.direction()) {
            case UP -> configManager.resolveColor(colors.trendUp());
            case DOWN -> configManager.resolveColor(colors.trendDown());
            case STABLE -> configManager.resolveColor(colors.trendStable());
        };

        String arrow = switch (trend.direction()) {
            case UP -> "\u25B2 ";
            case DOWN -> "\u25BC ";
            case STABLE -> "\u25CF ";
        };

        meta.displayName(Component.text(arrow + item.getDisplayNameOrMaterial(), trendColor)
                .decoration(TextDecoration.ITALIC, false));

        BigDecimal buyPrice = shopManager.getBuyPrice(item);
        BigDecimal sellPrice = shopManager.getSellPrice(item);
        TextColor muted = configManager.resolveColor(colors.muted());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Trend: ", muted)
                .append(Component.text(trend.label(), trendColor))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Buy: ", muted)
                .append(Component.text(configManager.formatCurrency(buyPrice),
                        configManager.resolveColor(colors.buyPrice())))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Sell: ", muted)
                .append(Component.text(configManager.formatCurrency(sellPrice),
                        configManager.resolveColor(colors.sellPrice())))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("[Click for price history]", muted)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        display.setItemMeta(meta);

        return new GuiItem(display, event -> {
            event.setCancelled(true);
            openDetailView(item, selectedTimeframe);
        });
    }

    private StaticPane createBrowserNav(PaginatedPane itemPane) {
        ColorsConfig colors = configManager.getConfig().gui().colors();
        MaterialsConfig materials = configManager.getConfig().gui().materials();

        StaticPane pane = new StaticPane(0, 5, 9, 1);
        TextColor accent = configManager.resolveColor(colors.accent());

        // Border
        Material borderMat = configManager.resolveMaterial(materials.border(), Material.BLACK_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            ItemStack glass = new ItemStack(borderMat);
            ItemMeta gm = glass.getItemMeta();
            gm.displayName(Component.empty());
            glass.setItemMeta(gm);
            pane.addItem(new GuiItem(glass, e -> {}), x, 0);
        }

        // Previous
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        pm.displayName(Component.text("Previous Page", accent).decoration(TextDecoration.ITALIC, false));
        prev.setItemMeta(pm);
        pane.addItem(new GuiItem(prev, e -> {
            if (itemPane.getPage() > 0) {
                itemPane.setPage(itemPane.getPage() - 1);
                gui.update();
            }
        }), 0, 0);

        // Close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(Component.text("Close", configManager.resolveColor(colors.negative()))
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(cm);
        pane.addItem(new GuiItem(close, e -> player.closeInventory()), 4, 0);

        // Next
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nm = next.getItemMeta();
        nm.displayName(Component.text("Next Page", accent).decoration(TextDecoration.ITALIC, false));
        next.setItemMeta(nm);
        pane.addItem(new GuiItem(next, e -> {
            if (itemPane.getPage() < itemPane.getPages() - 1) {
                itemPane.setPage(itemPane.getPage() + 1);
                gui.update();
            }
        }), 8, 0);

        return pane;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Level 2 — Item Detail + Chart
    // ─────────────────────────────────────────────────────────────────────────

    public void openDetailView(ShopItem item, Timeframe timeframe) {
        selectedTimeframe = timeframe;

        gui = new ChestGui(6, item.getDisplayNameOrMaterial() + " History");
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        ColorsConfig colors = configManager.getConfig().gui().colors();
        TextColor muted = configManager.resolveColor(colors.muted());

        // ── Header: item icon + price stats (row 0-1, cols 0-8) ─────────────
        StaticPane header = new StaticPane(0, 0, 9, 2);

        // Icon at slot (0,0) — occupies row 0, col 0
        ItemStack icon = new ItemStack(item.material());
        ItemMeta iconMeta = icon.getItemMeta();
        iconMeta.displayName(Component.text(item.getDisplayNameOrMaterial(),
                configManager.resolveColor(colors.itemName())).decoration(TextDecoration.ITALIC, false));
        icon.setItemMeta(iconMeta);
        header.addItem(new GuiItem(icon, e -> {}), 0, 0);

        // Price info paper at slot (1,0) — row 0, col 1
        BigDecimal buyPrice = shopManager.getBuyPrice(item);
        BigDecimal sellPrice = shopManager.getSellPrice(item);
        MarketEngine.PriceTrend trend = marketEngine.getPriceTrend(item.id());

        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Component.text("Price Statistics", muted)
                .decoration(TextDecoration.ITALIC, false));

        TextColor trendColor = switch (trend.direction()) {
            case UP -> configManager.resolveColor(colors.trendUp());
            case DOWN -> configManager.resolveColor(colors.trendDown());
            case STABLE -> configManager.resolveColor(colors.trendStable());
        };

        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.text("Buy:  ", muted)
                .append(Component.text(configManager.formatCurrency(buyPrice),
                        configManager.resolveColor(colors.buyPrice())))
                .decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Sell: ", muted)
                .append(Component.text(configManager.formatCurrency(sellPrice),
                        configManager.resolveColor(colors.sellPrice())))
                .decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Trend: ", muted)
                .append(Component.text(trend.label(), trendColor))
                .decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        infoItem.setItemMeta(infoMeta);
        header.addItem(new GuiItem(infoItem, e -> {}), 1, 0);

        // Timeframe selector in header at cols 5-7 (3 buttons: 1H, 24H, 7D, 30D — 4 buttons need 4 cols)
        // Actually put them at cols 5-8 in row 0
        createTimeframeButtons(header, item, timeframe, colors);

        gui.addPane(header);

        // ── Chart area (rows 2-3, cols 0-8) ─────────────────────────────────
        StaticPane chartArea = new StaticPane(0, 2, 9, 2);

        List<PriceHistory> history = itemRepository.getPriceHistorySince(
                item.id(), timeframe.windowStart(), timeframe.fetchLimit());

        if (history.isEmpty()) {
            ItemStack noData = new ItemStack(Material.BARRIER);
            ItemMeta ndMeta = noData.getItemMeta();
            ndMeta.displayName(Component.text("No price history yet",
                    configManager.resolveColor(colors.muted())).decoration(TextDecoration.ITALIC, false));
            List<Component> ndLore = List.of(
                    Component.text("History is recorded every 5 minutes.",
                            configManager.resolveColor(colors.muted())).decoration(TextDecoration.ITALIC, false),
                    Component.text("Trade some items first!",
                            configManager.resolveColor(colors.muted())).decoration(TextDecoration.ITALIC, false));
            ndMeta.lore(ndLore);
            noData.setItemMeta(ndMeta);
            // Center in the chart area (x=4, y=0 = row 2, col 4)
            chartArea.addItem(new GuiItem(noData, e -> {}), 4, 0);
        } else {
            buildPriceChart(chartArea, history, colors, muted);
        }

        gui.addPane(chartArea);

        // ── Stats row (row 4) ────────────────────────────────────────────────
        gui.addPane(buildStatsRow(item, history, colors, muted));

        // ── Navigation row (row 5) ──────────────────────────────────────────
        gui.addPane(buildDetailNav(item, timeframe));

        gui.show(player);
    }

    /**
     * Creates timeframe selector buttons and adds them to the given pane.
     * Buttons are placed in row 0 starting at column 5 (cols 5, 6, 7, 8 = 4 buttons).
     */
    private void createTimeframeButtons(StaticPane pane, ShopItem item,
                                        Timeframe selected, ColorsConfig colors) {
        // Border glass for the 4 button slots (cols 5-8)
        MaterialsConfig materials = configManager.getConfig().gui().materials();
        Material borderMat = configManager.resolveMaterial(materials.border(), Material.BLACK_STAINED_GLASS_PANE);

        int slot = 5;
        for (Timeframe tf : Timeframe.values()) {
            boolean isSelected = (tf == selected);
            Material mat = isSelected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
            TextColor labelColor = isSelected
                    ? configManager.resolveColor(colors.positive())
                    : configManager.resolveColor(colors.muted());

            ItemStack btn = new ItemStack(mat);
            ItemMeta bm = btn.getItemMeta();
            bm.displayName(Component.text(tf.label(), labelColor)
                    .decoration(TextDecoration.ITALIC, false));

            if (isSelected) {
                bm.lore(List.of(
                        Component.text("Currently selected", configManager.resolveColor(colors.positive()))
                                .decoration(TextDecoration.ITALIC, false)
                ));
            } else {
                bm.lore(List.of(
                        Component.text("Click to view " + tf.label() + " history", configManager.resolveColor(colors.muted()))
                                .decoration(TextDecoration.ITALIC, false)
                ));
            }
            btn.setItemMeta(bm);

            final Timeframe chosen = tf;
            pane.addItem(new GuiItem(btn, e -> {
                e.setCancelled(true);
                if (chosen != selected) {
                    openDetailView(item, chosen);
                }
            }), slot, 0);
            slot++;
        }
    }

    /**
     * Renders the price history as colored glass pane bars.
     * Each history entry = one column (x = column index).
     * Up to 2 rows of blocks per column: bottom row (y=0) always present,
     * top row (y=1) present when magnitude exceeds average.
     *
     * Colors: GREEN = up, RED = down, YELLOW = stable.
     */
    private void buildPriceChart(StaticPane pane, List<PriceHistory> history,
                                 ColorsConfig colors, TextColor muted) {
        // Reverse so oldest is on the left (x=0)
        List<PriceHistory> reversed = new ArrayList<>(history);
        java.util.Collections.reverse(reversed);

        // Average absolute change between consecutive periods (for magnitude)
        BigDecimal avgChange = BigDecimal.ZERO;
        for (int i = 1; i < reversed.size(); i++) {
            avgChange = avgChange.add(
                    reversed.get(i).price().subtract(reversed.get(i - 1).price()).abs());
        }
        if (reversed.size() > 1) {
            avgChange = avgChange.divide(BigDecimal.valueOf(reversed.size() - 1),
                    2, RoundingMode.HALF_UP);
        }
        if (avgChange.compareTo(BigDecimal.ZERO) == 0) avgChange = BigDecimal.ONE;

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm")
                .withZone(ZoneId.systemDefault());
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d")
                .withZone(ZoneId.systemDefault());

        int columns = Math.min(reversed.size(), 9);

        for (int col = 0; col < columns; col++) {
            PriceHistory ph = reversed.get(col);
            BigDecimal price = ph.price();

            // Direction vs previous period
            boolean isUp = false, isDown = false;
            if (col > 0) {
                int cmp = price.compareTo(reversed.get(col - 1).price());
                if (cmp > 0) isUp = true;
                else if (cmp < 0) isDown = true;
            }

            Material mat = isUp ? Material.GREEN_STAINED_GLASS_PANE
                    : isDown ? Material.RED_STAINED_GLASS_PANE
                    : Material.YELLOW_STAINED_GLASS_PANE;

            // Magnitude: 1 or 2 rows based on how big the change is
            BigDecimal deviation = col > 0
                    ? price.subtract(reversed.get(col - 1).price()).abs()
                    : BigDecimal.ZERO;
            int rows = deviation.divide(avgChange, RoundingMode.FLOOR).intValue() + 1;
            rows = Math.min(2, Math.max(1, rows));

            // Bottom block (always present)
            ItemStack bottomBlock = makeChartBlock(mat, ph, colors, muted, timeFmt, dateFmt);
            pane.addItem(new GuiItem(bottomBlock, e -> {}), col, 0);

            // Top block (only if magnitude warrants)
            if (rows > 1) {
                ItemStack topBlock = makeChartBlock(mat, ph, colors, muted, timeFmt, dateFmt);
                pane.addItem(new GuiItem(topBlock, e -> {}), col, 1);
            }
        }

        // Legend at the bottom of the chart area (row 2)
        pane.addItem(new GuiItem(makeLegendItem(Material.GREEN_STAINED_GLASS_PANE,
                "Rising", colors), e -> {}), 0, 2);
        pane.addItem(new GuiItem(makeLegendItem(Material.RED_STAINED_GLASS_PANE,
                "Falling", colors), e -> {}), 1, 2);
        pane.addItem(new GuiItem(makeLegendItem(Material.YELLOW_STAINED_GLASS_PANE,
                "Stable", colors), e -> {}), 2, 2);
    }

    private ItemStack makeChartBlock(Material mat, PriceHistory ph,
                                     ColorsConfig colors, TextColor muted,
                                     DateTimeFormatter timeFmt, DateTimeFormatter dateFmt) {
        ItemStack block = new ItemStack(mat);
        ItemMeta bm = block.getItemMeta();

        String timeStr = dateFmt.format(ph.timestamp()) + " " + timeFmt.format(ph.timestamp());
        String priceStr = configManager.formatCurrency(ph.price());
        String volStr = ph.totalVolume() + " vol";
        String bpdStr = "BPD " + ph.bpd().setScale(2, RoundingMode.HALF_UP) + "%";

        TextColor labelColor = switch (mat) {
            case GREEN_STAINED_GLASS_PANE -> configManager.resolveColor(colors.trendUp());
            case RED_STAINED_GLASS_PANE -> configManager.resolveColor(colors.trendDown());
            default -> configManager.resolveColor(colors.trendStable());
        };

        bm.displayName(Component.text(priceStr, labelColor).decoration(TextDecoration.ITALIC, false));
        bm.lore(List.of(
                Component.text(timeStr, muted).decoration(TextDecoration.ITALIC, false),
                Component.text(volStr + " | " + bpdStr, muted).decoration(TextDecoration.ITALIC, false)
        ));
        block.setItemMeta(bm);
        return block;
    }

    private ItemStack makeLegendItem(Material mat, String label, ColorsConfig colors) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label,
                configManager.resolveColor(colors.muted())).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private StaticPane buildStatsRow(ShopItem item, List<PriceHistory> history,
                                     ColorsConfig colors, TextColor muted) {
        StaticPane pane = new StaticPane(0, 4, 9, 1);

        BigDecimal high = BigDecimal.ZERO;
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal avg = BigDecimal.ZERO;
        int totalVol = 0;

        if (!history.isEmpty()) {
            high = history.stream().map(PriceHistory::price).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            low = history.stream().map(PriceHistory::price).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            totalVol = history.stream().mapToInt(PriceHistory::totalVolume).sum();
            BigDecimal sum = history.stream().map(PriceHistory::price).reduce(BigDecimal.ZERO, BigDecimal::add);
            avg = sum.divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);
        }

        // High — slot 0
        ItemStack highItem = new ItemStack(Material.EMERALD);
        ItemMeta hm = highItem.getItemMeta();
        hm.displayName(Component.text("High", configManager.resolveColor(colors.trendUp()))
                .decoration(TextDecoration.ITALIC, false));
        hm.lore(List.of(Component.text(configManager.formatCurrency(high), muted)
                .decoration(TextDecoration.ITALIC, false)));
        highItem.setItemMeta(hm);
        pane.addItem(new GuiItem(highItem, e -> {}), 0, 0);

        // Low — slot 1
        ItemStack lowItem = new ItemStack(Material.REDSTONE);
        ItemMeta lm = lowItem.getItemMeta();
        lm.displayName(Component.text("Low", configManager.resolveColor(colors.trendDown()))
                .decoration(TextDecoration.ITALIC, false));
        lm.lore(List.of(Component.text(configManager.formatCurrency(low), muted)
                .decoration(TextDecoration.ITALIC, false)));
        lowItem.setItemMeta(lm);
        pane.addItem(new GuiItem(lowItem, e -> {}), 1, 0);

        // Volume — slot 2
        ItemStack volItem = new ItemStack(Material.HOPPER);
        ItemMeta vm = volItem.getItemMeta();
        vm.displayName(Component.text("Volume", configManager.resolveColor(colors.accent()))
                .decoration(TextDecoration.ITALIC, false));
        vm.lore(List.of(Component.text(totalVol + " transactions", muted)
                .decoration(TextDecoration.ITALIC, false)));
        volItem.setItemMeta(vm);
        pane.addItem(new GuiItem(volItem, e -> {}), 2, 0);

        // Avg — slot 3
        ItemStack avgItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta am = avgItem.getItemMeta();
        am.displayName(Component.text("Average", configManager.resolveColor(colors.spread()))
                .decoration(TextDecoration.ITALIC, false));
        am.lore(List.of(Component.text(configManager.formatCurrency(avg), muted)
                .decoration(TextDecoration.ITALIC, false)));
        avgItem.setItemMeta(am);
        pane.addItem(new GuiItem(avgItem, e -> {}), 3, 0);

        // Spread from most recent — slot 4
        if (!history.isEmpty()) {
            PriceHistory latest = history.get(0);
            ItemStack spreadItem = new ItemStack(Material.BLAZE_POWDER);
            ItemMeta sm = spreadItem.getItemMeta();
            sm.displayName(Component.text("Spread", configManager.resolveColor(colors.spread()))
                    .decoration(TextDecoration.ITALIC, false));
            sm.lore(List.of(
                    Component.text("BPD: " + latest.bpd().setScale(2, RoundingMode.HALF_UP) + "%",
                            configManager.resolveColor(colors.buyPrice())).decoration(TextDecoration.ITALIC, false),
                    Component.text("SPD: " + latest.spd().setScale(2, RoundingMode.HALF_UP) + "%",
                            configManager.resolveColor(colors.sellPrice())).decoration(TextDecoration.ITALIC, false)
            ));
            spreadItem.setItemMeta(sm);
            pane.addItem(new GuiItem(spreadItem, e -> {}), 4, 0);
        }

        return pane;
    }

    private StaticPane buildDetailNav(ShopItem item, Timeframe timeframe) {
        ColorsConfig colors = configManager.getConfig().gui().colors();
        MaterialsConfig materials = configManager.getConfig().gui().materials();

        StaticPane pane = new StaticPane(0, 5, 9, 1);

        // Border
        Material borderMat = configManager.resolveMaterial(materials.border(), Material.BLACK_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            ItemStack glass = new ItemStack(borderMat);
            ItemMeta gm = glass.getItemMeta();
            gm.displayName(Component.empty());
            glass.setItemMeta(gm);
            pane.addItem(new GuiItem(glass, e -> {}), x, 0);
        }

        // Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.displayName(Component.text("Back to Browser",
                configManager.resolveColor(colors.accent())).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(bm);
        pane.addItem(new GuiItem(back, e -> openBrowser()), 0, 0);

        // Refresh — reopens with current timeframe
        ItemStack refresh = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta rm = refresh.getItemMeta();
        rm.displayName(Component.text("Refresh",
                configManager.resolveColor(colors.accent())).decoration(TextDecoration.ITALIC, false));
        refresh.setItemMeta(rm);
        pane.addItem(new GuiItem(refresh, e -> openDetailView(item, timeframe)), 4, 0);

        // Close
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(Component.text("Close", configManager.resolveColor(colors.negative()))
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(cm);
        pane.addItem(new GuiItem(close, e -> player.closeInventory()), 8, 0);

        return pane;
    }
}
