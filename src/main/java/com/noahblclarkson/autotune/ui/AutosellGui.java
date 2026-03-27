package com.noahblclarkson.autotune.ui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.ui.AnvilMinPriceGui;
import org.bukkit.event.inventory.InventoryClickEvent;
import com.noahblclarkson.autotune.config.AutoTuneConfig.ColorsConfig;
import com.noahblclarkson.autotune.config.AutoTuneConfig.MaterialsConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.Section;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class AutosellGui {

    private enum ViewMode {
        SECTIONS,
        ITEMS
    }

    private final AutoTune plugin;
    private final Player player;
    private final ShopManager shopManager;
    private final AutosellManager autosellManager;
    private final ConfigManager configManager;

    private ChestGui gui;
    private PaginatedPane itemsPane;
    private ViewMode viewMode = ViewMode.SECTIONS;
    private List<ShopItem> currentItems = List.of();
    private String currentTitle;

    public AutosellGui(AutoTune plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.shopManager = plugin.getShopManager();
        this.autosellManager = plugin.getAutosellManager();
        this.configManager = plugin.getConfigManager();
        this.currentTitle = configManager.getConfig().gui().titles().autosell();
    }

    public void open() {
        openSections();
    }

    private void openSections() {
        ColorsConfig colors = configManager.getConfig().gui().colors();

        viewMode = ViewMode.SECTIONS;
        currentItems = List.of();
        currentTitle = configManager.getConfig().gui().titles().autosell();

        gui = new ChestGui(6, currentTitle);
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        StaticPane sectionsPane = new StaticPane(0, 0, 9, 5);
        List<Section> sections = loadSections();

        TextColor sectionColor = configManager.resolveColor(colors.sectionName());
        TextColor mutedColor = configManager.resolveColor(colors.muted());

        int x = 0;
        int y = 0;
        for (Section section : sections) {
            ItemStack icon = new ItemStack(section.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(section.displayName(), sectionColor)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to configure autosell", mutedColor)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            icon.setItemMeta(meta);

            sectionsPane.addItem(new GuiItem(icon, event -> openSection(section.id())), x, y);

            x++;
            if (x > 8) {
                x = 0;
                y++;
            }
        }

        gui.addPane(sectionsPane);
        gui.addPane(createSectionsNavigationPane());
        gui.show(player);
    }

    private void openSection(String sectionId) {
        List<ShopItem> items = "all".equalsIgnoreCase(sectionId)
                ? shopManager.getAllItems()
                : shopManager.getItemsBySection(sectionId);

        viewMode = ViewMode.ITEMS;
        currentItems = items;
        currentTitle = sectionTitle(sectionId) + " - Autosell";

        renderItemsView();
    }

    private void renderItemsView() {
        gui = new ChestGui(6, currentTitle);
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        itemsPane = new PaginatedPane(0, 0, 9, 5);
        populateItems(currentItems);
        gui.addPane(itemsPane);

        StaticPane navigationPane = createItemsNavigationPane();
        gui.addPane(navigationPane);

        gui.show(player);
    }

    private void populateItems(List<ShopItem> items) {
        List<GuiItem> guiItems = new ArrayList<>();
        Set<Integer> enabledItems = autosellManager.getEnabledItems(player.getUniqueId());

        for (ShopItem item : items) {
            java.math.BigDecimal perItemMin = autosellManager.getMinPrice(player.getUniqueId(), item.id())
                    .orElse(null);
            guiItems.add(createAutosellItemGui(item, enabledItems.contains(item.id()), perItemMin));
        }

        itemsPane.populateWithGuiItems(guiItems);
    }

    private GuiItem createAutosellItemGui(ShopItem shopItem, boolean enabled, java.math.BigDecimal perItemMinPrice) {
        ColorsConfig colors = configManager.getConfig().gui().colors();

        ItemStack display = new ItemStack(shopItem.material());
        ItemMeta meta = display.getItemMeta();

        TextColor nameColor = enabled
                ? configManager.resolveColor(colors.positive())
                : configManager.resolveColor(colors.itemName());
        Component displayName = Component.text(shopItem.getDisplayNameOrMaterial())
                .color(nameColor)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);

        BigDecimal sellPrice = shopManager.getSellPrice(shopItem);

        TextColor mutedColor = configManager.resolveColor(colors.muted());
        TextColor positiveColor = configManager.resolveColor(colors.positive());
        TextColor negativeColor = configManager.resolveColor(colors.negative());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Sell Price: ", mutedColor)
                .append(Component.text(configManager.formatCurrency(sellPrice), configManager.resolveColor(colors.sellPrice())))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (enabled) {
            lore.add(Component.text("Status: ", mutedColor)
                    .append(Component.text("ENABLED", positiveColor).decoration(TextDecoration.BOLD, true))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("This item will be auto-sold", positiveColor)
                    .decoration(TextDecoration.ITALIC, false));
            // Show per-item min price if set
            if (perItemMinPrice != null) {
                lore.add(Component.text("Min price: ", mutedColor)
                        .append(Component.text(configManager.formatCurrency(perItemMinPrice), positiveColor))
                        .decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("Status: ", mutedColor)
                    .append(Component.text("DISABLED", negativeColor).decoration(TextDecoration.BOLD, true))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("This item will not be auto-sold", negativeColor)
                    .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Left-click: set min price (anvil)", mutedColor)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Shift+click: toggle enabled/disabled", mutedColor)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);

        if (enabled) {
            meta.setEnchantmentGlintOverride(true);
        }

        display.setItemMeta(meta);

        return new GuiItem(display, event -> {
            // Shift+left-click = toggle enabled/disabled
            if (event.isShiftClick() && event.isLeftClick()) {
                autosellManager.toggleItem(player, shopItem.id());
                renderItemsView();
                return;
            }
            // Normal left-click = open anvil to set per-item min price
            if (event.isLeftClick()) {
                player.closeInventory();
                new AnvilMinPriceGui(plugin, player, shopItem).open();
                return;
            }
            // Right-click also opens anvil (more discoverable for some players)
            if (event.isRightClick()) {
                player.closeInventory();
                new AnvilMinPriceGui(plugin, player, shopItem).open();
            }
        });
    }

    private StaticPane createSectionsNavigationPane() {
        ColorsConfig colors = configManager.getConfig().gui().colors();
        MaterialsConfig materials = configManager.getConfig().gui().materials();

        StaticPane pane = new StaticPane(0, 5, 9, 1);

        pane.addItem(createStatusItem(), 4, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.enableAll(), Material.LIME_DYE),
                "Enable All", configManager.resolveColor(colors.positive()),
                event -> {
                    autosellManager.enableAllItems(player);
                    refreshGui();
                }), 2, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.disableAll(), Material.RED_DYE),
                "Disable All", configManager.resolveColor(colors.negative()),
                event -> {
                    autosellManager.disableAllItems(player);
                    refreshGui();
                }), 6, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.sellInventory(), Material.HOPPER),
                "Sell Inventory", configManager.resolveColor(colors.sectionName()),
                event -> {
                    player.closeInventory();
                    autosellManager.sellInventory(player);
                }), 8, 0);

        return pane;
    }

    private StaticPane createItemsNavigationPane() {
        ColorsConfig colors = configManager.getConfig().gui().colors();
        MaterialsConfig materials = configManager.getConfig().gui().materials();

        StaticPane pane = new StaticPane(0, 5, 9, 1);

        TextColor accentColor = configManager.resolveColor(colors.accent());

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.previousPage(), Material.ARROW),
                "Previous Page", accentColor,
                event -> {
                    if (itemsPane.getPage() > 0) {
                        itemsPane.setPage(itemsPane.getPage() - 1);
                        gui.update();
                    }
                }), 0, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.back(), Material.DARK_OAK_DOOR),
                "Back", accentColor,
                event -> openSections()), 1, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.enableAll(), Material.LIME_DYE),
                "Enable All", configManager.resolveColor(colors.positive()),
                event -> {
                    autosellManager.enableAllItems(player);
                    renderItemsView();
                }), 2, 0);

        pane.addItem(createStatusItem(), 4, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.disableAll(), Material.RED_DYE),
                "Disable All", configManager.resolveColor(colors.negative()),
                event -> {
                    autosellManager.disableAllItems(player);
                    renderItemsView();
                }), 6, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.sellInventory(), Material.HOPPER),
                "Sell Inventory", configManager.resolveColor(colors.sectionName()),
                event -> {
                    player.closeInventory();
                    autosellManager.sellInventory(player);
                }), 7, 0);

        pane.addItem(createNavigationItem(
                configManager.resolveMaterial(materials.nextPage(), Material.ARROW),
                "Next Page", accentColor,
                event -> {
                    if (itemsPane.getPage() < itemsPane.getPages() - 1) {
                        itemsPane.setPage(itemsPane.getPage() + 1);
                        gui.update();
                    }
                }), 8, 0);

        return pane;
    }

    private GuiItem createStatusItem() {
        ColorsConfig colors = configManager.getConfig().gui().colors();
        MaterialsConfig materials = configManager.getConfig().gui().materials();

        ItemStack item = new ItemStack(configManager.resolveMaterial(materials.pageIndicator(), Material.PAPER));
        ItemMeta meta = item.getItemMeta();

        Set<Integer> enabledItems = autosellManager.getEnabledItems(player.getUniqueId());
        int enabledCount = enabledItems.size();
        int totalCount = shopManager.getAllItemIds().size();

        meta.displayName(Component.text("Autosell Status", configManager.resolveColor(colors.sectionName()))
                .decoration(TextDecoration.ITALIC, false));

        TextColor mutedColor = configManager.resolveColor(colors.muted());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Enabled items: ", mutedColor)
                .append(Component.text(enabledCount + "/" + totalCount, configManager.resolveColor(colors.itemName())))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("When enabled for an item:", mutedColor)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("- Auto-sells on pickup", configManager.resolveColor(colors.sellPrice()))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("- Use 'Sell Inventory' to sell all", configManager.resolveColor(colors.sellPrice()))
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);

        return new GuiItem(item, event -> {
        });
    }

    private GuiItem createNavigationItem(Material material, String name, TextColor color,
                                         java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return new GuiItem(item, action);
    }

    private void refreshGui() {
        if (viewMode == ViewMode.SECTIONS) {
            openSections();
        } else {
            renderItemsView();
        }
    }

    private String sectionTitle(String sectionId) {
        if (sectionId == null) {
            return configManager.getConfig().gui().titles().autosell();
        }

        for (Section section : loadSections()) {
            if (section.id().equalsIgnoreCase(sectionId)) {
                return section.displayName();
            }
        }

        return configManager.getConfig().gui().titles().autosell();
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
