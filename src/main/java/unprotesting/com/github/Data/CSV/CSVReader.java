package unprotesting.com.github.Data.CSV;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

//Reads a csv file and then creates a map of item names and prices which can then be used to set new prices

public class CSVReader { 

    public static ConcurrentHashMap<String, Double> readData() throws Exception {  
        final char percent = '%';
        final String path = "plugins/Auto-Tune/web/trade.csv";
        String line = "";
        ConcurrentHashMap<String, Double> output = new ConcurrentHashMap<String, Double>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        while((line = br.readLine()) != null){
            if (line.length() < 2){
                continue;
            }
            char firstChar = line.charAt(0);
            if (firstChar == percent){
                String item = line.replaceAll("%", "").replaceAll(",", "");
                String[] values = new String[4];
                String[] output_values = new String[4];
                while ((line = br.readLine()) != null){
                    values = line.split(",");
                    if (Arrays.asList(values).contains(null) || Arrays.asList(values).size() < 4){
                        break;
                    }
                    output_values = values;
                }
                System.out.println(item + ": " + output_values[1]);
                output.put(item, Double.valueOf(output_values[1]));
            }
        }
        br.close();
        return output;
    }  

}  
