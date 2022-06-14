package unprotesting.com.github.data.persistent;

import lombok.Getter;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DBMaker.Maker;

import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.persistent.timeperiods.EconomyInfoTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.GdpTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.ItemTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.LoanTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.TransactionsTimePeriod;

@Getter
public class Database {

  private DB db;
  private HTreeMap<Integer, TimePeriod> map;

  /**
   * Initializes the database.
   */
  @SuppressWarnings("unchecked")
  public Database() {

    createDB(Config.getConfig().getDataLocation());
    this.map = db.hashMap("map", Serializer.INTEGER, Serializer.JAVA).createOrOpen();

  }

  /**
   * Initializes the database in the specified location.
   * @param location The location of the database.
   */
  @SuppressWarnings("unchecked")
  public Database(String location) {

    createDB(location);
    this.map = db.hashMap("map", Serializer.INTEGER, Serializer.JAVA).createOrOpen();

  }

  // Method to build and create or link database to file
  /**
   * Build and create or link database to file.
   * @param location The location of the database.
   */
  private void createDB(String location) {

    Maker maker = DBMaker.fileDB(location + "data.db");

    db = maker.checksumHeaderBypass()
        .fileMmapEnableIfSupported()
        .fileMmapPreclearDisable()
        .cleanerHackEnable()
        .allocateStartSize(10 * 1024 * 1024) // 25MB
        .allocateIncrement(5 * 1024 * 1024) // 5MB
        .closeOnJvmShutdown().make();

    db.getStore().fileLoad();

  }

  /**
   * Save the cache to the database.
   */
  public void saveCacheToLastTP() {

    int size = map.size() - 1;

    if (size < 0) {
      return;
    }

    TimePeriod tp = map.get(size);
    ItemTimePeriod itemTP = tp.getItemTP();
    String[] items = itemTP.getItems();
    int[] buys = itemTP.getBuys();
    int[] sells = itemTP.getSells();
    int pos = 0;

    // Loop through all items and save the buy and sell values.
    for (String item : items) {
      buys[pos] = buys[pos] + Main.getInstance().getCache().getItems().get(item).getBuys();
      sells[pos] = sells[pos] + Main.getInstance().getCache().getItems().get(item).getSells();
      itemTP.setBuys(buys);
      itemTP.setSells(sells);
      pos++;
    }

    EnchantmentsTimePeriod enchantmentsTP = tp.getEnchantmentsTP();
    String[] enchantments = enchantmentsTP.getItems();
    int[] enchantmentBuys = enchantmentsTP.getBuys();
    int[] enchantmentSells = enchantmentsTP.getSells();
    pos = 0;

    // Loop through all enchantments and save the buy and sell values.
    for (String enchantment : enchantments) {

      enchantmentBuys[pos] = enchantmentBuys[pos] 
        + Main.getInstance().getCache().getEnchantments().get(enchantment).getBuys();
        
      enchantmentSells[pos] = enchantmentSells[pos] 
        + Main.getInstance().getCache().getEnchantments().get(enchantment).getSells();

      enchantmentsTP.setBuys(enchantmentBuys);
      enchantmentsTP.setSells(enchantmentSells);
      pos++;

    }

    tp.setItemTP(itemTP);
    tp.setEnchantmentsTP(enchantmentsTP);
    tp.setGdpTP(new GdpTimePeriod());
    tp.setLoanTP(new LoanTimePeriod());
    tp.setTransactionsTP(new TransactionsTimePeriod());
    tp.setEconomyInfoTP(new EconomyInfoTimePeriod());
    map.put(map.size() - 1, tp);

  }

}
