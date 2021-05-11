package unprotesting.com.github.Data.CSV;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.Persistent.TimePeriod;
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
        int size = Main.database.map.size();
        TimePeriod StringTP = Main.database.map.get(size-1);
        List<String> strs = Arrays.asList(StringTP.getItp().getItems());
        if (size < cutoff || cutoff < 3){
            cutoff = size;
        }
        Collections.sort(strs);
        for (String item : strs){
            writer.write("\n" + "%" + item + "\n");
            for (int i = (size-cutoff); i < size; i++){
                ItemTimePeriod ITP = Main.database.map.get(i).getItp();
                int pos = Arrays.asList(ITP.getItems()).indexOf(item);
                writer.write(ITP.getPrices()[pos] + "," +  ITP.getBuys()[pos] + "," + ITP.getSells()[pos] + "\n");
            }
        }
        writer.close();
    }
    
}
