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
import net.milkbowl.vault.economy.Economy;
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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class AuctionGui {

    // Slot positions within the buy pane (4-wide, rows 1-4 inside pane = rows 1-4 of gui)
    // 4 items per row x 4 rows = 16 max, but we show 12 to leave room for nav
    private static final int[] BUY_SLOTS = {
            // Row 1 (gui row 1, pane row 0): cols 0-2
            9,  10, 11,
            // Row 2 (gui row 2, pane row 1): cols 0-2
            18, 19, 20,
            // Row 3 (gui row 3, pane row 2): cols 0-2
            27, 28, 29,
            // Row 4 (gui row 4, pane row 3): cols 0-2
            36, 37, 38
    };
    private static final int[] SELL_SLOTS = {
            // Row 1 (gui row 1, pane row 0): cols 0-4
            9,  10, 11, 12, 13,
            // Row 2 (gui row 2, pane row 1): cols 0-4
            18, 19, 20, 21, 22
    };
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
    private final Economy economy;

    private final String material;
    private ChestGui gui;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d HH:mm")
            .withZone(ZoneId.systemDefault());

    // Constructors

    public AuctionGui(String playerName, AuctionManager auctionManager,
                      ConfigManager configManager, Economy economy) {
        this(null, playerName, auctionManager, configManager, economy);
    }

    public AuctionGui(String material, String playerName, AuctionManager auctionManager,
                      ConfigManager configManager, Economy economy) {
        this.material = material;
        this.playerName = playerName;
        this.auctionManager = auctionManager;
        this.configManager = configManager;
        this.economy = economy;
    }

    public void open(Player player) {
        gui = new ChestGui(6,
                material != null
                        ? "Â§6Auction: Â§f" + formatMaterial(material)
                        : "Â§6Auction House");
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        buildLayout(player);
        gui.show(player);
    }

    // Layout builder

    private void buildLayout(Player player) {

        StaticPane border = new StaticPane(0, 0, 9, 6);
        fillBorder(border, 6);
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

        // Header bar
        StaticPane header = new StaticPane(0, 0, 9, 1);
        String titleText = material != null
                ? "Â§6Â§l" + formatMaterial(material) + " Â§rÂ§7- Order Book"
                : "Â§6Â§lAuction House Â§rÂ§7- Player-to-player trading";
        header.addItem(new GuiItem(makeItem(Material.PAPER, Component.text(titleText),
                List.of(Component.text("Click items below to trade", GRAY))), e -> {}), 4, 0);
        gui.addPane(header);

        // Sell orders column (left)
        StaticPane sellPane = new StaticPane(0, 1, 9, 4);
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

        // Buy orders column (right)
        StaticPane buyPane = new StaticPane(5, 1, 3, 4);
        buyPane.addItem(new GuiItem(makeItem(Material.EMERALD,
                Component.text("BUY ORDERS", GREEN, TextDecoration.BOLD),
                List.of(Component.text("Players buying " + (material != null ? formatMaterial(material) : "items"), GRAY))),
                e -> {}), 0, 0);

        int buySlot = 0;
        for (AuctionOrder order : buyOrders) {
            int row = (buySlot / 3) + 1;
            int col = buySlot % 3;
            if (row >= 4) break;
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

        // Bottom nav
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

    // Order item display

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
            String action = order.side() == OrderSide.BUY ? "sell to this order" : "buy from this order";
            lore.add(Component.text("[Click to " + action + "]", GREEN, TextDecoration.BOLD));
        }

        Component displayName;
        if (order.side() == OrderSide.BUY) {
            displayName = Component.text("BUY ORDER " + formatMaterial(order.material()), GREEN)
                    .append(Component.text(" x" + order.remainingQuantity(), WHITE));
        } else {
            displayName = Component.text("SELL ORDER " + formatMaterial(order.material()), RED)
                    .append(Component.text(" x" + order.remainingQuantity(), WHITE));
        }

        final Component finalName = displayName;
        item.editMeta(m -> {
            m.displayName(finalName);
            m.lore(lore);
        });

        return item;
    }

    // Click handlers

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
                fillSellOrder(player, order);
            } else {
                fillBuyOrder(player, order);
            }
        }
    }

    private void fillBuyOrder(Player player, AuctionOrder order) {
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
        player.sendMessage(Component.text("Processing sale of " + fillQty + "x "
                + formatMaterial(order.material()) + "...", NamedTextColor.YELLOW));

        auctionManager.fillBuyOrderAsync(player, order.id(), fillQty)
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((result, ex) -> Bukkit.getScheduler().runTask(AutoTune.getInstance(), () -> {
                    if (ex != null) {
                        player.sendMessage(Component.text("Sale failed: " + ex.getMessage(), NamedTextColor.RED));
                    } else if (result.success()) {
                        player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
                    }
                    open(player);
                }));
    }
    private void fillSellOrder(Player player, AuctionOrder order) {
        Material mat = parseMaterial(order.material());
        if (mat == null) {
            player.sendMessage(Component.text("Unknown material in order", NamedTextColor.RED));
            return;
        }

        int fillQty = order.remainingQuantity();
        BigDecimal totalCost = order.price().multiply(BigDecimal.valueOf(fillQty));
        if (!economy.has(player, totalCost.doubleValue())) {
            player.sendMessage(Component.text("Insufficient funds. Need "
                    + configManager.formatCurrency(totalCost), NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("Processing purchase of " + fillQty + "x "
                + formatMaterial(order.material()) + "...", NamedTextColor.YELLOW));

        auctionManager.fillSellOrderAsync(player, order.id(), fillQty)
                .orTimeout(10, TimeUnit.SECONDS)
                .whenComplete((result, ex) -> Bukkit.getScheduler().runTask(AutoTune.getInstance(), () -> {
                    if (ex != null) {
                        player.sendMessage(Component.text("Purchase failed: " + ex.getMessage(), NamedTextColor.RED));
                    } else if (result.success()) {
                        player.sendMessage(Component.text(result.message(), NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
                    }
                    open(player);
                }));
    }
    // My Orders GUI

    private void openMyOrders(Player player) {
        List<AuctionOrder> orders = auctionManager.getPlayerOrders(player.getUniqueId());

        ChestGui myGui = new ChestGui(3, "Â§6Your Auction Orders");
        myGui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(0, 0, 9, 3);
        fillBorder(pane, 3);

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

    // Helpers

    private void fillBorder(StaticPane pane, int height) {
        Material border = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack borderItem = new ItemStack(border);
        GuiItem borderGuiItem = new GuiItem(borderItem, e -> e.setCancelled(true));

        for (int x = 0; x < 9; x++) {
            pane.addItem(borderGuiItem, x, 0);
            pane.addItem(new GuiItem(borderItem, ev -> ev.setCancelled(true)), x, height - 1);
        }
        for (int y = 1; y < height - 1; y++) {
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
            return Material.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String formatMaterial(String material) {
        if (material == null) return "";
        String s = material.replace("_", " ").toLowerCase(Locale.ROOT);
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
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
