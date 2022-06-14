package unprotesting.com.github.data.ephemeral.other;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Sale {

  private final String item;
  private int amount;

  /**
   * Initializes the sale.
   */
  public Sale(String item, int amount) {

    this.item = item;
    this.amount = amount;

  }

  public enum SalePositionType {
    BUY, SELL, EBUY, ESELL
  }

}
