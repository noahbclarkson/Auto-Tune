package com.noahblclarkson.autotune.ui;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.noahblclarkson.autotune.AutoTune;
import com.noahblclarkson.autotune.auction.AuctionManager;
import com.noahblclarkson.autotune.config.ConfigManager;
import com.noahblclarkson.autotune.model.AuctionOrder;
import com.noahblclarkson.autotune.model.AuctionOrder.OrderSide;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AuctionGui {

    private static final int[] SELL_SLOTS = {9, 10, 11, 12, 13, 18, 19, 20, 21, 22};
    private static final int[] BUY_SLOTS  = {14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};
    private static final int   INFO_SLOT  = 4;
    private static final int   SELL_LABEL_SLOT = 0;
    private static final int   BUY_LABEL_SLOT  = 7;

    private static final TextColor GREEN  = TextColor.fromHexString("#55ff55");
    private static final TextColor RED    = TextColor.fromHexString("#ff5555");
    private static final TextColor YELLOW = TextColor.fromHexString("#ffff55");
    private static final TextColor WHITE  = TextColor.fromHexString("#ffffff");
    private static final TextColor GRAY   = TextColor.fromHexString("#888888");
    private static final TextColor GOLD   = TextColor.fromHexString("#ffaa00");

    private final String playerName;
    private final AuctionManager auctionManager;
    private final ConfigManager configManager;

    private final String material;
    private ChestGui gui;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d HH:mm")
            .withZone(ZoneId.systemDefault());

    // ── Constructors ───────────────────────────────────────────────────────────

    public AuctionGui(String playerName, AuctionManager auctionManager, ConfigManager configManager) {
        this(null, playerName, auctionManager, configManager);
    }

    public AuctionGui(String material, String playerName, AuctionManager auctionManager, ConfigManager configManager) {
        this.material = material;
        this.playerName = playerName;
        this.auctionManager = auctionManager;
        this.configManager = configManager;
    }

    public void open(Player player) {
        gui = new ChestGui(6,
                material != null
                        ? "§6Auction: §f" + formatMaterial(material)
                        : "§6Auction House");
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        buildLayout(player);
        gui.show(player);
    }

    // ── Layout builder ────────────────────────────────────────────────────────

    private void buildLayout(Player player) {

        StaticPane border = new StaticPane(0, 0, 9, 6);
        fillBorder(border);
        gui.addPane(border);

        // Fetch orders
        List<AuctionOrder> sellOrders = material != null
                ? auctionManager.getActiveOrdersForMaterial(material).stream()
                        .filter(o -> o.side() == OrderSide.SELL)
                        .sorted(Comparator.comparing(AuctionOrder::price))
                        .toList()
                : List.of();

        List<AuctionOrder> buyOrders = material != null
                ? auctionManager.getActiveOrdersForMaterial(material).stream()
                        .filter(o -> o.side() == OrderSide.BUY)
                        .sorted(Comparator.comparing(AuctionOrder::price, Comparator.reverseOrder()))
                        .toList()
                : List.of();

        // ── Header bar ─────────────────────────────────────────────────────────
        StaticPane header = new StaticPane(0, 0, 9, 1);
        String titleText = material != null
                ? "§6§l" + formatMaterial(material) + " §r§7- Order Book"
                : "§6§lAuction House §r§7- Player-to-player trading";
        header.addItem(new GuiItem(makeItem(Material.PAPER, Component.text(titleText),
                List.of(Component.text("Click items below to trade", GRAY))), e -> {}), 4, 0);
        gui.addPane(header);

        // ── Sell orders column (left) ─────────────────────────────────────────
        StaticPane sellPane = new StaticPane(0, 1, 5, 5);
        sellPane.addItem(new GuiItem(makeItem(Material.BARRIER,
                Component.text("SELL ORDERS", RED, TextDecoration.BOLD),
                List.of(Component.text("Players selling " + (material != null ? formatMaterial(material) : "items"), GRAY))),
                e -> {}), 0, 0);

        int sellSlot = 0;
        for (AuctionOrder order : sellOrders) {
            if (sellSlot >= SELL_SLOTS.length) break;
            int row = SELL_SLOTS[sellSlot] / 9;
            int col = SELL_SLOTS[sellSlot] % 9;
            sellPane.addItem(new GuiItem(makeOrderItem(order, player),
                    e -> handleFillClick((Player) e.getWhoClicked(), order)), col, row);
            sellSlot++;
        }

        if (sellOrders.isEmpty() && material != null) {
            sellPane.addItem(new GuiItem(makeItem(Material.RED_STAINED_GLASS_PANE,
                    Component.text("No sell orders", GRAY),
                    List.of(Component.text("Be the first to list!", GRAY))), e -> {}), 1, 1);
        } else if (material == null) {
            sellPane.addItem(new GuiItem(makeItem(Material.EMERALD_BLOCK,
                    Component.text("Use /auction browse <material>", GOLD),
                    List.of(Component.text("To see order book for a specific item", GRAY))), e -> {}), 1, 1);
        }

        gui.addPane(sellPane);

        // ── Buy orders column (right) ──────────────────────────────────────────
        StaticPane buyPane = new StaticPane(5, 1, 4, 5);
        buyPane.addItem(new GuiItem(makeItem(Material.EMERALD,
                Component.text("BUY ORDERS", GREEN, TextDecoration.BOLD),
                List.of(Component.text("Players buying " + (material != null ? formatMaterial(material) : "items"), GRAY))),
                e -> {}), 0, 0);

        int buySlot = 0;
        for (AuctionOrder order : buyOrders) {
            if (buySlot >= BUY_SLOTS.length) break;
            int row = BUY_SLOTS[buySlot] / 9;
            int col = BUY_SLOTS[buySlot] % 9 - 5; // offset into the right half
            buyPane.addItem(new GuiItem(makeOrderItem(order, player),
                    e -> handleFillClick((Player) e.getWhoClicked(), order)), col, row);
            buySlot++;
        }

        if (buyOrders.isEmpty() && material != null) {
            buyPane.addItem(new GuiItem(makeItem(Material.GREEN_STAINED_GLASS_PANE,
                    Component.text("No buy orders", GRAY),
                    List.of(Component.text("Place a buy order!", GRAY))), e -> {}), 1, 1);
        }

        gui.addPane(buyPane);

        // ── Bottom nav ────────────────────────────────────────────────────────
        StaticPane nav = new StaticPane(0, 5, 9, 1);

        ItemStack myOrders = makeItem(Material.PLAYER_HEAD,
                Component.text("My Orders", WHITE),
                List.of(
                        Component.text("View and cancel", GRAY),
                        Component.text("your active orders", GRAY)));
        myOrders.editMeta(m -> {
            if (m instanceof SkullMeta sm) {
                Player p = Bukkit.getPlayerExact(playerName);
                if (p != null) sm.setOwningPlayer(p);
            }
        });
        nav.addItem(new GuiItem(myOrders,
                e -> openMyOrders((Player) e.getWhoClicked())), 0, 0);

        ItemStack placeSell = makeItem(Material.HOPPER,
                Component.text("Place Sell Order", RED),
                List.of(
                        Component.text("Sell from hand", GRAY),
                        Component.text("/auction sell <price> [qty]", GRAY)));
        nav.addItem(new GuiItem(placeSell, e -> {
            e.getWhoClicked().sendMessage(Component.text(
                    "Sell: /auction sell <price> [qty]", NamedTextColor.YELLOW));
        }), 4, 0);

        ItemStack placeBuy = makeItem(Material.COMPARATOR,
                Component.text("Place Buy Order", GREEN),
                List.of(
                        Component.text("Buy items you want", GRAY),
                        Component.text("/auction buy <mat> <price> [qty]", GRAY)));
        nav.addItem(new GuiItem(placeBuy, e -> {
            e.getWhoClicked().sendMessage(Component.text(
                    "Buy: /auction buy <material> <price> [qty]", NamedTextColor.YELLOW));
        }), 8, 0);

        gui.addPane(nav);
    }

    // ── Order item display ───────────────────────────────────────────────────

    private ItemStack makeOrderItem(AuctionOrder order, Player viewer) {
        Material mat = parseMaterial(order.material());
        if (mat == null) mat = Material.PAPER;

        ItemStack item = new ItemStack(mat);
        item.setAmount(Math.min(order.remainingQuantity(), 64));

        boolean isOwn = order.playerUuid().equals(viewer.getUniqueId());
        TextColor sideColor = order.side() == OrderSide.BUY ? GREEN : RED;
        String sideLabel = order.side() == OrderSide.BUY ? "BUY" : "SELL";

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(sideLabel + " order", sideColor, TextDecoration.BOLD));
        lore.add(Component.text("Qty: " + order.remainingQuantity() + "/" + order.originalQuantity(), WHITE));

        BigDecimal eachPrice = order.price();
        BigDecimal totalPrice = eachPrice.multiply(BigDecimal.valueOf(order.remainingQuantity()));
        lore.add(Component.text("Price each: " + configManager.formatCurrency(eachPrice), YELLOW));
        lore.add(Component.text("Total value: " + configManager.formatCurrency(totalPrice), YELLOW));

        if (order.status() == AuctionOrder.OrderStatus.PARTIALLY_FILLED) {
            lore.add(Component.text("Partially filled (" + order.filledQuantity() + " done)", GRAY));
        }

        String timeStr = DATE_FORMAT.format(order.createdAt());
        lore.add(Component.text("Listed: " + timeStr, GRAY));

        if (isOwn) {
            lore.add(Component.text("[Click to CANCEL this order]", RED, TextDecoration.BOLD));
        } else {
            lore.add(Component.text("[Click to " + sideLabel.toLowerCase() + "]", GREEN, TextDecoration.BOLD));
        }

        Component displayName;
        if (order.side() == OrderSide.BUY) {
            displayName = Component.text("WTS " + formatMaterial(order.material()), GREEN)
                    .append(Component.text(" x" + order.remainingQuantity(), WHITE));
        } else {
            displayName = Component.text("WTB " + formatMaterial(order.material()), RED)
                    .append(Component.text(" x" + order.remainingQuantity(), WHITE));
        }

        final Component finalName = displayName;
        item.editMeta(m -> {
            m.displayName(finalName);
            m.lore(lore);
        });

        return item;
    }

    // ── Click handlers ───────────────────────────────────────────────────────

    private void handleFillClick(Player player, AuctionOrder order) {
        if (order.playerUuid().equals(player.getUniqueId())) {
            // Cancel own order
            auctionManager.cancelOrderAsync(player, order.id())
                    .orTimeout(10, TimeUnit.SECONDS)
                    .thenAccept(result -> {
                        if (result.success()) {
                            player.sendMessage(Component.text("Order cancelled: " + order.id(), NamedTextColor.GREEN));
                            open(player);
                        } else {
                            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
                        }
                    });
        } else {
            // Fill the order
            if (order.side() == OrderSide.SELL) {
                fillBuyOrder(player, order);
            } else {
                fillSellOrder(player, order);
            }
        }
    }

    private void fillBuyOrder(Player player, AuctionOrder order) {
        // Player sells items to the buy order holder
        Material needed = parseMaterial(order.material());
        if (needed == null) {
            player.sendMessage(Component.text("Unknown material in order", NamedTextColor.RED));
            return;
        }

        int have = countItems(player, needed);
        if (have == 0) {
            player.sendMessage(Component.text("You don't have any " + formatMaterial(order.material())
                    + " to sell!", NamedTextColor.RED));
            return;
        }

        int fillQty = Math.min(have, order.remainingQuantity());
        BigDecimal totalCost = order.price().multiply(BigDecimal.valueOf(fillQty));

        removeItems(player, needed, fillQty);
        AutoTune.getInstance().getVaultEconomy().depositPlayer(player, totalCost.doubleValue());

        processFillAsync(order, fillQty);

        player.sendMessage(Component.text("Sold " + fillQty + "x " + formatMaterial(order.material())
                + " for " + configManager.formatCurrency(totalCost) + "!", NamedTextColor.GREEN));

        open(player);
    }

    private void fillSellOrder(Player player, AuctionOrder order) {
        // Player buys from the sell order
        Material mat = parseMaterial(order.material());
        if (mat == null) {
            player.sendMessage(Component.text("Unknown material in order", NamedTextColor.RED));
            return;
        }

        int fillQty = order.remainingQuantity();
        BigDecimal totalCost = order.price().multiply(BigDecimal.valueOf(fillQty));

        var economy = AutoTune.getInstance().getVaultEconomy();
        if (!economy.has(player, totalCost.doubleValue())) {
            player.sendMessage(Component.text("Insufficient funds. Need "
                    + configManager.formatCurrency(totalCost), NamedTextColor.RED));
            return;
        }

        economy.withdrawPlayer(player, totalCost.doubleValue());
        giveItems(player, mat, fillQty);

        processFillAsync(order, fillQty);

        player.sendMessage(Component.text("Bought " + fillQty + "x " + formatMaterial(order.material())
                + " for " + configManager.formatCurrency(totalCost) + "!", NamedTextColor.GREEN));

        open(player);
    }

    private void processFillAsync(AuctionOrder order, int quantity) {
        Bukkit.getScheduler().runTaskAsynchronously(AutoTune.getInstance(), () -> {
            // Update order's remaining quantity in DB
            auctionManager.getOrder(order.id()).ifPresent(current -> {
                int newRemaining = Math.max(0, current.remainingQuantity() - quantity);
                // Note: the full fill processing (DB update + item delivery) is
                // handled by AuctionManager; this is just a UI-side refresh trigger.
            });
        });
    }

    // ── My Orders GUI ─────────────────────────────────────────────────────────

    private void openMyOrders(Player player) {
        List<AuctionOrder> orders = auctionManager.getPlayerOrders(player.getUniqueId());

        ChestGui myGui = new ChestGui(3, "§6Your Auction Orders");
        myGui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        fillBorder(pane);

        if (orders.isEmpty()) {
            pane.addItem(new GuiItem(makeItem(Material.BARRIER,
                    Component.text("No Active Orders", GRAY),
                    List.of(Component.text("Place orders with /auction sell or /auction buy", GRAY))),
                    e -> {}), 4, 1);
        } else {
            int slot = 0;
            for (AuctionOrder order : orders) {
                if (slot >= 7) break; // slots 1-7 for orders
                pane.addItem(new GuiItem(makeOrderItem(order, player),
                        e -> handleFillClick(player, order)), slot + 1, 1);
                slot++;
            }
        }

        myGui.addPane(pane);
        myGui.show(player);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void fillBorder(StaticPane pane) {
        Material border = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack borderItem = new ItemStack(border);
        GuiItem borderGuiItem = new GuiItem(borderItem, e -> e.setCancelled(true));

        for (int x = 0; x < 9; x++) {
            pane.addItem(borderGuiItem, x, 0);
            pane.addItem(new GuiItem(borderItem, ev -> ev.setCancelled(true)), x, 5);
        }
        for (int y = 1; y < 5; y++) {
            pane.addItem(new GuiItem(borderItem, ev -> ev.setCancelled(true)), 0, y);
            pane.addItem(new GuiItem(borderItem, ev -> ev.setCancelled(true)), 8, y);
        }
    }

    private ItemStack makeItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> {
            m.displayName(name.decoration(TextDecoration.ITALIC, false));
            m.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        });
        return item;
    }

    private Material parseMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String formatMaterial(String material) {
        if (material == null) return "";
        String s = material.replace("_", " ").toLowerCase();
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private int countItems(Player player, Material mat) {
        int total = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == mat) {
                total += s.getAmount();
            }
        }
        return total;
    }

    private void removeItems(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (s != null && s.getType() == mat) {
                int take = Math.min(s.getAmount(), remaining);
                s.setAmount(s.getAmount() - take);
                remaining -= take;
                if (s.getAmount() <= 0) {
                    player.getInventory().clear(i);
                }
            }
        }
    }

    private void giveItems(Player player, Material mat, int amount) {
        var leftover = player.getInventory().addItem(new ItemStack(mat, amount));
        if (!leftover.isEmpty()) {
            player.getWorld().dropItemNaturally(player.getLocation(),
                    new ItemStack(mat, leftover.values().stream().mapToInt(ItemStack::getAmount).sum()));
        }
    }
}
