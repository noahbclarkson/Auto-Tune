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
import com.noahblclarkson.autotune.database.TransactionRepository;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.model.Transaction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TransactionHistoryGui {

    public enum Mode {
        PLAYER,
        ADMIN
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AutoTune plugin;
    private final Player viewer;
    private final Mode mode;
    private final UUID filterPlayer;

    private ChestGui gui;
    private PaginatedPane transactionPane;

    public TransactionHistoryGui(AutoTune plugin, Player viewer, Mode mode, @Nullable UUID filterPlayer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.mode = mode;
        this.filterPlayer = filterPlayer;
    }

    public void open() {
        GuiConfig guiConfig = plugin.getConfigManager().getConfig().gui();
        String title = mode == Mode.ADMIN
                ? guiConfig.titles().adminTransactionHistory()
                : guiConfig.titles().transactionHistory();
        gui = new ChestGui(6, title);
        gui.setOnGlobalClick(event -> event.setCancelled(true));

        TransactionRepository transactionRepo = plugin.getInjector().getInstance(TransactionRepository.class);
        DatabaseManager databaseManager = plugin.getDatabaseManager();

        databaseManager.supplyAsync(() -> {
            if (filterPlayer != null) {
                return transactionRepo.findByPlayer(filterPlayer, 200);
            }
            return transactionRepo.findRecent(200);
        }).thenAccept(transactions ->
                databaseManager.runOnMain(() -> {
                    if (transactions == null) {
                        viewer.sendMessage(plugin.getConfigManager().getMessage("error.database"));
                        return;
                    }
                    renderTransactions(transactions);
                })).exceptionally(ex -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to load transactions", ex);
            return null;
        });
    }

    private void renderTransactions(List<Transaction> transactions) {
        ConfigManager configManager = plugin.getConfigManager();
        ColorsConfig colors = configManager.getConfig().gui().colors();

        transactionPane = new PaginatedPane(0, 0, 9, 5);

        ShopManager shopManager = plugin.getShopManager();

        TextColor buyColor = configManager.resolveColor(colors.buyPrice());
        TextColor sellColor = configManager.resolveColor(colors.sellPrice());
        TextColor mutedColor = configManager.resolveColor(colors.muted());
        TextColor itemNameColor = configManager.resolveColor(colors.itemName());
        TextColor sectionColor = configManager.resolveColor(colors.sectionName());
        TextColor accentColor = configManager.resolveColor(colors.accent());

        List<GuiItem> guiItems = new ArrayList<>();

        for (Transaction tx : transactions) {
            Optional<ShopItem> shopItemOpt = shopManager.getItemById(tx.itemId());
            Material displayMaterial = shopItemOpt.map(ShopItem::material).orElse(Material.PAPER);

            TextColor typeColor = tx.type() == Transaction.TransactionType.BUY ? buyColor : sellColor;
            String typeLabel = tx.type() == Transaction.TransactionType.BUY ? "BUY" : "SELL";

            ItemStack display = new ItemStack(displayMaterial);
            ItemMeta meta = display.getItemMeta();

            String itemName = shopItemOpt.map(ShopItem::getDisplayNameOrMaterial).orElse("Unknown");
            meta.displayName(Component.text(typeLabel + " - " + itemName, typeColor)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.text("Amount: ", mutedColor)
                    .append(Component.text(String.valueOf(tx.amount()), itemNameColor))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Price/Unit: ", mutedColor)
                    .append(Component.text(configManager.formatCurrency(tx.pricePerUnit()), itemNameColor))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Total: ", mutedColor)
                    .append(Component.text(configManager.formatCurrency(tx.totalPrice()), sectionColor))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Time: ", mutedColor)
                    .append(Component.text(TIME_FORMAT.format(tx.timestamp()), itemNameColor))
                    .decoration(TextDecoration.ITALIC, false));

            if (mode == Mode.ADMIN) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(tx.playerUuid());
                String playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : tx.playerUuid().toString();
                lore.add(Component.text("Player: ", mutedColor)
                        .append(Component.text(playerName, accentColor))
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            display.setItemMeta(meta);
            guiItems.add(new GuiItem(display, e -> {}));
        }

        if (guiItems.isEmpty()) {
            Material notAvailableMaterial = configManager.resolveMaterial(
                    configManager.getConfig().gui().materials().notAvailable(), Material.BARRIER);
            ItemStack noTx = new ItemStack(notAvailableMaterial);
            ItemMeta noTxMeta = noTx.getItemMeta();
            noTxMeta.displayName(Component.text("No transactions found", mutedColor)
                    .decoration(TextDecoration.ITALIC, false));
            noTx.setItemMeta(noTxMeta);
            guiItems.add(new GuiItem(noTx, e -> {}));
        }

        transactionPane.populateWithGuiItems(guiItems);
        gui.addPane(transactionPane);
        gui.addPane(createNavigationPane());
        gui.show(viewer);
    }

    private StaticPane createNavigationPane() {
        ConfigManager configManager = plugin.getConfigManager();
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
            if (transactionPane.getPage() > 0) {
                transactionPane.setPage(transactionPane.getPage() - 1);
                gui.update();
            }
        }), 0, 0);

        ItemStack next = new ItemStack(configManager.resolveMaterial(materials.nextPage(), Material.ARROW));
        ItemMeta nextMeta = next.getItemMeta();
        nextMeta.displayName(Component.text("Next Page", accentColor)
                .decoration(TextDecoration.ITALIC, false));
        next.setItemMeta(nextMeta);
        pane.addItem(new GuiItem(next, e -> {
            if (transactionPane.getPage() < transactionPane.getPages() - 1) {
                transactionPane.setPage(transactionPane.getPage() + 1);
                gui.update();
            }
        }), 8, 0);

        ItemStack close = new ItemStack(configManager.resolveMaterial(materials.close(), Material.BARRIER));
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(Component.text("Close", configManager.resolveColor(colors.negative()))
                .decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        pane.addItem(new GuiItem(close, e -> viewer.closeInventory()), 4, 0);

        return pane;
    }
}
