package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;
import lombok.Setter;

@Getter
public class ItemData {

  
  private int buys;
  private int sells;
  @Setter
  private double price;

  /**
   * Initializes the item data.
   * @param price The price of the item.
   */
  public ItemData(double price) {

    this.buys = 0;
    this.sells = 0;
    this.price = price;

  }

  /**
   * Increases the buy amount.
   * @param amount The amount to increase by.
   */
  public void increaseBuys(int amount) {
    buys = buys + amount;
  }

  /**
   * Increases the sell amount.
   * @param amount The amount to increase by.
   */
  public void increaseSells(int amount) {
    sells = sells + amount;
  }

}
