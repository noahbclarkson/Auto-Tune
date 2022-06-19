package unprotesting.com.github.events.async;

import lombok.Getter;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

public class SellPriceDifferenceUpdateEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Updates the sell price difference.
   * @param isAsync Whether the event is being run async or not.
   */
  public SellPriceDifferenceUpdateEvent(boolean isAsync) {

    super(isAsync);

    // If "sell-price-difference-variation-enabled" is false
    // then don't update the sell price difference.
    if (Config.getConfig().isSellPriceDifferenceVariationEnabled()) {
      updateSellPriceDifference();
    }

  }

  private void updateSellPriceDifference() {

    double spd = Main.getInstance().getDb().getSpd();

    double fraction = Config.getConfig().getSellPriceVariationUpdatePeriod() 
        / Config.getConfig().getSellPriceVariationTimePeriod();

    double change = fraction * (Config.getConfig().getSellPriceDifferenceVariationStart()
        - Main.getInstance().getDataFiles().getConfig().getDouble("sell-price-difference", 10));

    double newSpd = spd - change;

    // If the new sell price difference is greater than the minimum then update it.
    if (newSpd > Config.getConfig().getSellPriceDifference()) {

      Main.getInstance().getDb().getEconomyData().get("SPD").addTimePeriod(newSpd);

      Main.getInstance().getLogger().info("Sell price difference changed from " 
          + spd + " to " + newSpd);
      
    }



  }

}
