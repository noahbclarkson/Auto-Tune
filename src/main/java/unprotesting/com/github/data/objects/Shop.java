package unprotesting.com.github.data.objects;

import java.io.IOException;
import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

import org.bukkit.configuration.ConfigurationSection;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;


public class Shop implements Serializable {

  private static final long serialVersionUID = -6381163788906178955L;

  // History of buys for each time period.
  private int[] buys;
  // History of sells for each time period.
  private int[] sells;
  // History of prices for each time period.
  private double[] prices;
  // The size of the historical data.
  private int size;
  // Whether the item is an enchantment
  @Getter
  private final boolean enchantment;
  // Whether the item is locked
  // Not serialized
  private boolean locked;
  // Whether to use a custom sell price difference
  // Not serialized
  private double customSpd;
  // Whether to use a custom maxVolatility
  // Not serialized
  @Getter
  private double maxVolatility;
  // Whether to use a custom minVolatility
  // Not serialized
  @Getter
  private double minVolatility;
  // The percentage change in the last day
  // Not serialized
  @Getter
  private double change;
 

  /**
   * Constructor.
   */
  public Shop(ConfigurationSection config, boolean isEnchantment) {

    this.buys = new int[1];
    this.sells = new int[1];
    this.prices = new double[]{config.getDouble("price")};
    this.enchantment = isEnchantment;
    this.locked = config.getBoolean("locked", false);
    this.customSpd = config.getDouble("sell-price-difference", -1);
    this.maxVolatility = config.getDouble("max-volatility", Config.getConfig().getMaxVolatility());
    this.minVolatility = config.getDouble("min-volatility", Config.getConfig().getMinVolatility());
    this.size = 1;

  }

  /**
   * Constructor.
   * @param buys The buys.
   * @param sells The sells.
   * @param prices The prices.
   */
  public Shop(int[] buys, int[] sells, double[] prices, int size, boolean enchantment) {

    this.buys = buys;
    this.sells = sells;
    this.prices = prices;
    this.enchantment = enchantment;
    this.size = size;

  }

  /**
   * Load the non serialized data from the config.
   * @param config The config section.
   */
  public void loadConfiguration(ConfigurationSection config) {
      
    this.locked = config.getBoolean("locked", false);
    this.customSpd = config.getDouble("sell-price-difference", -1);
    this.maxVolatility = config.getDouble("max-volatility", Config.getConfig().getMaxVolatility());
    this.minVolatility = config.getDouble("min-volatility", Config.getConfig().getMinVolatility());
  
  }

  /**
   * Get buy count for latest time period.
   */
  public int getBuyCount() {
    return buys[size - 1];
  
  }

  /**
   * Get buy count for specified time period.
   * @param timePeriod The time period.
   */
  public int getBuyCount(int timePeriod) {
    return buys[timePeriod];
  }

  /**
   * Get sell count for latest time period.
   */
  public int getSellCount() {
    return sells[size - 1];
  }

  /**
   * Get sell count for specified time period.
   * @param timePeriod The time period.
   */
  public int getSellCount(int timePeriod) {
    return sells[timePeriod];
  }

  /**
   * Get price for latest time period.
   */
  public double getPrice() {
    return prices[size - 1];
  }

  /**
   * Get price for specified time period.
   * @param timePeriod The time period.
   */
  public double getPrice(int timePeriod) {
    return prices[timePeriod];
  }

  /**
   * Get the sell price.
   */
  public double getSellPrice() {
    Main.getInstance().getLogger().info("price: " + prices[size - 1]);
    Main.getInstance().getLogger().info("Spd: " + getSpd());
    return getPrice() - getPrice() * getSpd() * 0.01;
  }

  public void setPrice(Double price) {
    prices[size - 1] = price;
  }

  /**
   * Add to the latest buy count.
   * @param buyCount The additional buys.
   */
  public void addToBuyCount(int buyCount) {
    this.buys[size - 1] += buyCount;
  }

