package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.PurchaseUtil;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.Format;

import java.util.HashMap;

/**
 * The command for selling items.
 */
public class SellCommand implements CommandExecutor {

    public SellCommand(@NotNull AutoTune plugin) {
        plugin.getCommand("sell").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {

        if (sender instanceof Player) {
            return interpret((Player) sender);
        }

        Format.sendMessage(sender, "<red>You must be a player to use this command.");
        return true;
    }

    private boolean interpret(@NotNull Player player) {
        player.getOpenInventory().close();
        Config config = Config.get();
        boolean isSellLimits = config.isEnableSellLimits();
        ChestGui gui = new ChestGui(5, "Sell Panel");
        gui.setOnClose(event -> {
            for (ItemStack item : gui.getInventory().getContents()) {
                if (item == null) {
                    continue;
                }

                if (isSellLimits) {
                    int sellsLeft = ShopUtil.getSellsLeft(player, item.getType().toString().toLowerCase());
                    int itemAmount = item.getAmount();
                    int amountCantSell = itemAmount - sellsLeft;

                    if (amountCantSell > 0) {
                        // Handle player trying to sell more than they can
                        ItemStack itemCantSell = item.clone();
                        itemCantSell.setAmount(amountCantSell);

                        HashMap<Integer, ItemStack> failed = player.getInventory().addItem(itemCantSell);
                        if (!failed.isEmpty()) {
                            player.getWorld().dropItem(player.getLocation(), failed.get(0));
                        }

                        TagResolver resolver = PurchaseUtil.getTagResolver(item.displayName(), 0, amountCantSell, EconomyUtil.getEconomy().getBalance(player), null);
                        Format.sendMessage(player, config.getRunOutOfSells(), resolver);

                        item.setAmount(sellsLeft);
                    }
                }

                PurchaseUtil.sellItemStack(item, player);
            }
        });
        gui.show(player);
        return true;
    }

}
