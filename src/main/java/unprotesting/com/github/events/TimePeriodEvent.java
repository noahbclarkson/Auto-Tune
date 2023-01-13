package unprotesting.com.github.events;

import java.util.HashMap;

import org.bukkit.Bukkit;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.CsvHandler;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

/**
 * The event for updating item prices.
 */
public class TimePeriodEvent extends AutoTuneEvent {

    /**
     * Constructor for the TimePeriodEvent class.
     *
     * @param isAsync Whether to run the check in a separate thread.
     */
    public TimePeriodEvent(boolean isAsync) {
        super(isAsync);
        Config config = Config.get();
        AutoTuneLogger logger = Format.getLog();

        int players = Bukkit.getOnlinePlayers().size();

        if (players < config.getMinimumPlayers() && isAsync) {
            logger.config("Not enough players to start price update. ("
                    + players + " < " + config.getMinimumPlayers() + ")");
            resetRecentPurchases();
            return;
        }

        logger.config("Price update started as there are " + players + " players online.");
        updatePrices();
        Database.get().updateChanges();
        CsvHandler.writePriceData();
        // Database.get().updateRelations();
    }

    private void updatePrices() {
        AutoTuneLogger logger = Format.getLog();
        for (String s : ShopUtil.getShopNames()) {
            Shop shop = ShopUtil.getShop(s, true);
            double initialPrice = shop.getPrice();
            double strength = shop.strength();
            double newPrice = initialPrice + initialPrice * strength * shop.getVolatility() * 0.01;
            shop.timePeriod(newPrice);
            ShopUtil.putShop(s, shop);

            if (newPrice != initialPrice) {
                logger.config("Price of " + s + " changed from "
                        + Format.currency(initialPrice) + " to " + Format.currency(newPrice));
                logger.finer("Changed by " + Format.currency(newPrice - initialPrice));
                logger.finest("Volatility: " + shop.getVolatility());
                logger.finest("Strength: " + strength);
            }
        }
        AutoTuneInventoryCheckEvent.autosellItemMaxReached = new HashMap<>();
    }

    private void resetRecentPurchases() {
        for (String s : ShopUtil.getShopNames()) {
            Shop shop = ShopUtil.getShop(s, true);
            shop.clearRecentPurchases();
            ShopUtil.putShop(s, shop);
        }
        AutoTuneInventoryCheckEvent.autosellItemMaxReached = new HashMap<>();
    }
}
