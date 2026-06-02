package com.noahblclarkson.autotune.ui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.noahblclarkson.autotune.AutoTune;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Pre-transaction confirmation dialog shown before executing buy or sell orders.
 * Displays an itemized summary with price, quantity, tax, and net total so players
 * can verify before committing. Eliminates accidental mis-clicks on large orders.
 */
public class CartConfirmGui {

    private static final TextColor GREEN = TextColor.fromHexString("#55ff55");
    private static final TextColor RED = TextColor.fromHexString("#ff5555");
    private static final TextColor YELLOW = TextColor.fromHexString("#ffff55");
    private static final TextColor WHITE = TextColor.fromHexString("#ffffff");
    private static final TextColor GRAY = TextColor.fromHexString("#888888");
    private static final TextColor GOLD = TextColor.fromHexString("#ffaa00");
    private static final TextColor ACCENT = TextColor.fromHexString("#55aaff");
    private static final double ENCHANT_PRICE_THRESHOLD = 1.0;
    private static final Set<UUID> PENDING_TRANSACTIONS = ConcurrentHashMap.newKeySet();

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

        String title = "\u00a7e\u00a7lConfirm Purchase";
        ChestGui gui = new ChestGui(3, title);
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        fillBorder(pane, Material.BLACK_STAINED_GLASS_PANE);

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
            lore = new ArrayList<>(lore);
            lore.add(Component.text("\u26a0 INSUFFICIENT FUNDS", RED, TextDecoration.BOLD));
        }
        meta.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        displayItem.setItemMeta(meta);
        pane.addItem(new GuiItem(displayItem, e -> {}), 4, 1);

        boolean canAfford = total.compareTo(playerBalance) <= 0;
        boolean transactionPending = PENDING_TRANSACTIONS.contains(player.getUniqueId());
        boolean canSubmit = canAfford && !transactionPending;
        ItemStack confirmItem = makeConfirmItem(
                transactionPending ? "PROCESSING PURCHASE" : "\u2713 CONFIRM PURCHASE",
                transactionPending
                        ? "Please wait for the current transaction"
                        : "Click to buy " + amount + "x " + shopItem.getDisplayNameOrMaterial(),
                canSubmit ? GREEN : GRAY,
                canSubmit ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);

        ItemStack cancelItem = makeSimpleItem(Material.RED_STAINED_GLASS_PANE, "\u2717 CANCEL", RED,
                "Go back, no changes made", GRAY);

        if (canSubmit) {
            pane.addItem(new GuiItem(confirmItem, e -> executeBuy(shopItem, amount)), 2, 2);
        } else {
            pane.addItem(new GuiItem(transactionPending
                    ? makeDisabledItem("PROCESSING", GRAY)
                    : makeDisabledItem("INSUFFICIENT FUNDS", RED), e -> {}), 2, 2);
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

        String title = "\u00a7e\u00a7lConfirm Sale";
        ChestGui gui = new ChestGui(3, title);
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        fillBorder(pane, Material.BLACK_STAINED_GLASS_PANE);

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

        ItemStack confirmItem = makeConfirmItem(
                "\u2713 CONFIRM SALE",
                "Click to sell " + amount + "x " + shopItem.getDisplayNameOrMaterial(),
                GREEN,
                Material.LIME_STAINED_GLASS_PANE);
        ItemStack cancelItem = makeSimpleItem(Material.RED_STAINED_GLASS_PANE, "\u2717 CANCEL", RED,
                "Go back, no changes made", GRAY);

        if (PENDING_TRANSACTIONS.contains(player.getUniqueId())) {
            pane.addItem(new GuiItem(makeDisabledItem("PROCESSING", GRAY), e -> {}), 2, 2);
        } else {
            pane.addItem(new GuiItem(confirmItem, e -> executeSell(shopItem, amount)), 2, 2);
        }
        pane.addItem(new GuiItem(cancelItem, e -> openBuySellGui(shopItem)), 6, 2);

        gui.addPane(pane);
        gui.show(player);
    }

    private void executeBuy(ShopItem shopItem, int amount) {
        if (!markTransactionPending()) {
            return;
        }
        showProcessingDialog("Processing Purchase", shopItem, amount);
        try {
            economyManager.processBuyAsync(player, shopItem, amount).whenComplete((result, ex) ->
                    plugin.getDatabaseManager().runOnMain(() -> handleResult(
                            shopItem,
                            result,
                            ex,
                            "Failed to process buy transaction",
                            "Purchase failed. Please try again.",
                            "Purchased")));
        } catch (RuntimeException ex) {
            handleResult(shopItem, null, ex, "Failed to start buy transaction",
                    "Purchase failed. Please try again.", "Purchased");
        }
    }

    private void executeSell(ShopItem shopItem, int amount) {
        if (!markTransactionPending()) {
            return;
        }
        showProcessingDialog("Processing Sale", shopItem, amount);
        try {
            economyManager.processSellAsync(player, shopItem, amount).whenComplete((result, ex) ->
                    plugin.getDatabaseManager().runOnMain(() -> handleResult(
                            shopItem,
                            result,
                            ex,
                            "Failed to process sell transaction",
                            "Sale failed. Please try again.",
                            "Sold")));
        } catch (RuntimeException ex) {
            handleResult(shopItem, null, ex, "Failed to start sell transaction",
                    "Sale failed. Please try again.", "Sold");
        }
    }

    private void handleResult(
            ShopItem shopItem,
            EconomyManager.TransactionResult result,
            Throwable ex,
            String logMessage,
            String failureMessage,
            String successVerb
    ) {
        clearTransactionPending();
        if (ex != null) {
            plugin.getLogger().log(Level.WARNING, logMessage, ex);
            player.sendMessage(Component.text(failureMessage, NamedTextColor.RED));
        } else if (result == null) {
            player.sendMessage(Component.text(failureMessage, NamedTextColor.RED));
        } else if (result.success()) {
            player.sendMessage(Component.text(successVerb + " " + result.amount() + "x "
                    + shopItem.getDisplayNameOrMaterial() + " for "
                    + configManager.formatCurrency(result.totalPrice()), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(result.errorMessage(), NamedTextColor.RED));
        }
        openBuySellGui(shopItem);
    }

    private void openBuySellGui(ShopItem shopItem) {
        new ShopGui(plugin, player).openBuySellGui(shopItem);
    }

    private boolean markTransactionPending() {
        if (!PENDING_TRANSACTIONS.add(player.getUniqueId())) {
            player.sendMessage(Component.text("A transaction is already processing.", NamedTextColor.YELLOW));
            return false;
        }
        return true;
    }

    private void clearTransactionPending() {
        PENDING_TRANSACTIONS.remove(player.getUniqueId());
    }

    private void showProcessingDialog(String title, ShopItem shopItem, int amount) {
        ChestGui processingGui = new ChestGui(3, title);
        processingGui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        fillBorder(pane, Material.BLACK_STAINED_GLASS_PANE);

        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Processing...", ACCENT).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(amount + "x " + shopItem.getDisplayNameOrMaterial(), GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        pane.addItem(new GuiItem(item, e -> {}), 4, 1);

        processingGui.addPane(pane);
        processingGui.show(player);
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

    private ItemStack makeConfirmItem(String title, String lore, TextColor color, Material material) {
        return makeSimpleItem(material, title, color, lore, color);
    }

    private ItemStack makeDisabledItem(String text, TextColor color) {
        return makeSimpleItem(Material.GRAY_STAINED_GLASS_PANE, text, color, null, color);
    }

    private ItemStack makeSimpleItem(
            Material material,
            String title,
            TextColor titleColor,
            String lore,
            TextColor loreColor
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, titleColor).decoration(TextDecoration.ITALIC, false));
        if (lore != null) {
            meta.lore(List.of(Component.text(lore, loreColor).decoration(TextDecoration.ITALIC, false)));
        }
        item.setItemMeta(meta);
        return item;
    }
}
