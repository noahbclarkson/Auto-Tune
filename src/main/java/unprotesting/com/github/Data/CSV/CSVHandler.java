package unprotesting.com.github.Data.CSV;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Persistent.TimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.EnchantmentsTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.ItemTimePeriod;

public class CSVHandler {

    //  Create CSV files with all price, buy and sell data to be read by the webpage
    public static void writeCSV(){
        try {
            write("trade", 0);
            write("trade-short", Config.getMaximumShortTradeLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //  Create individual CSV file
    private static void write(String csvname, int cutoff) throws IOException{
        FileWriter writer = new FileWriter("plugins/Auto-Tune/web/" + csvname + ".csv");
        int size = Main.getDatabase().map.size();
        TimePeriod StringTP = Main.getDatabase().map.get(size-1);
        List<String> strs = Arrays.asList(StringTP.getItp().getItems());
        List<String> strs2 = Arrays.asList(StringTP.getEtp().getItems());
        if (size < cutoff || cutoff < 3){
            cutoff = size;
        }
        Collections.sort(strs);
        Collections.sort(strs);
        for (String item : strs){
            writer.write("\n" + "%" + item + "\n");
            for (int i = (size-cutoff); i < size; i++){
                ItemTimePeriod ITP = Main.getDatabase().map.get(i).getItp();
                int pos = Arrays.asList(ITP.getItems()).indexOf(item);
                writer.append(i + "," + ITP.getPrices()[pos] + "," +  ITP.getBuys()[pos] + "," + ITP.getSells()[pos] + "\n");
            }
        }
        if (Config.isEnableEnchantments()){
            for (String enchantment : strs2){
                writer.write("\n" + "%" + enchantment + "\n");
                for (int i = (size-cutoff); i < size; i++){
                    EnchantmentsTimePeriod ETP = Main.getDatabase().map.get(i).getEtp();
                    int pos = Arrays.asList(ETP.getItems()).indexOf(enchantment);
                    writer.append(i + "," + ETP.getPrices()[pos] + "," +  ETP.getBuys()[pos] + "," + ETP.getSells()[pos] + "\n");
                }
            }
        }
        writer.close();
    }
    
}
