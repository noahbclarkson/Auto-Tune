package com.noahblclarkson.autotune.ui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.AutoTuneConfig;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.economy.EconomyManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import com.noahblclarkson.autotune.util.EnchantmentPricing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Pre-transaction confirmation dialog shown before executing buy or sell orders.
 * Displays an itemized summary with price, quantity, tax, and net total so players
 * can verify before committing. Eliminates accidental mis-clicks on large orders.
 */
public class CartConfirmGui {

    private static final TextColor GREEN   = TextColor.fromHexString("#55ff55");
    private static final TextColor RED     = TextColor.fromHexString("#ff5555");
    private static final TextColor YELLOW  = TextColor.fromHexString("#ffff55");
    private static final TextColor WHITE   = TextColor.fromHexString("#ffffff");
    private static final TextColor GRAY    = TextColor.fromHexString("#888888");
    private static final TextColor GOLD    = TextColor.fromHexString("#ffaa00");
    private static final TextColor ACCENT  = TextColor.fromHexString("#55aaff");
    private static final double ENCHANT_PRICE_THRESHOLD = 1.0;

    private final AutoTune plugin;
    private final Player player;
    private final ShopManager shopManager;
    private final EconomyManager economyManager;
    private final ConfigManager configManager;

    public CartConfirmGui(AutoTune plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.shopManager = plugin.getShopManager();
        this.economyManager = plugin.getEconomyManager();
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Shows a buy confirmation dialog. Player must click Confirm to execute.
     */
    public void showBuyConfirm(ShopItem shopItem, int amount) {
        BigDecimal pricePerUnit = shopManager.getBuyPrice(shopItem, amount);
        BigDecimal subtotal = pricePerUnit.multiply(BigDecimal.valueOf(amount));
        BigDecimal tax = plugin.getTreasuryService().calculateBuyTax(subtotal);
        BigDecimal total = subtotal.add(tax);
        BigDecimal playerBalance = BigDecimal.valueOf(economyManager.getBalance(player));

        String title = "§e§lConfirm Purchase";
        ChestGui gui = new ChestGui(3, title);
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        fillBorder(pane, Material.BLACK_STAINED_GLASS_PANE);

        // Row 1: item display
        ItemStack displayItem = new ItemStack(shopItem.material());
        displayItem.setAmount(Math.min(amount, 64));
        ItemMeta meta = displayItem.getItemMeta();
        meta.displayName(Component.text(shopItem.getDisplayNameOrMaterial(), GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = List.of(
                Component.text("Quantity: ", GRAY).append(Component.text(amount, WHITE)),
                Component.text("Price each: ", GRAY).append(Component.text(
                        configManager.formatCurrency(pricePerUnit), GREEN)),
                Component.text("Subtotal: ", GRAY).append(Component.text(
                        configManager.formatCurrency(subtotal), GREEN)),
                Component.text("Tax: ", GRAY).append(Component.text(
                        configManager.formatCurrency(tax), YELLOW)),
                Component.empty(),
                Component.text("TOTAL: ", GOLD, TextDecoration.BOLD)
                        .append(Component.text(configManager.formatCurrency(total), GOLD, TextDecoration.BOLD)),
                Component.text("Your balance: ", GRAY).append(Component.text(
                        configManager.formatCurrency(playerBalance), WHITE))
        );
        if (total.compareTo(playerBalance) > 0) {
            lore = new java.util.ArrayList<>(lore);
            lore.add(Component.text("⚠ INSUFFICIENT FUNDS", RED, TextDecoration.BOLD));
        }
        meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        displayItem.setItemMeta(meta);
        pane.addItem(new GuiItem(displayItem, e -> {}), 4, 1);

        // Row 2: Confirm / Cancel
        // Confirm (green) — only active if player has enough money
        boolean canAfford = total.compareTo(playerBalance) <= 0;
        Material confirmMat = canAfford ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE;
        ItemStack confirmItem = new ItemStack(confirmMat);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.displayName(Component.text("✓ CONFIRM PURCHASE", canAfford ? GREEN : GRAY)
                .decoration(TextDecoration.ITALIC, false));
        confirmMeta.lore(List.of(
                Component.text("Click to buy " + amount + "x " + shopItem.getDisplayNameOrMaterial(),
                        canAfford ? GREEN : GRAY)
        ));
        confirmItem.setItemMeta(confirmMeta);

        Material cancelMat = Material.RED_STAINED_GLASS_PANE;
        ItemStack cancelItem = new ItemStack(cancelMat);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.displayName(Component.text("✗ CANCEL", RED).decoration(TextDecoration.ITALIC, false));
        cancelMeta.lore(List.of(Component.text("Go back, no changes made", GRAY)));
        cancelItem.setItemMeta(cancelMeta);

        if (canAfford) {
            pane.addItem(new GuiItem(confirmItem, e -> executeBuy(shopItem, amount)), 2, 2);
        } else {
            pane.addItem(new GuiItem(makeDisabledItem("INSUFFICIENT FUNDS", RED), e -> {}), 2, 2);
        }
        pane.addItem(new GuiItem(cancelItem, e -> openBuySellGui(shopItem)), 6, 2);

        gui.addPane(pane);
        gui.show(player);
    }

    /**
     * Shows a sell confirmation dialog. Player must click Confirm to execute.
     */
    public void showSellConfirm(ShopItem shopItem, int amount, ItemStack itemStack) {
        BigDecimal basePricePerUnit = shopManager.getSellPrice(shopItem, amount);
        BigDecimal pricePerUnit = basePricePerUnit;

        // Apply enchantment multiplier if the item has enchantments
        if (itemStack != null) {
            double enchantMult = EnchantmentPricing.getMultiplier(itemStack,
                    configManager.getConfig().enchantment());
            if (enchantMult > ENCHANT_PRICE_THRESHOLD) {
                pricePerUnit = EnchantmentPricing.applyMultiplier(basePricePerUnit, enchantMult);
            }
        }

        BigDecimal subtotal = pricePerUnit.multiply(BigDecimal.valueOf(amount));
        BigDecimal tax = plugin.getTreasuryService().calculateSellTax(subtotal);
        BigDecimal netProceeds = subtotal.subtract(tax);

        String title = "§e§lConfirm Sale";
        ChestGui gui = new ChestGui(3, title);
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        fillBorder(pane, Material.BLACK_STAINED_GLASS_PANE);

        // Row 1: item display
        ItemStack displayItem = new ItemStack(shopItem.material());
        displayItem.setAmount(Math.min(amount, 64));
        ItemMeta meta = displayItem.getItemMeta();
        meta.displayName(Component.text(shopItem.getDisplayNameOrMaterial(), GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = List.of(
                Component.text("Quantity: ", GRAY).append(Component.text(amount, WHITE)),
                Component.text("Price each: ", GRAY).append(Component.text(
                        configManager.formatCurrency(pricePerUnit), GREEN)),
                Component.text("Subtotal: ", GRAY).append(Component.text(
                        configManager.formatCurrency(subtotal), GREEN)),
                Component.text("Tax: ", GRAY).append(Component.text(
                        configManager.formatCurrency(tax), YELLOW)),
                Component.empty(),
                Component.text("YOU RECEIVE: ", GREEN, TextDecoration.BOLD)
                        .append(Component.text(configManager.formatCurrency(netProceeds), GREEN, TextDecoration.BOLD))
        );
        meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        displayItem.setItemMeta(meta);
        pane.addItem(new GuiItem(displayItem, e -> {}), 4, 1);

        // Row 2: Confirm / Cancel
        ItemStack confirmItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.displayName(Component.text("✓ CONFIRM SALE", GREEN).decoration(TextDecoration.ITALIC, false));
        confirmMeta.lore(List.of(
                Component.text("Click to sell " + amount + "x " + shopItem.getDisplayNameOrMaterial(), GREEN)
        ));
        confirmItem.setItemMeta(confirmMeta);

        ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.displayName(Component.text("✗ CANCEL", RED).decoration(TextDecoration.ITALIC, false));
        cancelMeta.lore(List.of(Component.text("Go back, no changes made", GRAY)));
        cancelItem.setItemMeta(cancelMeta);

        pane.addItem(new GuiItem(confirmItem, e -> executeSell(shopItem, amount, itemStack)), 2, 2);
        pane.addItem(new GuiItem(cancelItem, e -> openBuySellGui(shopItem)), 6, 2);

        gui.addPane(pane);
        gui.show(player);
    }

    // ─── Execution after confirm ─────────────────────────────────────────────

    private void executeBuy(ShopItem shopItem, int amount) {
        economyManager.processBuyAsync(player, shopItem, amount).thenAccept(result ->
                plugin.getDatabaseManager().runOnMain(() -> {
                    if (result.success()) {
                        player.sendMessage(Component.text("✓ Purchased " + result.amount() + "x "
                                + shopItem.getDisplayNameOrMaterial() + " for "
                                + configManager.formatCurrency(result.totalPrice()), NamedTextColor.GREEN));
                        openBuySellGui(shopItem);
                    } else {
                        player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
                        openBuySellGui(shopItem);
                    }
                })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to process buy transaction", ex);
            return null;
        });
    }

    private void executeSell(ShopItem shopItem, int amount, ItemStack itemStack) {
        economyManager.processSellAsync(player, shopItem, amount).thenAccept(result ->
                plugin.getDatabaseManager().runOnMain(() -> {
                    if (result.success()) {
                        player.sendMessage(Component.text("✓ Sold " + result.amount() + "x "
                                + shopItem.getDisplayNameOrMaterial() + " for "
                                + configManager.formatCurrency(result.totalPrice()), NamedTextColor.GREEN));
                        openBuySellGui(shopItem);
                    } else {
                        player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
                        openBuySellGui(shopItem);
                    }
                })).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING, "Failed to process sell transaction", ex);
            return null;
        });
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void openBuySellGui(ShopItem shopItem) {
        new ShopGui(plugin, player).openBuySellGui(shopItem);
    }

    private void fillBorder(StaticPane pane, Material borderMat) {
        ItemStack borderItem = new ItemStack(borderMat);
        GuiItem borderGuiItem = new GuiItem(borderItem, e -> e.setCancelled(true));
        for (int x = 0; x < 9; x++) {
            pane.addItem(borderGuiItem, x, 0);
            pane.addItem(borderGuiItem, x, 2);
        }
        for (int y = 1; y <= 2; y++) {
            pane.addItem(borderGuiItem, 0, y);
            pane.addItem(borderGuiItem, 8, y);
        }
    }

    private ItemStack makeDisabledItem(String text, TextColor color) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(text, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
