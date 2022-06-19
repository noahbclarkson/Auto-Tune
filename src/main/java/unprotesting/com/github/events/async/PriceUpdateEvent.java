package unprotesting.com.github.events.async;

import lombok.Getter;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.objects.Shop;
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
      
    updateShops();
    Main.getInstance().getDb().updatePercentageChanges();
    Main.getInstance().getDb().getMaxPurchases().clear();

  }

  /**
   * Updates the prices of shops.
   */
  private void updateShops() {

    for (String item : Main.getInstance().getDb().getShops().keySet()) {

      Shop shop = Main.getInstance().getDb().getShops().get(item);
      double initialPrice = shop.getPrice();
      double[] sb = shop.loadAverageBuySellValue();
      double[] volatility = new double[]{shop.getMaxVolatility(), shop.getMinVolatility()};

      shop.createNewTimePeriod(UtilFunctions.calculateNewPrice(
          shop.getPrice(), volatility, sb[0], sb[1]));
      
      if (shop.getPrice() != initialPrice) {

        Main.getInstance().getLogger().info("Shop " + item + " price changed from " 
            + initialPrice + " to " + shop.getPrice());

      }
      
    }

  }


}
