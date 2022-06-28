package unprotesting.com.github.data;

import org.bukkit.OfflinePlayer;

public class ShopUtil {

  protected static String[] sectionNameCache;
  protected static String[] shopNameCache;

  public static Shop getShop(String item) {
    return Database.get().getShop(item);
  }

  public static void putShop(String key, Shop shop) {
    Database.get().putShop(key, shop);
  }

  /**
   * Get the list of possible shop names.
   * @return The list of possible shop names.
   */
  public static String[] getShopNames() {
    if (shopNameCache == null) {
      shopNameCache = Database.get().getShopNames();
    }
    return shopNameCache;
  }

  /**
   * Get the list of possible section names.
   * @return The list of possible section names.
   */
  public static String[] getSectionNames() {
    if (sectionNameCache == null) {
      sectionNameCache = Database.get().sections.keySet().toArray(new String[0]);
    }
    return sectionNameCache;
  }

  /**
   * Get a section of the shop.
   * @param name The name of the section.
   * @return The section.
   */
  public static Section getSection(String name) {

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

  public static int getBuysLeft(OfflinePlayer player, String item) {
    return Database.get().getPurchasesLeft(item, player.getUniqueId(), true);
  }

  public static int getSellsLeft(OfflinePlayer player, String item) {
    return Database.get().getPurchasesLeft(item, player.getUniqueId(), false);
  }

  public static void addTransaction(Transaction transaction) {
    Database.get().transactions.put(System.currentTimeMillis(), transaction);
  }

  
}
