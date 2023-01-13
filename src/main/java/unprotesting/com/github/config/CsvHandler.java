package unprotesting.com.github.config;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import lombok.Cleanup;
import org.bukkit.Bukkit;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

/**
 * The utility class for writing price data to a CSV file.
 */
public class CsvHandler {

    /**
     * Write the price data for all items to a CSV file.
     */
    public static void writePriceData() {
        if (!Config.get().isWebServer()) return;
        Bukkit.getScheduler().runTaskAsynchronously(AutoTune.getInstance(), () -> {
            AutoTuneLogger logger = Format.getLog();
            try {
                logger.config("Writing price data to CSV file.");
                writeCsv();
                logger.config("Price data written to data.csv");
            } catch (IOException e) {
                logger.severe("Could not write data to csv file.");
                logger.config(e.toString());
            }
        });
    }

    private static void writeCsv() throws IOException {
        AutoTune instance = AutoTune.getInstance();
        File dataFolder = new File(instance.getDataFolder(), "/web/data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File file = new File(instance.getDataFolder() + "/web/data/data.csv");
        if (!file.exists()) {
            file.delete();
        }
        file.createNewFile();
        @Cleanup
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        String[] shopNames = ShopUtil.getShopNames();
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
