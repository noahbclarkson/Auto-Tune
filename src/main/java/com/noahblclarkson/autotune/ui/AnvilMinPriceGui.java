package com.noahblclarkson.autotune.ui;

import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.manager.AutosellManager;
import com.noahblclarkson.autotune.manager.ShopManager;
import com.noahblclarkson.autotune.model.ShopItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens an anvil GUI so players can type a custom minimum sell price for an item.
 * The player places the item in the left slot and types a price in the rename field.
 * Clicking the output slot reads the current rename text and sets it as the min price.
 *
 * Usage:
 *   new AnvilMinPriceGui(plugin, player, shopItem).open();
 */
public class AnvilMinPriceGui implements Listener {

    private final AutoTune plugin;
    private final Player player;
    private final ShopItem shopItem;
    private final int itemId;
    private final AutosellManager autosellManager;
    private final ShopManager shopManager;
    private final ConfigManager configManager;

    /**
     * Guards against multiple open anvil GUIs per player and stores the last-known
     * rename text (updated on every PrepareAnvilEvent so we have it when the player
     * clicks the output slot).
     */
    private static final Map<UUID, String> PLAYER_RENAME_TEXT = new ConcurrentHashMap<>();
    private static final Map<UUID, AnvilMinPriceGui> OPEN_GUIS = new ConcurrentHashMap<>();
    // Anvil slot index 2 = output slot (where the renamed item appears)
    private static final int ANVIL_OUTPUT_SLOT = 2;

    public AnvilMinPriceGui(
            AutoTune plugin,
            Player player,
            ShopItem shopItem
    ) {
        this.plugin = plugin;
        this.player = player;
        this.shopItem = shopItem;
        this.itemId = shopItem.id();
        this.autosellManager = plugin.getAutosellManager();
        this.shopManager = plugin.getShopManager();
        this.configManager = plugin.getConfigManager();
    }

    /**
     * Opens the anvil GUI for this player. The left slot is pre-filled with the shop item
     * so the player sees which item they're editing.
     */
    public void open() {
        OPEN_GUIS.remove(player.getUniqueId());
        PLAYER_RENAME_TEXT.remove(player.getUniqueId());

        Bukkit.getPluginManager().registerEvents(this, plugin);
        OPEN_GUIS.put(player.getUniqueId(), this);

        // Bukkit.createInventory with ANVIL returns CraftInventoryCustom, not AnvilInventory.
        // Use the Inventory interface — PrepareAnvilEvent still fires from its own inventory.
        String title = "Min Price — " + shopItem.getDisplayNameOrMaterial();
        Inventory anvil = Bukkit.createInventory(null, InventoryType.ANVIL, Component.text(title));

        // Pre-fill left slot with a hint item showing the current effective price
        ItemStack hintItem = buildHintItem();
        anvil.setItem(0, hintItem);

        player.openInventory(anvil);
    }

    /**
     * Fires every time the anvil recomputes its result (e.g., player types a character).
     * Capture the rename text so we have it when the player clicks output.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!event.getView().getPlayer().equals(player)) {
            return;
        }
        AnvilInventory inv = event.getInventory();
        // getRenameText() is deprecated but is the only way in Paper 1.21.4 to read the anvil text field.
        @SuppressWarnings("deprecation")
        String text = inv.getRenameText();
        PLAYER_RENAME_TEXT.put(player.getUniqueId(), text != null ? text : "");
    }

    /**
     * Fires when the player clicks any slot in the anvil GUI.
     * We only care about clicks on the output slot (raw slot 2).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getPlayer().equals(player)) {
            return;
        }
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }

        // Raw slot 2 = output slot in anvil view
        if (event.getRawSlot() != ANVIL_OUTPUT_SLOT) {
            return;
        }

        event.setCancelled(true);

        // Get the rename text that was last captured by PrepareAnvilEvent.
        // If the player clicked output without PrepareAnvilEvent firing first
        // (e.g., same text re-applied), PLAYER_RENAME_TEXT may still have the
        // previous value — that's fine, it's still what the player sees.
        String renameText = PLAYER_RENAME_TEXT.getOrDefault(player.getUniqueId(), "").trim();

        // Also try reading directly from the output slot's item display name.
        // After InventoryClickEvent processes, the item reflects the renamed result.
        ItemStack output = event.getView().getItem(2);
        String fromOutput = "";
        if (output != null && output.hasItemMeta() && output.getItemMeta().displayName() != null) {
            fromOutput = PlainTextComponentSerializer.plainText().serialize(
                    output.getItemMeta().displayName());
        }

        // Prefer the more specific text (from the output item's display name)
        String rawText = !fromOutput.isEmpty() ? fromOutput : renameText;
        rawText = rawText.trim();

        if (rawText.isEmpty() || rawText.equalsIgnoreCase("cancel") || rawText.equalsIgnoreCase("remove")) {
            autosellManager.removeMinPrice(player, itemId);
            closeQuietly();
            return;
        }

        Optional<Double> priceOpt = parsePrice(rawText);
        if (priceOpt.isEmpty()) {
            player.sendMessage(Component.text(
                    "Invalid price: '" + rawText + "'. Enter a positive number (e.g. 1.50) or leave blank to remove the per-item minimum.",
                    NamedTextColor.RED));
            return;
        }

        double price = priceOpt.get();
        if (price < 0) {
            player.sendMessage(Component.text(
                    "Price cannot be negative. Enter a positive number.",
                    NamedTextColor.RED));
            return;
        }

        autosellManager.setMinPrice(player, itemId, price);
        closeQuietly();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) {
            return;
        }
        cleanup();
    }

    private void closeQuietly() {
        cleanup();
        player.closeInventory();
    }

    private void cleanup() {
        OPEN_GUIS.remove(player.getUniqueId());
        PLAYER_RENAME_TEXT.remove(player.getUniqueId());
        // Unregister only this listener instance, not all plugin listeners.
        // Calling getHandlerList().unregister(plugin) would destroy InventoryFramework's GuiListener.
        HandlerList.unregisterAll(this);
    }

    /**
     * Builds the hint item placed in the anvil's left input slot.
     * Its display name shows the current effective min price.
     */
    private ItemStack buildHintItem() {
        ItemStack item = new ItemStack(shopItem.material());
        ItemMeta meta = item.getItemMeta();

        BigDecimal effectiveMin = BigDecimal.valueOf(
                autosellManager.getEffectiveMinPrice(player.getUniqueId(), itemId));
        BigDecimal globalMin = BigDecimal.valueOf(
                configManager.getConfig().autosell().minimumPrice());

        String currentLabel = "Current min: " + configManager.formatCurrency(effectiveMin);
        if (effectiveMin.compareTo(globalMin) <= 0) {
            currentLabel += " (global default)";
        }
        currentLabel += " — Type new price above";

        meta.displayName(Component.text(currentLabel, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Parses a price string. Accepts plain numbers ("5.00", "1.5"), currency symbols
     * ("$5.00", "£1.50"), or any text that contains a valid positive number.
     */
    private Optional<Double> parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        // Strip currency symbols, letters, and whitespace — keep only digits, dots, minus
        String cleaned = text.replaceAll("[^0-9.\\-]", "");

        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return Optional.empty();
        }

        try {
            double price = Double.parseDouble(cleaned);
            if (price < 0 || Double.isNaN(price) || Double.isInfinite(price)) {
                return Optional.empty();
            }
            return Optional.of(price);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
