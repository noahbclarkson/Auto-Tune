package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;

@Getter
public class MaxBuySellData {

  private int buys;
  private int sells;

  /**
   * Initializes the max buy sell data.
   * @param buys The amount of buys.
   * @param sells The amount of sells.
   */
  public MaxBuySellData(int buys, int sells) {

    this.buys = buys;
    this.sells = sells;
    
  }

}
