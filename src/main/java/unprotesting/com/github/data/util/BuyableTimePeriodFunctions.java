package unprotesting.com.github.data.util;

import lombok.Getter;

public abstract class BuyableTimePeriodFunctions {

  @Getter
  private int[] buys;
  @Getter
  private int[] sells;
  @Getter
  private double[] prices;
  @Getter
  private String[] items;

  /**
   * Initializes the buyable time period functions.
   * @param size The size of the buyable time period.
   */
  public void init(int size) {
    this.buys = new int[size];
    this.sells = new int[size];
    this.prices = new double[size];
    this.items = new String[size];
  }

}
