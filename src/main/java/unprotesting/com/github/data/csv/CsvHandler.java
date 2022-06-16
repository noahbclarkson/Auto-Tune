package unprotesting.com.github.data.csv;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.persistent.TimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.GdpTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.ItemTimePeriod;

public class CsvHandler {

  /**
   * Creates a CSV file with all price, buy and sell data to be read by the webpage.
   */
  public static void writeCsv() {

    try {
      write("trade.csv", 0);
    } catch (IOException e) {
      Main.getInstance().getLogger().severe("Could not write to csv file.");
    }

  }

  /**
   * Write the latest x rows of price data.
   * @param fileName The name of the file to write to.
   * @param rows The number of rows to write.
   */
  private static void write(String fileName, int rows) throws IOException {
    FileWriter fileStream = new FileWriter(Config.getConfig().getDataLocation() + fileName);
    BufferedWriter writer = new BufferedWriter(fileStream);
    int size = Main.getInstance().getDatabase().getMap().size();
    TimePeriod tp = Main.getInstance().getDatabase().getMap().get(size - 1);
    List<String> items = Arrays.asList(tp.getItemTP().getItems());
    List<String> enchantments = Arrays.asList(tp.getEnchantmentsTP().getItems());
    writer.write("GDP,Balance,Debt,Loss,Inflation");

    if (rows == -1) {
      rows = size;
    }
    
    for (String item : items) {
      writer.write(item + ",");
    }

    if (Config.getConfig().isEnableEnchantments()) {
      for (String enchantment : enchantments) {
        writer.write(enchantment.toUpperCase() + ",");
      }
    }

    writer.write("\n");

    for (int i = (size - rows); i < size; i++) {
      GdpTimePeriod gtp = Main.getInstance().getDatabase().getMap().get(i).getGdpTP();

      writer.write(gtp.getGdp() + "," 
          + gtp.getBalance() + ","
          + gtp.getDebt() + ","
          + gtp.getLoss() + ","
          + gtp.getInflation());

      ItemTimePeriod itp = Main.getInstance().getDatabase().getMap().get(i).getItemTP();

      EnchantmentsTimePeriod etp = Main.getInstance()
          .getDatabase().getMap().get(i).getEnchantmentsTP();

      if (itp == null) {
        continue;
      }

      for (int j = 0; j < itp.getItems().length; j++) {
        writer.write("," + itp.getPrices()[j]);
      }

      if (Config.getConfig().isEnableEnchantments()) {

        for (int j = 0; j < etp.getItems().length; j++) {
          writer.write("," + etp.getPrices()[j]);
        }

      }

    }

    writer.close();
    
  }

}
