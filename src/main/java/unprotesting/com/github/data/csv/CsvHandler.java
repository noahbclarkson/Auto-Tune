package unprotesting.com.github.data.csv;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
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
      write("trade", 0);
    } catch (IOException e) {
      Main.getInstance().getLogger().severe("Could not write to csv file.");
    }

  }

  /**
   * Creates a CSV file with all price, buy and sell data to be read by the webpage.
   * @param csvName The name of the csv file.
   * @param cutoffValue The cutoff value for the data.
   * @throws IOException If the file could not be created.
   */
  private static void write(String csvName, int cutoffValue) throws IOException {

    int cutoff = cutoffValue;
    FileWriter writer = new FileWriter("plugins/Auto-Tune/web/" + csvName + ".csv");
    int size = Main.getInstance().getDatabase().getMap().size();
    TimePeriod tp = Main.getInstance().getDatabase().getMap().get(size - 1);
    List<String> items = Arrays.asList(tp.getItemTP().getItems());
    List<String> enchantments = Arrays.asList(tp.getEnchantmentsTP().getItems());

    if (size < cutoff || cutoff < 3) {
      cutoff = size;
    }

    Collections.sort(items);
    Collections.sort(enchantments);

    for (String item : items) {

      writer.write("\n" + "%" + item + "\n");

      for (int i = (size - cutoff); i < size; i++) {

        ItemTimePeriod itp = Main.getInstance().getDatabase().getMap().get(i).getItemTP();
        int pos = Arrays.asList(itp.getItems()).indexOf(item);

        writer.append(i + "," + itp.getPrices()[pos] + "," 
            + itp.getBuys()[pos] + "," + itp.getSells()[pos] + "\n");

      }

    }

    if (Config.getConfig().isEnableEnchantments()) {

      for (String enchantment : enchantments) {

        writer.write("\n" + "%" + enchantment + "\n");

        for (int i = (size - cutoff); i < size; i++) {

          EnchantmentsTimePeriod etp = Main.getInstance().getDatabase()
              .getMap().get(i).getEnchantmentsTP();

          int pos = Arrays.asList(etp.getItems()).indexOf(enchantment);

          writer.append(i + "," + etp.getPrices()[pos] + "," 
              + etp.getBuys()[pos] + "," + etp.getSells()[pos] + "\n");

        }

      }

    }

    for (int k = 0; k < 5; k++) {

      if (k == 0) {
        writer.write("\n" + "%GDP" + "\n");
      } else if (k == 1) {
        writer.write("\n" + "%Balance" + "\n");
      } else if (k == 2) {
        writer.write("\n" + "%Debt" + "\n");
      } else if (k == 3) {
        writer.write("\n" + "%Loss" + "\n");
      } else if (k == 4) {
        writer.write("\n" + "%Inflation" + "\n");
      }

      for (int i = (size - cutoff); i < size; i++) {

        GdpTimePeriod gtp = Main.getInstance().getDatabase().getMap().get(i).getGdpTP();

        if (k == 0) {
          writer.write(i + "," + gtp.getGdp() + "\n");
        } else if (k == 1) {
          writer.write(i + "," + gtp.getBalance() + "\n");
        } else if (k == 2) {
          writer.write(i + "," + gtp.getDebt() + "\n");
        } else if (k == 3) {
          writer.write(i + "," + gtp.getLoss() + "\n");
        } else if (k == 4) {
          writer.write(i + "," + gtp.getInflation() + "\n");
        }

      }

    }

    writer.close();

  }

}
