package unprotesting.com.github.data.csv;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.persistent.TimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.GDPTimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.ItemTimePeriod;

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
        for (int k = 0; k < 4; k++){
            if (k==0){
                writer.write("\n" + "%GDP" + "\n");
            }
            else if (k==1){
                writer.write("\n" + "%Balance" + "\n");
            }
            else if (k==2){
                writer.write("\n" + "%Debt" + "\n");
            }
            else if (k==3){
                writer.write("\n" + "%Loss" + "\n");
            }
            for (int i = (size-cutoff); i < size; i++){
                GDPTimePeriod GTP = Main.getDatabase().map.get(i).getGtp();
                if (k==0){
                    writer.write(i + "," + GTP.getGDP() + "\n");
                }
                else if (k==1){
                    writer.write(i + "," + GTP.getBalance() + "\n");
                }
                else if (k==2){
                    writer.write(i + "," + GTP.getDebt() + "\n");
                }
                else if (k==3){
                    writer.write(i + "," + GTP.getLoss() + "\n");
                }
            }
        }
        writer.close();
    }
    
}
