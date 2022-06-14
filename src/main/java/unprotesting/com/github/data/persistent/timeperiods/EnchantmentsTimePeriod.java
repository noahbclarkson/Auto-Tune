package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.LocalDataCache;
import unprotesting.com.github.data.ephemeral.data.EnchantmentData;
import unprotesting.com.github.data.util.BuyableTimePeriodFunctions;

//  Enchantment time period object for storing enchantment price, ratio and buy/sell data 

@Getter
public class EnchantmentsTimePeriod extends BuyableTimePeriodFunctions implements Serializable {

  private static final long serialVersionUID = -1102531405L;

  @Setter
  private int[] buys;
  @Setter
  private int[] sells;
  private double[] prices;
  private double[] ratios;
  private String[] items;

  /**
   * Initializes the enchantment time period.
   */
  public EnchantmentsTimePeriod() {
    Set<String> set = Main.getInstance().getCache().getEnchantments().keySet();
    int size = set.size();
    init(size);
    this.ratios = new double[size];
    this.prices = new double[size];
    this.buys = new int[size];
    this.sells = new int[size];
    this.items = new String[size];
    int i = 0;
    LocalDataCache cache = Main.getInstance().getCache();

    for (String key : set) {
      EnchantmentData data = cache.getEnchantments().get(key);
      this.items[i] = key;
      setVars(i, data);
      i++;
    }
    
  }

  /**
   * Set the variables for the time period.
   * @param pos The index of the time period.
   * @param data The enchantment data.
   */
  private void setVars(int pos, EnchantmentData data) {

    buys[pos] = data.getBuys();
    sells[pos] = data.getSells();
    prices[pos] = data.getPrice();
    ratios[pos] = data.getRatio();

  }

}
