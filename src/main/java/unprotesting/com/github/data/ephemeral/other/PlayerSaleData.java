package unprotesting.com.github.data.ephemeral.other;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;

@Getter
public class PlayerSaleData {

  private List<Sale> buys;
  private List<Sale> sells;
  private List<Sale> buysE;
  private List<Sale> sellsE;

  /**
   * Initializes the player sale data.
   */
  public PlayerSaleData() {

    this.buys = new ArrayList<Sale>();
    this.sells = new ArrayList<Sale>();
    this.buysE = new ArrayList<Sale>();
    this.sellsE = new ArrayList<Sale>();

  }

  /**
   * Add a new sale to one of the sale lists.
   * @param item The item name.
   * @param amount The amount of the item.
   * @param position The position of the sale.
   */
  public void addSale(String item, int amount, SalePositionType position) {

    Sale sale = checkIfSaleExists(item, position);
    sale.setAmount(sale.getAmount() + amount);

    switch (position) {
      case BUY:
        buys.add(sale);
        break;
      case SELL:
        sells.add(sale);
        break;
      case EBUY:
        buysE.add(sale);
        break;
      case ESELL:
        sellsE.add(sale);
        break;
      default:
        break;
    }

  }

  // Ensure a sale object for an item doesn't already exist in a select list
  private Sale checkIfSaleExists(String item, SalePositionType position) {

    switch (position) {
      case BUY:
        Sale sale = getSale(item, buys);
        buys.remove(sale);
        return sale;
      case SELL:
        Sale sale2 = getSale(item, sells);
        sells.remove(sale2);
        return sale2;
      case EBUY:
        Sale sale3 = getSale(item, buysE);
        buysE.remove(sale3);
        return sale3;
      case ESELL:
        Sale sale4 = getSale(item, sellsE);
        sellsE.remove(sale4);
        return sale4;
      default:
        return new Sale(item, 0);
    }

  }

  private Sale getSale(String item, List<Sale> sales) {

    // Loop through sales and return the sale if it exists.
    for (Sale sale : sales) {

      // If the sale exists, return it.
      if (sale.getItem().equals(item)) {
        return sale;
      }

    }

    return new Sale(item, 0);

  }

}
