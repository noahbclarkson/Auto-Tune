package unprotesting.com.github.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import lombok.Cleanup;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.data.Shop;
import unprotesting.com.github.data.ShopUtil;
import unprotesting.com.github.util.Format;

/**
 * Class for handling the creation of txt files.
 */
public class TxtHandler {

    /**
     * Export price data to prices.txt.
     */
    public static void exportPrices() {
        try {
            exportPriceData();
        } catch (IOException e) {
            Format.getLog().severe("Could not export prices!");
            Format.getLog().config(e.toString());
        }
    }

    /**
     * Import price data from prices.txt.
     */
    public static void importPrices() {
        try {
            importPriceData();
        } catch (IOException e) {
            Format.getLog().severe("Could not import prices!");
            Format.getLog().config(e.toString());
        }
    }

    private static void exportPriceData() throws IOException {
        File file = new File(AutoTune.getInstance().getDataFolder(), "prices.txt");
        file.delete();
        file.createNewFile();
        @Cleanup
        BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(file));
        String[] shopNames = ShopUtil.getShopNames();
        for (String shopName : shopNames) {
            Shop shop = ShopUtil.getShop(shopName, true);
            writer.write(shopName + ": " + shop.getPrice());
            writer.newLine();
        }
    }

    private static void importPriceData() throws IOException {
        File file = new File(AutoTune.getInstance().getDataFolder(), "prices.txt");
        @Cleanup
        BufferedReader reader = new BufferedReader(new java.io.FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            parseLine(line);
        }
    }

    private static void parseLine(String line) {
        String[] split = line.split(": ");
        String shopName = split[0];
        try {
            double price = Double.parseDouble(split[1]);
            Shop shop = ShopUtil.getShop(shopName, true);
            shop.setPrice(price);
            ShopUtil.putShop(shopName, shop);
        } catch (NumberFormatException e) {
            Format.getLog().warning("Could not parse price data for " + shopName);
            Format.getLog().config(e.toString());
        }
    }

}
