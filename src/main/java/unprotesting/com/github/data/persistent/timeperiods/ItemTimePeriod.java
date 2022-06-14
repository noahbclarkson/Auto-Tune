package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.LocalDataCache;
import unprotesting.com.github.data.ephemeral.data.ItemData;
import unprotesting.com.github.data.util.BuyableTimePeriodFunctions;

@Getter
public class ItemTimePeriod extends BuyableTimePeriodFunctions implements Serializable {

  private static final long serialVersionUID = -1102531407L;

  @Setter
  private int[] buys;
  @Setter
  private int[] sells;
  private double[] prices;
  private String[] items;

  /**
   * Initializes the item time period.
   */
  public ItemTimePeriod() {

    Set<String> set = Main.getInstance().getCache().getItems().keySet();
    int size = set.size();
    init(size);
    this.buys = new int[size];
    this.sells = new int[size];
    this.prices = new double[size];
    this.items = new String[size];
    int i = 0;
    LocalDataCache cache = Main.getInstance().getCache();

    for (String key : set) {

      ItemData data = cache.getItems().get(key);
      this.items[i] = key;
      setVars(i, data);
      i++;
      
    }

  }

  /**
   * Set the variables for the time period.
   * @param pos The index of the time period.
   * @param data The item data.
   */
  private void setVars(int pos, ItemData data) {
    buys[pos] = data.getBuys();
    sells[pos] = data.getSells();
    prices[pos] = data.getPrice();
  }

}
