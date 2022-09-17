package unprotesting.com.github.events;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.EconomyDataUtil;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.data.Transaction;
import unprotesting.com.github.data.Transaction.TransactionType;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.Format;

/**
 * The event for sending a player their money from items they have auto-sold.
 */
public class AutosellProfitEvent extends AutoTuneEvent {

    /**
     * Updates the autosell profit.
     *
     * @param isAsync Whether the event is being run async or not.
     */
    public AutosellProfitEvent(boolean isAsync) {
        super(isAsync);
        deposit();
    }

    private void deposit() {
        for (String s : ShopUtil.getShopNames()) {
            Shop shop = ShopUtil.getShop(s);
            Map<UUID, Integer> autosell = shop.getAutosell();

            if (autosell.isEmpty()) {
                continue;
            }

            for (Map.Entry<UUID, Integer> entry : autosell.entrySet()) {

                if (entry.getValue() <= 0) {
                    continue;
                }

                int amount = entry.getValue();
                double price = shop.getSellPrice();
                double total = price * amount;
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                EconomyUtil.getEconomy().depositPlayer(player, total);
                ShopUtil.addTransaction(new Transaction(
                        price, amount, entry.getKey(), s, TransactionType.SELL));
                EconomyDataUtil.increaseEconomyData("GDP", total / 2);
                double loss = shop.getPrice() * amount - total;
                EconomyDataUtil.increaseEconomyData("LOSS", loss);
                String balance = Format.currency(EconomyUtil.getEconomy().getBalance(player));

                TagResolver resolver = TagResolver.resolver(
                        Placeholder.parsed("total", Format.currency(total)),
                        Placeholder.parsed("balance", balance));

                if (player.isOnline()) {
                    Format.sendMessage(Objects.requireNonNull(player.getPlayer()),
                            Config.get().getAutosellProfit(), resolver);
                }

            }

            shop.clearAutosell();
            ShopUtil.putShop(s, shop);
        }
    }

}
