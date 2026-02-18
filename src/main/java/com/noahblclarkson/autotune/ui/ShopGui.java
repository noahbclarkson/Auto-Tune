package com.noahblclarkson.autotune.ui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig.ColorsConfig;
import com.noahblclarkson.autotune.config.AutoTuneConfig.GuiConfig;
import com.noahblclarkson.autotune.config.AutoTuneConfig.MaterialsConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.database.DatabaseManager;
import com.noahblclarkson.autotune.database.EconomySnapshotRepository;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.manager.EconomyMetricsManager;
import com.noahblclarkson.autotune.manager.MarketEngine;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.EconomySnapshot;
import com.noahblclarkson.autotune.model.Section;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.util.ItemSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

public class ShopGui {

    private final AutoTune plugin;
    private final Player player;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final MarketEngine marketEngine;

    private ChestGui gui;
    private PaginatedPane itemsPane;
    private List<ShopItem> currentItems = List.of();
    private String currentTitle;
    private int currentPage;

    public ShopGui(AutoTune plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.shopManager = plugin.getShopManager();
        this.economyManager = plugin.getEconomyManager();
        this.configManager = plugin.getConfigManager();
        this.databaseManager = plugin.getDatabaseManager();
        this.marketEngine = plugin.getMarketEngine();
        this.currentTitle = configManager.getConfig().gui().titles().shop();
    }

    public void open() {
        openSections();
    }