  /**
   * Add to the latest sell count.
   * @param sellCount The additional sells.
   */
  public void addToSellCount(int sellCount) {
    this.sells[size - 1] += sellCount;
  }

  /**
   * Get the sell price difference percentage for the shop.
   * @return The sell price difference percentage.
   */
  public double getSpd() {
    if (customSpd != -1) {
      return customSpd;
    }
    return Main.getInstance().getDb().getSpd();
  }

  /**
   * Create a new time period.
   */
  public void createNewTimePeriod(double newPrice) {

    if (locked) {
      return;
    }

    int initialSize = buys.length;
    int[] newBuys = new int[initialSize + 1];
    int[] newSells = new int[initialSize + 1];
    double[] newPrices = new double[initialSize + 1];

    for (int i = 0; i < buys.length; i++) {

      newBuys[i] = buys[i];
      newSells[i] = sells[i];
      newPrices[i] = prices[i];

    }

    newBuys[initialSize] = 0;
    newSells[initialSize] = 0;
    newPrices[initialSize] = newPrice;
    this.buys = newBuys;
    this.sells = newSells;
    this.prices = newPrices;
    this.size = initialSize + 1;

  }

  /**
   * Update the percentage change.
   * @param timePeriod The time period setting to use.
   */
  public void updatePercentageChanges(int timePeriod) {

    if (size < 2) {
      return;
    }

    int tpInDay = (int) Math.floor(1.0 / (timePeriod / 1440.0));
    int base = size - tpInDay > 0 ? size - tpInDay : 0;
    this.change = (prices[size - 1] - prices[base]) / prices[base] * 100;

  }

  /**
   * Loads the average buys and sells using the algorithm set in the config.
   */
  public double[] loadAverageBuySellValue() {

    int y = 1;
    double buy = 0;
    double sell = 0;

    for (int i = size - 1; i >= 0; i--) {

      y = (int) Math.round(Config.getConfig().getDataSelectionM() 
      * Math.pow(i, Config.getConfig().getDataSelectionZ()) 
      + Config.getConfig().getDataSelectionC());

      buy += buys[size - y];
      sell += sells[size - y];

    }

    return new double[]{buy, sell};

  }

  public static class ShopSerializer implements Serializer<Shop> {

    @Override
    public void serialize(DataOutput2 out, Shop value) throws IOException {
      
      out.writeInt(value.size);
      for (int i = 0; i < value.buys.length; i++) {
        out.writeInt(value.buys[i]);
      }
      for (int i = 0; i < value.sells.length; i++) {
        out.writeInt(value.sells[i]);
      }
      for (int i = 0; i < value.prices.length; i++) {
        out.writeDouble(value.prices[i]);
      }
      out.writeBoolean(value.enchantment);
    }

    @Override
    public Shop deserialize(DataInput2 input, int available) throws IOException {
      
      int size = input.readInt();
      int[] buys = new int[size];
      int[] sells = new int[size];
      double[] prices = new double[size];
      for (int i = 0; i < size; i++) {
        buys[i] = input.readInt();
      }
      for (int i = 0; i < size; i++) {
        sells[i] = input.readInt();
      }
      for (int i = 0; i < size; i++) {
        prices[i] = input.readDouble();
      }
      boolean enchantment = input.readBoolean();

      if (buys.length != size || sells.length != size || prices.length != size) {
        Main.getInstance().getLogger().severe("Shop deserialization error!");
        Main.getInstance().getLogger().severe("Shop size: " + size);
        Main.getInstance().getLogger().severe("Buys length: " + buys.length);
        Main.getInstance().getLogger().severe("Sells length: " + sells.length);
        Main.getInstance().getLogger().severe("Prices length: " + prices.length);
        return null;
      }

      return new Shop(buys, sells, prices, size, enchantment);
    }

  }


}

