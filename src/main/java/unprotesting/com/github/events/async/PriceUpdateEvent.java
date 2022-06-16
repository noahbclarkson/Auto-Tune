package unprotesting.com.github.events.async;

import java.util.Arrays;

import lombok.Getter;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.EnchantmentData;
import unprotesting.com.github.data.ephemeral.data.ItemData;
import unprotesting.com.github.data.persistent.Database;
import unprotesting.com.github.data.persistent.TimePeriod;
import unprotesting.com.github.util.UtilFunctions;

public class PriceUpdateEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Updates the prices.
   * @param isAsync Whether the event is being run async or not.
   */
  public PriceUpdateEvent(boolean isAsync) {

    super(isAsync);

    calculateAndLoad();

  }

  /**
   * Calculates the prices and loads them into the database.
   */
  private void calculateAndLoad() {

    int playerCount = UtilFunctions.calculatePlayerCount();

    // If the player count is less than "update-prices-threshold" then don't update the prices.
    if (playerCount < Config.getConfig().getUpdatePricesThreshold()) {

      Main.getInstance().getLogger().info("Player count is less than " 
          + Config.getConfig().getUpdatePricesThreshold()
          + " so not updating prices.");

      return;
    }

    Main.getInstance().getLogger().info("Calculating prices as player count is " + playerCount);
      
    Main.getInstance().updateTimePeriod();
    updateItems();
    updateEnchantments();
    Main.getInstance().getCache().updatePercentageChanges();

  }

  /**
   * Updates the prices of all items.
   */
  private void updateItems() {

    // Loop through all items and update them.
    for (String item : Main.getInstance().getCache().getItems().keySet()) {

      // If the item is locked then don't update it.
      if (Main.getInstance().getDataFiles().getShops().getConfigurationSection("shops")
          .getConfigurationSection(item).getBoolean("locked", false)) {

        continue;

      }
      
      ItemData data = Main.getInstance().getCache().getItems().get(item);
      double initialPrice = data.getPrice();
      double[] sb = loadAverageBuySellValue(item, false);
      double[] volatility = getMaxMinVolatility("shops", item);
      data.setPrice(UtilFunctions.calculateNewPrice(data.getPrice(), volatility, sb[0], sb[1]));
      Main.getInstance().getCache().getItems().put(item, data);

      if (data.getPrice() != initialPrice) {

        Main.getInstance().getLogger().info("Updated price of " 
            + item + " from " + initialPrice + " to " + data.getPrice());

      }

    }

  }

  /**
   * Updates the prices of all enchantments.
   */
  private void updateEnchantments() {

    // Loop through all enchantments and update them.
    for (String item : Main.getInstance().getCache().getEnchantments().keySet()) {

      // If the enchantment is locked then don't update it.
      if (Main.getInstance().getDataFiles().getEnchantments()
          .getConfigurationSection("enchantments." + item).getBoolean("locked", false)) {

        continue;

      }

      EnchantmentData data = Main.getInstance().getCache().getEnchantments().get(item);
      double[] sb = loadAverageBuySellValue(item, true);
      double[] volatility = getMaxMinVolatility("enchantments", item);
      data.setPrice(UtilFunctions.calculateNewPrice(data.getPrice(), volatility, sb[0], sb[1]));
      data.setRatio(UtilFunctions.calculateNewPrice(data.getRatio(), volatility, sb[0], sb[1]));
      Main.getInstance().getCache().getEnchantments().put(item, data);

    }

  }



  /**
   * Loads the average buy and sell values for an item.
   * @param item The item.
   * @param isEnchantment Whether the item is an enchantment or not.
   * @return The average buy and sell values.
   */
  private double[] loadAverageBuySellValue(String item, boolean isEnchantment) {

    Database db = Main.getInstance().getDatabase();
    int size = Main.getInstance().getDatabase().getMap().size();
    int y = 1;
    double finalBuys = 0;
    double finalSells = 0;

    // Loop through all time periods and calculate the average buy and sell values.
    for (int i = 0; i < size; i++) {

      y = (int) Math.round(Config.getConfig().getDataSelectionM() 
          * Math.pow(i, Config.getConfig().getDataSelectionZ()) 
          + Config.getConfig().getDataSelectionC());

      TimePeriod period = db.getMap().get(size - y);
      double buys = 0;
      double sells = 0;

      try {

        // If the item is an enchantment then load the enchantment data 
        // otherwise load the item data.
        if (isEnchantment) {

          int loc = Arrays.asList(period.getEnchantmentsTP().getItems()).indexOf(item);
          buys = period.getEnchantmentsTP().getBuys()[loc];
          sells = period.getEnchantmentsTP().getSells()[loc];

        } else {

          int loc = Arrays.asList(period.getItemTP().getItems()).indexOf(item);
          buys = period.getItemTP().getBuys()[loc];
          sells = period.getItemTP().getSells()[loc];

        }

      } catch (NullPointerException e) {
        
        Main.getInstance().getLogger().severe(
            "Error loading average buy and sell values for " + item);

        break;

      }

      finalBuys += buys;
      finalSells += sells;

    }

    double avBuy = finalBuys / y;
    double avSell = finalSells / y;
    return new double[] { avBuy, avSell };

  }

  /**
   * Gets the max and min volatility for an item/enchantment.
   * @param sectionName The section name. (shops or enchantments)
   * @param item The item/enchantment.
   * @return The max and min volatility.
   */
  private double[] getMaxMinVolatility(String sectionName, String item) {

    Double maxVolatility = Config.getConfig().getMaxVolatility();
    Double minVolatility = Config.getConfig().getMinVolatility();

    YamlConfiguration config = Main.getInstance().getDataFiles().getShops();

    if (sectionName.equals("enchantments")) {
      config = Main.getInstance().getDataFiles().getEnchantments();
    }

    maxVolatility = config.getConfigurationSection(
        sectionName).getConfigurationSection(item).getDouble("max-volatility", maxVolatility);

    minVolatility = config.getConfigurationSection(
        sectionName).getConfigurationSection(item).getDouble("min-volatility", minVolatility);

    double[] volatility = new double[2];
    volatility[0] = maxVolatility;
    volatility[1] = minVolatility;
    return volatility;

  }

}
