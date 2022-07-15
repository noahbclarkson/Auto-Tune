package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.Pane.Priority;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.PurchaseUtil;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.events.TimePeriodEvent;
import unprotesting.com.github.util.Format;

/**
 * The command for buying and selling items.
 */
public class ShopCommand extends AutoTuneShopFormat implements CommandExecutor {

    private static final int[] AMOUNTS = {1, 2, 4, 8, 16, 32, 64};
    private static final int[] ENCHANTMENT_AMOUNTS = {1, 2, 3, 4, 5};

    public ShopCommand(@NotNull AutoTune plugin) {
        plugin.getCommand("shop").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (sender instanceof Player) {
            return interpret((Player) sender, args);
        }

        Format.sendMessage(sender, "<red>You must be a player to use this command.");
        return true;
    }

    @Override
    protected void doShop(@NotNull HumanEntity player, @NotNull ChestGui gui, 
        @NotNull String shopName) {
        gui.getPanes().clear();
        getBackground(gui);
        gui.addPane(getGdpPane((Player) player, gui));
        gui.addPane(getBackToShop((Player) player, gui, ShopUtil.getShop(shopName).getSection()));
        gui.addPane(getPurchasePane((Player) player, gui, shopName));
        gui.update();
    }

    protected static void update(@NotNull Player player) {
        Format.sendMessage(player, "<green>Updating prices...");
        Bukkit.getScheduler().runTask(AutoTune.getInstance(),
                () -> Bukkit.getPluginManager().callEvent(new TimePeriodEvent(false)));
        Format.sendMessage(player, "<green>Prices updated!");
    }

    private OutlinePane getPurchasePane(@NotNull Player player, 
        @NotNull ChestGui gui, @NotNull String shopName) {
        Shop shop = ShopUtil.getShop(shopName);
        int length = shop.isEnchantment() ? ENCHANTMENT_AMOUNTS.length : AMOUNTS.length;
        OutlinePane pane = new OutlinePane(1, 2, length, 2, Priority.HIGHEST);
        Config config = Config.get();
        List<String> lore = config.getPurchaseBuyLore();

        Material material = shop.isEnchantment() ? Material.ENCHANTED_BOOK
                : Material.matchMaterial(shopName);

        int[] amounts = shop.isEnchantment() ? ENCHANTMENT_AMOUNTS : AMOUNTS;
        boolean buy = true;

        for (int i = 0; i < 2; i++) {
            for (int amount : amounts) {
                ItemStack item = new ItemStack(material);

                if (shop.isEnchantment()) {
                    Enchantment enchantment = Enchantment.getByKey(
                        NamespacedKey.minecraft(shopName));
                    if (enchantment.getMaxLevel() < amount) {
                        GuiItem background = getBackgroundItem();
                        if (background != null) {
                            pane.addItem(background);
                        }
                        continue;
                    }
                    final int finalAmount = amount;
                    item.editMeta(meta -> meta.displayName(enchantment.displayName(finalAmount)));
                }

                if (!shop.isEnchantment()) {
                    int max = item.getMaxStackSize();
                    if (amount > max) {
                        GuiItem background = getBackgroundItem();
                        if (background != null) {
                            pane.addItem(background);
                        }
                        continue;
                    }
                }

                List<Component> loreList = getLore(player, shopName, lore, amount);
                item.setAmount(amount);
                item.lore(loreList);
                final boolean isBuy = buy;
                final int finalAmount = amount;
                GuiItem guiItem = new GuiItem(item, event -> {
                    event.setCancelled(true);
                    PurchaseUtil.purchaseItem(shopName, player, finalAmount, isBuy);
                    gui.getPanes().clear();
                    getBackground(gui);
                    gui.addPane(getGdpPane(player, gui));
                    gui.addPane(getBackToShop(player, gui, 
                        ShopUtil.getShop(shopName).getSection()));
                    gui.addPane(getPurchasePane(player, gui, shopName));
                    gui.update();
                });
                pane.addItem(guiItem);
            }
            buy = false;
            lore = config.getPurchaseSellLore();
        }

        return pane;
    }

    @Override
    protected List<Component> applyLore(@NotNull Player player, 
        @NotNull String shopName, int amount) {
        return getLore(player, shopName, Config.get().getShopLore(), amount);
    }

}
