package unprotesting.com.github.events;

import lombok.Getter;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;
import unprotesting.com.github.util.UtilFunctions;

public class TimePeriodEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Constructor for the TimePeriodEvent class.
   * @param isAsync Whether to run the check in a separate thread.
   */
  public TimePeriodEvent(boolean isAsync) {
    super(isAsync);
    int players = UtilFunctions.calculatePlayerCount();

    if (players < Config.get().getMinimumPlayers()) {
      Format.getLog().config("Not enough players to start price update. (" 
          + players + " < " + Config.get().getMinimumPlayers() + ")");
      return;
    }

    Format.getLog().config("Price update started as there are " + players + " players online.");
    updatePrices();
    Database.get().updateChanges();
    Database.get().updateRelations();
  }

  private void updatePrices() {
    
    for (String s : ShopUtil.getShopNames()) {
      Shop shop = ShopUtil.getShop(s);
      double initialPrice = shop.getPrice();
      double strength = shop.strength();
      double newPrice = initialPrice + initialPrice * strength * shop.getVolatility() * 0.01;
      shop.timePeriod(newPrice);
      ShopUtil.putShop(s, shop);

      if (newPrice != initialPrice) {
        Format.getLog().config("Price of " + s + " changed from " 
            + Format.currency(initialPrice) + " to " + Format.currency(newPrice));
        Format.getLog().finer("Changed by " + Format.currency(newPrice - initialPrice));
        Format.getLog().finest("Volatility: " + shop.getVolatility());
        Format.getLog().finest("Strength: " + strength);
      }
    }
  }
}