    private void openSections() {
        GuiConfig guiConfig = configManager.getConfig().gui();
        ColorsConfig colors = guiConfig.colors();
        MaterialsConfig materials = guiConfig.materials();

        currentItems = List.of();
        currentTitle = guiConfig.titles().shop();

        gui = new ChestGui(6, currentTitle);
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        StaticPane sectionsPane = new StaticPane(0, 0, 9, 5);
        List<Section> sections = loadSections();

        Material borderMaterial = configManager.resolveMaterial(materials.border(), Material.BLACK_STAINED_GLASS_PANE);
        fillBorders(sectionsPane, borderMaterial, 9, 5);

        TextColor sectionColor = configManager.resolveColor(colors.sectionName());
        TextColor mutedColor = configManager.resolveColor(colors.muted());

        int x = 1;
        int y = 1;
        for (Section section : sections) {
            if (x > 7) {
                x = 1;
                y++;
                if (y > 3) break;
            }

            ItemStack icon = new ItemStack(section.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(section.displayName(), sectionColor)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to browse", mutedColor)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);

            sectionsPane.addItem(new GuiItem(icon, event -> openSection(section.id())), x, y);
            x++;
        }

        if (guiConfig.showEconomyStats()) {
            EconomySnapshotRepository snapshotRepo = plugin.getInjector().getInstance(EconomySnapshotRepository.class);
            Optional<EconomySnapshot> latestSnapshot = snapshotRepo.findLatest();
            if (latestSnapshot.isPresent()) {
                EconomySnapshot snapshot = latestSnapshot.get();
                Material econMaterial = configManager.resolveMaterial(materials.economyStats(), Material.GOLD_BLOCK);
                ItemStack econBlock = new ItemStack(econMaterial);
                ItemMeta econMeta = econBlock.getItemMeta();
                econMeta.displayName(Component.text("Economy Stats", sectionColor)
                        .decoration(TextDecoration.ITALIC, false));
                List<Component> econLore = new ArrayList<>();
                econLore.add(Component.empty());
                econLore.add(Component.text("GDP (24h): ", mutedColor)
                        .append(Component.text(configManager.formatCurrency(snapshot.gdp()), configManager.resolveColor(colors.positive())))
                        .decoration(TextDecoration.ITALIC, false));
                String inflationLabel = plugin.getInjector().getInstance(EconomyMetricsManager.class).getInflationLabel();
                econLore.add(Component.text("Inflation: ", mutedColor)
                        .append(Component.text(snapshot.averagePriceChange().setScale(2, RoundingMode.HALF_UP) + "% (" + inflationLabel + ")", configManager.resolveColor(colors.sellPrice())))
                        .decoration(TextDecoration.ITALIC, false));
                econLore.add(Component.text("Total Debt: ", mutedColor)
                        .append(Component.text(configManager.formatCurrency(snapshot.totalDebt()), configManager.resolveColor(colors.negative())))
                        .decoration(TextDecoration.ITALIC, false));
                econLore.add(Component.text("Active Loans: ", mutedColor)
                        .append(Component.text(String.valueOf(snapshot.activeLoans()), configManager.resolveColor(colors.itemName())))
                        .decoration(TextDecoration.ITALIC, false));
                econLore.add(Component.text("Players: ", mutedColor)
                        .append(Component.text(String.valueOf(snapshot.playerCount()), configManager.resolveColor(colors.itemName())))
                        .decoration(TextDecoration.ITALIC, false));
                econMeta.lore(econLore);
                econBlock.setItemMeta(econMeta);
                sectionsPane.addItem(new GuiItem(econBlock, e -> {}), 4, 0);
            }
        }

        gui.addPane(sectionsPane);

        StaticPane navPane = new StaticPane(0, 5, 9, 1);
        for (int bx = 0; bx < 9; bx++) {
            navPane.addItem(createBorderItem(borderMaterial), bx, 0);
        }

        TextColor accentColor = configManager.resolveColor(colors.accent());
        navPane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.trends(), Material.SPYGLASS),
                "Market Trends", sectionColor,
                event -> new TrendsGui(plugin, player).open()), 3, 0);
        if (guiConfig.searchEnabled()) {
            navPane.addItem(createNavigationItem(
                    configManager.resolveMaterial(materials.search(), Material.NAME_TAG),
                    "Search", accentColor,
                    event -> openSearchPrompt()), 5, 0);
        }
        gui.addPane(navPane);

        gui.show(player);
    }

    private void openSection(String sectionId) {
        List<ShopItem> items = "all".equalsIgnoreCase(sectionId)
                ? shopManager.getAllItems()
                : shopManager.getItemsBySection(sectionId);

        currentItems = items;
        currentTitle = sectionTitle(sectionId);
        currentPage = 0;

        renderItemsView(true);
    }

    public void openSearchResults(String query, List<ShopItem> results) {
        currentItems = results;
        currentTitle = "Search: " + query;
        currentPage = 0;
        renderItemsView(true);
    }

    private void renderItemsView(boolean showBack) {
        gui = new ChestGui(6, currentTitle);
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        int rows = Math.max(1, Math.min(5, configManager.getConfig().gui().itemsPerPage() / 9));
        itemsPane = new PaginatedPane(0, 0, 9, rows);
        populateItems(currentItems);
        if (itemsPane.getPages() > 0) {
            currentPage = Math.min(currentPage, itemsPane.getPages() - 1);
            itemsPane.setPage(currentPage);
        } else {
            currentPage = 0;
        }
        gui.addPane(itemsPane);

        StaticPane navigationPane = createItemsNavigationPane(showBack);
        gui.addPane(navigationPane);

        gui.show(player);
    }

    private void populateItems(List<ShopItem> items) {
        List<GuiItem> guiItems = new ArrayList<>();
        for (ShopItem item : items) {
            guiItems.add(createShopItemGui(item));
        }
        itemsPane.populateWithGuiItems(guiItems);
    }

    private GuiItem createShopItemGui(ShopItem shopItem) {
        ColorsConfig colors = configManager.getConfig().gui().colors();

        ItemStack display;
        if (shopItem.itemData() != null) {
            ItemStack deserialized = ItemSerializer.tryDeserializeItemStack(shopItem.itemData());
            display = deserialized != null ? deserialized.clone() : new ItemStack(shopItem.material());
            display.setAmount(1);
        } else {
            display = new ItemStack(shopItem.material());
        }

        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text(shopItem.getDisplayNameOrMaterial())
                .color(configManager.resolveColor(colors.itemName()))
                .decoration(TextDecoration.ITALIC, false));

        BigDecimal buyPrice = shopManager.getBuyPrice(shopItem);
        BigDecimal sellPrice = shopManager.getSellPrice(shopItem);
        MarketEngine.SpreadResult spread = marketEngine.getSpread(shopItem.id());
        MarketEngine.PriceTrend trend = marketEngine.getPriceTrend(shopItem.id());
        boolean buyable = shopManager.isBuyable(shopItem);

        TextColor mutedColor = configManager.resolveColor(colors.muted());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (buyable) {
            lore.add(Component.text("Buy: ", mutedColor)
                    .append(Component.text(configManager.formatCurrency(buyPrice), configManager.resolveColor(colors.buyPrice())))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Buy: ", mutedColor)
                    .append(Component.text("Not yet available", configManager.resolveColor(colors.negative())))
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Sell: ", mutedColor)
                .append(Component.text(configManager.formatCurrency(sellPrice), configManager.resolveColor(colors.sellPrice())))
                .decoration(TextDecoration.ITALIC, false));

        BigDecimal spreadPercent = spread.bpd().add(spread.spd()).multiply(BigDecimal.valueOf(100));
        lore.add(Component.text("Spread: ", mutedColor)
                .append(Component.text(spreadPercent.setScale(1, RoundingMode.HALF_UP) + "%", configManager.resolveColor(colors.spread())))
                .decoration(TextDecoration.ITALIC, false));

        TextColor trendColor = switch (trend.direction()) {
            case UP -> configManager.resolveColor(colors.trendUp());
            case DOWN -> configManager.resolveColor(colors.trendDown());
            case STABLE -> configManager.resolveColor(colors.trendStable());
        };
        String trendArrow = switch (trend.direction()) {
            case UP -> "\u25B2 ";
            case DOWN -> "\u25BC ";
            case STABLE -> "\u25CF ";
        };
        lore.add(Component.text("Trend: ", mutedColor)
                .append(Component.text(trendArrow + trend.label(), trendColor))
                .decoration(TextDecoration.ITALIC, false));

        if (configManager.getConfig().gui().show24hChange()) {
            BigDecimal change24h = marketEngine.get24hChange(shopItem.id());
            if (change24h.compareTo(BigDecimal.ZERO) != 0) {
                TextColor changeColor = change24h.compareTo(BigDecimal.ZERO) > 0
                        ? configManager.resolveColor(colors.positive())
                        : configManager.resolveColor(colors.negative());
                String changePrefix = change24h.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
                lore.add(Component.text("24h: ", mutedColor)
                        .append(Component.text(changePrefix + change24h + "%", changeColor))
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("Click to buy/sell", mutedColor)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        display.setItemMeta(meta);

        return new GuiItem(display, event -> openBuySellGui(shopItem));
    }

    private void openBuySellGui(ShopItem shopItem) {
        GuiConfig guiConfig = configManager.getConfig().gui();
        ColorsConfig colors = guiConfig.colors();
        MaterialsConfig materials = guiConfig.materials();

        BigDecimal buyPrice = shopManager.getBuyPrice(shopItem);
        BigDecimal sellPrice = shopManager.getSellPrice(shopItem);
        boolean buyable = shopManager.isBuyable(shopItem);
        int maxStack = new ItemStack(shopItem.material()).getMaxStackSize();

        ChestGui buySellGui = new ChestGui(4, shopItem.getDisplayNameOrMaterial());
        buySellGui.setOnGlobalClick(event -> event.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 4);
        Material border = configManager.resolveMaterial(materials.border(), Material.BLACK_STAINED_GLASS_PANE);

        for (int bx = 0; bx < 9; bx++) {
            pane.addItem(createBorderItem(border), bx, 0);
            pane.addItem(createBorderItem(border), bx, 3);
        }
        pane.addItem(createBorderItem(border), 0, 1);
        pane.addItem(createBorderItem(border), 0, 2);

        TextColor itemNameColor = configManager.resolveColor(colors.itemName());
        TextColor mutedColor = configManager.resolveColor(colors.muted());

        ItemStack infoItem;
        if (shopItem.itemData() != null) {
            ItemStack deserialized = ItemSerializer.tryDeserializeItemStack(shopItem.itemData());
            infoItem = deserialized != null ? deserialized.clone() : new ItemStack(shopItem.material());
            infoItem.setAmount(1);
        } else {
            infoItem = new ItemStack(shopItem.material());
        }
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(Component.text(shopItem.getDisplayNameOrMaterial(), itemNameColor)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.empty());
        if (buyable) {
            infoLore.add(Component.text("Buy: ", mutedColor)
                    .append(Component.text(configManager.formatCurrency(buyPrice) + " each", configManager.resolveColor(colors.buyPrice())))
                    .decoration(TextDecoration.ITALIC, false));
        }
        infoLore.add(Component.text("Sell: ", mutedColor)
                .append(Component.text(configManager.formatCurrency(sellPrice) + " each", configManager.resolveColor(colors.sellPrice())))
                .decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.empty());
        infoLore.add(Component.text("You have: " + economyManager.countItems(player, shopItem), mutedColor)
                .decoration(TextDecoration.ITALIC, false));
        infoLore.add(Component.text("Balance: " + configManager.formatCurrency(economyManager.getBalance(player)), mutedColor)
                .decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(infoLore);
        infoItem.setItemMeta(infoMeta);
        pane.addItem(new GuiItem(infoItem, e -> {}), 0, 0);

        List<Integer> validQuantities = new ArrayList<>();
        for (int q : guiConfig.buyQuantities()) {
            if (q <= maxStack) {
                validQuantities.add(q);
            }
        }

        TextColor buyColor = configManager.resolveColor(colors.buyPrice());
        TextColor sellColor = configManager.resolveColor(colors.sellPrice());
        Material buyButtonMaterial = configManager.resolveMaterial(materials.buyButton(), Material.LIME_STAINED_GLASS_PANE);
        Material sellButtonMaterial = configManager.resolveMaterial(materials.sellButton(), Material.ORANGE_STAINED_GLASS_PANE);

        if (buyable) {
            int col = 1;
            for (int qty : validQuantities) {
                if (col > 8) break;
                BigDecimal qtyBuyPrice = shopManager.getBuyPrice(shopItem, qty);
                BigDecimal totalCost = qtyBuyPrice.multiply(BigDecimal.valueOf(qty));
                ItemStack buyItem = new ItemStack(buyButtonMaterial, Math.min(qty, 64));
                ItemMeta buyMeta = buyItem.getItemMeta();
                buyMeta.displayName(Component.text("Buy " + qty, buyColor)
                        .decoration(TextDecoration.ITALIC, false));
                buyMeta.lore(List.of(
                        Component.text("Cost: " + configManager.formatCurrency(totalCost), mutedColor)
                                .decoration(TextDecoration.ITALIC, false)
                ));
                buyItem.setItemMeta(buyMeta);
                final int amount = qty;
                pane.addItem(new GuiItem(buyItem, event -> executeBuy(shopItem, amount)), col, 1);
                col++;
            }
        } else {
            Material notAvailableMaterial = configManager.resolveMaterial(materials.notAvailable(), Material.BARRIER);
            ItemStack noBuy = new ItemStack(notAvailableMaterial);
            ItemMeta noBuyMeta = noBuy.getItemMeta();
            noBuyMeta.displayName(Component.text("Not available for purchase", configManager.resolveColor(colors.negative()))
                    .decoration(TextDecoration.ITALIC, false));
            noBuyMeta.lore(List.of(
                    Component.text("This item must be sold first", mutedColor)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            noBuy.setItemMeta(noBuyMeta);
            pane.addItem(new GuiItem(noBuy, e -> {}), 4, 1);
        }

        int col = 1;
        for (int qty : validQuantities) {
            if (col > 8) break;
            BigDecimal qtySellPrice = shopManager.getSellPrice(shopItem, qty);
            BigDecimal totalValue = qtySellPrice.multiply(BigDecimal.valueOf(qty));
            ItemStack sellItem = new ItemStack(sellButtonMaterial, Math.min(qty, 64));
            ItemMeta sellMeta = sellItem.getItemMeta();
            sellMeta.displayName(Component.text("Sell " + qty, sellColor)
                    .decoration(TextDecoration.ITALIC, false));
            sellMeta.lore(List.of(
                    Component.text("Value: " + configManager.formatCurrency(totalValue), mutedColor)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            sellItem.setItemMeta(sellMeta);
            final int amount = qty;
            pane.addItem(new GuiItem(sellItem, event -> executeSell(shopItem, amount)), col, 2);
            col++;
        }

        TextColor accentColor = configManager.resolveColor(colors.accent());
        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.back(), Material.DARK_OAK_DOOR),
                "Back", accentColor,
                event -> renderItemsView(true)), 8, 3);

        buySellGui.addPane(pane);
        buySellGui.show(player);
    }

    private void executeBuy(ShopItem shopItem, int amount) {
        economyManager.processBuyAsync(player, shopItem, amount).thenAccept(result ->
                databaseManager.runOnMain(() -> {
                    if (result.success()) {
                        player.sendMessage(configManager.getMessage("shop.purchase-success", Map.of(
                                "amount", String.valueOf(result.amount()),
                                "item", shopItem.getDisplayNameOrMaterial(),
                                "price", configManager.formatCurrency(result.totalPrice())
                        )));
                        openBuySellGui(shopItem);
                    } else {
                        player.sendMessage(Component.text(result.errorMessage(),
                                configManager.resolveColor(configManager.getConfig().gui().colors().negative())));
                    }
                })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to process buy transaction", ex);
            return null;
        });
    }

    private void executeSell(ShopItem shopItem, int amount) {
        economyManager.processSellAsync(player, shopItem, amount).thenAccept(result ->
                databaseManager.runOnMain(() -> {
                    if (result.success()) {
                        player.sendMessage(configManager.getMessage("shop.sale-success", Map.of(
                                "amount", String.valueOf(result.amount()),
                                "item", shopItem.getDisplayNameOrMaterial(),
                                "price", configManager.formatCurrency(result.totalPrice())
                        )));
                        openBuySellGui(shopItem);
                    } else {
                        player.sendMessage(Component.text(result.errorMessage(),
                                configManager.resolveColor(configManager.getConfig().gui().colors().negative())));
                    }
                })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to process sell transaction", ex);
            return null;
        });
    }

    private StaticPane createItemsNavigationPane(boolean showBack) {
        GuiConfig guiConfig = configManager.getConfig().gui();
        ColorsConfig colors = guiConfig.colors();
        MaterialsConfig materials = guiConfig.materials();

        StaticPane pane = new StaticPane(0, 5, 9, 1);

        Material borderMaterial = configManager.resolveMaterial(materials.border(), Material.BLACK_STAINED_GLASS_PANE);
        for (int x = 0; x < 9; x++) {
            pane.addItem(createBorderItem(borderMaterial), x, 0);
        }

        TextColor accentColor = configManager.resolveColor(colors.accent());

        if (currentPage > 0) {
            pane.addItem(createNavigationItem(
                    configManager.resolveMaterial(materials.previousPage(), Material.ARROW),
                    "Previous Page", accentColor,
                    event -> {
                        currentPage--;
                        renderItemsView(showBack);
                    }), 0, 0);
        }

        if (showBack) {
            pane.addItem(createNavigationItem(
                    configManager.resolveMaterial(materials.back(), Material.DARK_OAK_DOOR),
                    "Back", accentColor,
                    event -> openSections()), 1, 0);
        }

        Material pageIndicatorMaterial = configManager.resolveMaterial(materials.pageIndicator(), Material.PAPER);
        ItemStack pageItem = new ItemStack(pageIndicatorMaterial);
        ItemMeta pageMeta = pageItem.getItemMeta();
        int displayPage = currentPage + 1;
        int totalPages = Math.max(1, itemsPane.getPages());
        pageMeta.displayName(Component.text("Page " + displayPage + "/" + totalPages, configManager.resolveColor(colors.itemName()))
                .decoration(TextDecoration.ITALIC, false));
        pageItem.setItemMeta(pageMeta);
        pane.addItem(new GuiItem(pageItem, e -> {}), 4, 0);

        if (guiConfig.searchEnabled()) {
            pane.addItem(createNavigationItem(
                    configManager.resolveMaterial(materials.search(), Material.NAME_TAG),
                    "Search", accentColor,
                    event -> openSearchPrompt()), 7, 0);
        }

        if (itemsPane.getPages() > 0 && currentPage < itemsPane.getPages() - 1) {
            pane.addItem(createNavigationItem(
                    configManager.resolveMaterial(materials.nextPage(), Material.ARROW),
                    "Next Page", accentColor,
                    event -> {
                        currentPage++;
                        renderItemsView(showBack);
                    }), 8, 0);
        }

        return pane;
    }

    private GuiItem createNavigationItem(Material material, String name, TextColor color,
                                         java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return new GuiItem(item, action);
    }

    private GuiItem createBorderItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return new GuiItem(item, e -> {});
    }

    private void fillBorders(StaticPane pane, Material borderMaterial, int width, int height) {
        for (int x = 0; x < width; x++) {
            pane.addItem(createBorderItem(borderMaterial), x, 0);
            pane.addItem(createBorderItem(borderMaterial), x, height - 1);
        }
        for (int y = 1; y < height - 1; y++) {
            pane.addItem(createBorderItem(borderMaterial), 0, y);
            pane.addItem(createBorderItem(borderMaterial), width - 1, y);
        }
    }

    private void openSearchPrompt() {
        player.closeInventory();
        player.sendMessage(configManager.getMessage("shop.search-prompt"));

        new ChatSearchHandler(plugin, player, configManager.getConfig().gui().searchTimeoutTicks(),
                query -> {
                    if (query.isEmpty()) {
                        player.sendMessage(configManager.getMessage("shop.search-cancelled"));
                        return;
                    }
                    shopManager.searchAsync(query).thenAccept(results ->
                            databaseManager.runOnMain(() -> {
                                if (results == null) {
                                    player.sendMessage(configManager.getMessage("error.database"));
                                    return;
                                }
                                if (results.isEmpty()) {
                                    player.sendMessage(configManager.getMessage("shop.search-no-results",
                                            Map.of("query", query)));
                                }
                                openSearchResults(query, results);
                            })).exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "Failed to search shop", ex);
                        return null;
                    });
                },
                () -> player.sendMessage(configManager.getMessage("shop.search-cancelled"))
        );
    }

    private String sectionTitle(String sectionId) {
        if (sectionId == null) {
            return configManager.getConfig().gui().titles().shop();
        }

        for (Section section : loadSections()) {
            if (section.id().equalsIgnoreCase(sectionId)) {
                return section.displayName();
            }
        }

        return configManager.getConfig().gui().titles().shop();
    }

    private List<Section> loadSections() {
        ConfigurationSection sectionConfig = plugin.getConfig().getConfigurationSection("sections");
        List<Section> sections = new ArrayList<>();

        if (sectionConfig != null) {
            for (String key : sectionConfig.getKeys(false)) {
                ConfigurationSection entry = sectionConfig.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }

                String displayName = entry.getString("display-name", key);
                String iconName = entry.getString("icon", "CHEST");
                Material icon = Material.matchMaterial(iconName);
                if (icon == null) {
                    icon = Material.CHEST;
                }
                int priority = entry.getInt("priority", 0);

                sections.add(Section.builder()
                        .id(key)
                        .displayName(displayName)
                        .icon(icon)
                        .priority(priority)
                        .build());
            }
        }

        if (sections.isEmpty()) {
            sections.add(Section.builder().id("all").displayName("All Items").icon(Material.CHEST).priority(0).build());
        }

        sections.sort(Comparator.comparingInt(Section::priority));
        return sections;
    }
}
