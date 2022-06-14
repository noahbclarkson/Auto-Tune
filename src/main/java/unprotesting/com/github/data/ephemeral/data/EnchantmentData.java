package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;
import lombok.Setter;

@Getter
public class EnchantmentData {

  private int buys;
  private int sells;
  @Setter
  private double price;
  @Setter
  private double ratio;

  /**
   * Initializes the enchantment data.
   */
  public EnchantmentData(double price, double ratio) {

    this.buys = 0;
    this.sells = 0;
    this.price = price;
    this.ratio = ratio;

  }

  /**
   * Increases the buy amount.
   * @param amount The amount to increase by.
   */
  public void increaseBuys(int amount) {
    buys += amount;
  }

  /**
   * Increases the sell amount.
   * @param amount The amount to increase by.
   */
  public void increaseSells(int amount) {
    sells += amount;
  }

}
