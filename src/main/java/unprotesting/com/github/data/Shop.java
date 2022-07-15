package unprotesting.com.github.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.CollectFirst.CollectFirstSetting;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

/**
 * The class that represents a shop.
 */
@Builder
@AllArgsConstructor
public class Shop implements Serializable {

    private static final long serialVersionUID = -6381163788906178955L;

    private static double M = 0.05;
    private static double Z = 1.75;

    // History of buys for each time period.
    protected int[] buys;
    // History of sells for each time period.
    protected int[] sells;
    // History of prices for each time period.
    @Getter
    protected double[] prices;
    // The size of the historical data.
    @Getter
    protected int size;
    // Whether the item is an enchantment
    @Getter
    protected final boolean enchantment;
    // The collect first setting for this shop
    @Getter
    @Setter
    protected CollectFirst setting;
    // The autosell data for this shop
    @Getter
    protected Map<UUID, Integer> autosell;
    // The total buys for this shop
    @Getter
    protected int totalBuys;
    // The total sells for this shop
    @Getter
    protected int totalSells;
    // Whether the item is locked
    @Getter
    protected boolean locked;
    // Whether to use a custom sell price difference
    protected double customSpd;
    // Whether to use a custom maxVolatility
    @Getter
    protected double volatility;
    // Whether to use a custom volatility
    @Getter
    protected double change;
    // The maximum buys per time period
    @Getter
    protected int maxBuys;
    // The maximum sells per time period
    @Getter
    protected int maxSells;
    // The update rate of the item
    @Getter
    protected int updateRate;
    // The time since the last update
    @Getter
    protected int timeSinceUpdate;
    // The section this shop belongs to.
    @Getter
    protected String section;
    // The recent buys for this item
    @Getter
    protected Map<UUID, Integer> recentBuys;
    // The recent sells for this item
    @Getter
    protected Map<UUID, Integer> recentSells;

    /**
     * Constructor for the shop class.
     *
     * @param config        The configuration section for the shop.
     * @param isEnchantment Whether the item is an enchantment.
     */
    protected Shop(ConfigurationSection config, String sectionName, boolean isEnchantment) {
        this.buys = new int[1];
        this.sells = new int[1];
        this.prices = new double[]{config.getDouble("price")};
        this.enchantment = isEnchantment;
        this.size = 1;
        this.totalBuys = 0;
        this.totalSells = 0;
        this.autosell = new HashMap<UUID, Integer>();
        this.recentBuys = new HashMap<UUID, Integer>();
        this.recentSells = new HashMap<UUID, Integer>();
        this.setting = new CollectFirst(config.getString("collect-first", "server"));
        this.loadConfiguration(config, sectionName);
    }

    /**
     * Load the non serialized data from the config.
     *
     * @param config The config section.
     */
    protected void loadConfiguration(ConfigurationSection config, String sectionName) {
        AutoTuneLogger logger = Format.getLog();
        locked = config.getBoolean("locked", false);
        logger.finest("Locked: " + this.locked);
        customSpd = config.getDouble("sell-price-difference", -1);
        logger.finest("Custom SPD: " + this.customSpd);
        volatility = config.getDouble("volatility", Config.get().getVolatility());
        logger.finest("Volatility: " + this.volatility);
        section = sectionName;
        logger.finest("Section: " + this.section);
        maxBuys = config.getInt("max-buy", -1);
        logger.finest("Max Buys: " + this.maxBuys);
        maxSells = config.getInt("max-sell", -1);
        logger.finest("Max Sells: " + this.maxSells);
        updateRate = config.getInt("update-rate", 1);
        logger.finest("Update Rate: " + this.updateRate);
        double startPrice = config.getDouble("price");

        if (startPrice != prices[0]) {
            // Check if price in prices array
            boolean found = false;
            for (double price : prices) {
                if (price == startPrice) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                prices[size - 1] = startPrice;
                logger.info("Price changed for " + section + " to " + startPrice
                        + " because the price was changed in the config.");
            }
        }

        if (section == null) {
            logger.warning("Shop " + config.getName() + " was loaded with no section!");
        }
    }

    /**
     * Get the buy count for the last time period.
     *
     * @return The buy count.
     */
    public int getBuyCount() {
        return buys[size - 1];
    }

    /**
     * Get the sell count for the last time period.
     *
     * @return The sell count.
     */
    public int getSellCount() {
        return sells[size - 1];
    }

    /**
     * Get the price for the last time period.
     *
     * @return The price.
     */
    public double getPrice() {
        return prices[size - 1];
    }

    /**
     * Set the price for the last time period.
     *
     * @param price The price.
     */
    public void setPrice(double price) {
        prices[size - 1] = price;
    }

    /**
     * Get the sell price.
     */
    public double getSellPrice() {
        return getPrice() - getPrice() * getSpd() * 0.01;
    }

