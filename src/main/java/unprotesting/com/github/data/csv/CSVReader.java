package unprotesting.com.github.data.csv;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.ItemData;

//Reads a csv file and then creates a map of item names and prices which can then be used to set new prices

public class CSVReader { 

    public static ConcurrentHashMap<String, ItemData> readData() throws Exception {  
        final char percent = '%';
        final String path = "plugins/Auto-Tune/web/trade.csv";
        String line = "";
        ConcurrentHashMap<String, ItemData> output = new ConcurrentHashMap<String, ItemData>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        while((line = br.readLine()) != null){
            if (line.length() < 2){continue;}
            char firstChar = line.charAt(0);
            if (firstChar == percent){
                String item = line.replace("%", "").replace(",", "");
                if (item == null || item.length() < 1){continue;}
                String[] values = new String[4];
                Double[] output_values = new Double[4];
                int i = 0;
                while ((line = br.readLine()) != null){
                    if (i < 2){i++;continue;}
                    values = line.split(",");
                    if (Arrays.asList(values).contains(null) || Arrays.asList(values).contains("") || Arrays.asList(values).size() < 4){
                        break;
                    }
                    try{
                        output_values[0] = Double.parseDouble(values[0]);
                        output_values[1] = Double.parseDouble(values[1]);
                        output_values[2] += Double.parseDouble(values[2]);
                        output_values[3] += Double.parseDouble(values[3]);
                    }
                    catch(NullPointerException e){break;}
                }
                if (output_values[1] == null){
                    try{output_values[1] = Main.getDataFiles().getShops().getConfigurationSection(item).getDouble("price");}
                    catch(NullPointerException e){continue;}
                }
                if (output_values[2] == null){output_values[2] = 0.0;}
                if (output_values[3] == null){output_values[3] = 0.0;}
                ItemData iData = new ItemData(Double.valueOf(output_values[1]));
                iData.increaseBuys(output_values[2].intValue());
                iData.increaseSells(output_values[3].intValue());
                output.put(item, iData);
            }
        }
        br.close();
        return output;
    }  

}  
