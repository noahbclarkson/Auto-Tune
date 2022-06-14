package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;
import unprotesting.com.github.config.Config;

public class EconomyInfoData {

  @Getter
  private double sellPriceDifference;

  /**
   * Initializes the economy info data.
   */
  public EconomyInfoData(double sellPriceDifference) {
    this.sellPriceDifference = sellPriceDifference;
    Config.getConfig().setSellPriceDifference(this.sellPriceDifference);
  }

  /**
   * Updates the sell price difference.
   * @param newSpd The new sell price difference.
   */
  public void updateSellPriceDifference(double newSpd) {
    sellPriceDifference = newSpd;
    Config.getConfig().setSellPriceDifference(newSpd);
  }

}