    /**
     * Add to the latest buy count.
     *
     * @param buyCount The additional buys.
     */
    public void addBuys(UUID player, int buyCount) {
        AutoTuneLogger logger = Format.getLog();
        if (recentBuys.containsKey(player)) {
            recentBuys.merge(player, buyCount, Integer::sum);
            logger.finest("Recent buys: " + recentBuys.get(player));
        } else {
            recentBuys.put(player, buyCount);
            logger.finest("New recent buys: " + recentBuys.get(player));
        }
        this.buys[size - 1] = buyCount + buys[size - 1];
        logger.finer("Increased buys by " + buyCount + " to " + buys[size - 1]);
        logger.finest("Updated at time period " + (size - 1));
    }

    /**
     * Add to the latest sell count.
     *
     * @param sellCount The additional sells.
     */
    public void addSells(UUID player, int sellCount) {
        AutoTuneLogger logger = Format.getLog();
        if (recentSells.containsKey(player)) {
            recentSells.merge(player, sellCount, Integer::sum);
            logger.finest("Recent sells: " + recentSells.get(player));
        } else {
            recentSells.put(player, sellCount);
            logger.finest("New recent sells: " + recentSells.get(player));
        }
        this.sells[size - 1] = sellCount + sells[size - 1];
        logger.finer("Increased sells by " + sellCount + " to " + sells[size - 1]);
        logger.finest("Updated at time period " + (size - 1));
    }

    /**
     * Clear the autosell data.
     */
    public void clearAutosell() {
        autosell.clear();
    }

    /**
     * Increase the autosell count for a uuid.
     *
     * @param uuid  The uuid.
     * @param count The count.
     */
    public void addAutosell(UUID uuid, int count) {
        AutoTuneLogger logger = Format.getLog();
        if (autosell.containsKey(uuid)) {
            autosell.merge(uuid, count, Integer::sum);
            logger.finest("Autosell: " + autosell.get(uuid) + " for " + uuid);
        } else {
            autosell.put(uuid, count);
            logger.finest("New autosell: " + autosell.get(uuid) + " for " + uuid);
        }
    }

    /**
     * Calculates if a given player has unlocked this item.
     *
     * @return Whether the player has unlocked this item.
     */
    public boolean isUnlocked(UUID player) {
        if (setting.getSetting().equals(CollectFirstSetting.SERVER)) {
            return setting.isFoundInServer();
        } else if (setting.getSetting().equals(CollectFirstSetting.PLAYER)) {
            return setting.playerFound(player);
        } else {
            return true;
        }
    }

    private double getSpd() {
        if (customSpd != -1) {
            return customSpd;
        }
        return Config.get().getSellPriceDifference();
    }

    /**
     * Create a new time period for the shop.
     *
     * @param price The new price for the time period.
     */
    public void timePeriod(double price) {
        int[] newBuys = new int[size + 1];
        int[] newSells = new int[size + 1];
        double[] newPrices = new double[size + 1];

        this.totalBuys += buys[size - 1];
        this.totalSells += sells[size - 1];

        for (int i = 0; i < size; i++) {
            newBuys[i] = buys[i];
            newSells[i] = sells[i];
            newPrices[i] = prices[i];
        }

        newBuys[size] = 0;
        newSells[size] = 0;
        double newPrice = prices[size - 1];

        if (!locked && updateRate > 0) {
            if (timeSinceUpdate >= updateRate) {
                newPrice = price;
                this.timeSinceUpdate = 0;
                this.recentBuys.clear();
                this.recentSells.clear();
            }
            this.timeSinceUpdate++;
        }

        newPrices[size] = newPrice;
        this.buys = newBuys;
        this.sells = newSells;
        this.prices = newPrices;
        this.size++;
    }

    /**
     * Update the percentage change for the shop.
     */
    protected void updateChange() {
        if (locked || size < 2) {
            return;
        }

        int dailyTimePeriods = (int) Math.floor(1f / (Config.get().getTimePeriod() / 1440f));
        int start = size - dailyTimePeriods > 0 ? size - dailyTimePeriods : 0;
        this.change = (prices[size - 1] - prices[start]) / prices[start];
    }

    /**
     * Loads the buy vs sell strength for the shop.
     */
    public double strength() {
        int x = 0;
        int y = 1;
        double buy = 0;
        double sell = 0;

        while (y <= size) {
            buy += buys[size - y];
            sell += sells[size - y];
            x++;
            y = (int) Math.round(M * Math.pow(x, Z) + 0.5);
        }

        if (buy == 0 && sell == 0) {
            return 0;
        }

        return (buy - sell) / (buy + sell);
    }

    /**
     * Get the display name for this shop.
     */
    protected static Component getDisplayName(String name, boolean isEnchantment) {
        name = name.toLowerCase();

        if (isEnchantment) {
            return Enchantment.getByKey(NamespacedKey.minecraft(name)).displayName(1);
        }

        return new ItemStack(Material.matchMaterial(name)).displayName();
    }

}
