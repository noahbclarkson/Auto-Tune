package unprotesting.com.github.data;

import lombok.experimental.UtilityClass;
import org.bukkit.OfflinePlayer;
import unprotesting.com.github.config.Config;

/**
 * A utility class for interacting with shops and the database.
 */
@UtilityClass
public class ShopUtil {

    private static String[] sectionNameCache;
    private static String[] shopNameCache;

    public Shop getShop(String item) {
        return Database.get().getShop(item);
    }

    public void putShop(String key, Shop shop) {
        Database.get().putShop(key, shop);
    }

    /**
     * Get the list of possible shop names.
     *
     * @return The list of possible shop names.
     */
    public String[] getShopNames() {
        if (shopNameCache == null) {
            shopNameCache = Database.get().getShopNames();
        }
        return shopNameCache;
    }

    /**
     * Get the list of possible section names.
     *
     * @return The list of possible section names.
     */
    public String[] getSectionNames() {
        if (sectionNameCache == null) {
            sectionNameCache = Database.get().sections.keySet().toArray(new String[0]);
        }
        return sectionNameCache;
    }

    /**
     * Get a section of the shop.
     *
     * @param name The name of the section.
     * @return The section.
     */
    public Section getSection(String name) {
        if (Database.get().sections.containsKey(name)) {
            return Database.get().sections.get(name);
        }

        for (String sectionName : getSectionNames()) {
            if (sectionName.equalsIgnoreCase(name)) {
                return Database.get().sections.get(sectionName);
            }
        }

        return null;
    }

    public int getBuysLeft(OfflinePlayer player, String item) {
        return Database.get().getPurchasesLeft(item, player.getUniqueId(), true);
    }

    public int getSellsLeft(OfflinePlayer player, String item) {
        return Database.get().getPurchasesLeft(item, player.getUniqueId(), false);
    }

    public void addTransaction(Transaction transaction) {
        Database.get().transactions.put(System.currentTimeMillis(), transaction);
    }

    public boolean removeShop(String item) {
        return Database.get().removeShop(item);
    }

    public void reload() {
        Config.init();
        Database.get().reload();
    }

}
