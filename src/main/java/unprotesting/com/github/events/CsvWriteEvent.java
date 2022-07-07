package unprotesting.com.github.events;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import lombok.Cleanup;
import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;

/**
 * The event for writing price data to a CSV file.
 */
public class CsvWriteEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Write the price data for all items to a CSV file.
   *
   * @param isAsync Whether the event is being run async or not.
   */
  public CsvWriteEvent(boolean isAsync) {
    super(isAsync);
    try {
      Format.getLog().config("Writing price data to CSV file.");
      writeCsv();
      Format.getLog().config("Price data written to data.csv");
    } catch (IOException e) {
      Format.getLog().severe("Could not write data to csv file.");
      e.printStackTrace();
    }
  }

  private static void writeCsv() throws IOException {
    File dataFolder = new File(AutoTune.getInstance().getDataFolder(), "/web/data");
    if (!dataFolder.exists()) {
      dataFolder.mkdirs();
    }
    File file = new File(AutoTune.getInstance().getDataFolder() + "/web/data/data.csv");
    if (!file.exists()) {
      file.delete();
    }
    file.createNewFile();
    @Cleanup
    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
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
        if (shops[i].getSize() > t) {
          writer.write(shops[i].getPrices()[t] + ",");
          dataStillPresent = true;
        } else {
          writer.write(",");
        }
      }
      if (dataStillPresent) {
        writer.newLine();
      }
      t++;
    }
  }

}
