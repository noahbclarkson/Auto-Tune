package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;

@Getter
public class EconomyInfoTimePeriod implements Serializable {

  private static final long serialVersionUID = -1102531404L;

  private double sellPriceDifference;

  /**
   * Initializes the economy info time period.
   */
  public EconomyInfoTimePeriod() {

    this.sellPriceDifference = Main.getInstance().getCache()
      .getEconomyInfo().getSellPriceDifference();

  }

}
