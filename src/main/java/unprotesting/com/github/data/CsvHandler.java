package unprotesting.com.github.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import lombok.Cleanup;

import unprotesting.com.github.AutoTune;

public class CsvHandler {

  /**
   * Write all the price data points to a csv file.
   */
  public static void write() {
    try {
      writeCsv("web/data/data.csv");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void writeCsv(String filename) throws IOException {
    File file = new File(AutoTune.getInstance().getDataFolder() + "/" + filename);
    if (!file.exists()) {
      file.delete();
    }
    file.createNewFile();
    @Cleanup BufferedWriter writer = new BufferedWriter(new FileWriter(file));
    String[] shopNames = ShopUtil.getShopNames();
    // Sort shopNames alphabetically.
    Arrays.sort(shopNames);
    int size = shopNames.length;
    Shop[] shops = new Shop[size];
    for (int i = 0; i < size; i++) {
      shops[i] = ShopUtil.getShop(shopNames[i]);
    }
    for (int i = 0; i < size; i++) {
      if (i < size - 1) {
        writer.write(shopNames[i] + ",");
      } else {
        writer.write(shopNames[i]);
      }
    }
    writer.newLine();
    boolean dataStillPresent = true;
    int t = 0;
    while (dataStillPresent) {
      dataStillPresent = false;
      for (int i = 0; i < size; i++) {
        if (shops[i].size > t) {
          writer.write(shops[i].prices[t] + ",");
          dataStillPresent = true;
        }
      }
      if (dataStillPresent) {
        writer.newLine();
      }
      t++;
    }
  }

  
}
