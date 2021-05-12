package unprotesting.com.github.Data.CSV;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.ConcurrentHashMap;

//Reads a csv file and then creates a map of item names and prices which can then be used to set new prices

public class CSVReader { 

    public static ConcurrentHashMap<String, Double> readData() throws Exception {  
        //Will need to change the path to location of the csv file
        ConcurrentHashMap<String, Double> output = new ConcurrentHashMap<String, Double>();
        char percent = '%';
        String path = "plugins/Auto-Tune/web/trade.csv";
        String line = "";
        BufferedReader br = new BufferedReader(new FileReader(path));
        while((line = br.readLine()) != null){
            char firstChar = line.charAt(0);
            if (firstChar == percent){
                String item = line.replaceAll("%", "");
                String[] values = new String[3];
                while ((line = br.readLine()) != null){
                    if (line.length() < 2){
                        break;
                    }
                    values = line.split(",");
                }
                System.out.println(item + ": " + values[0]);
                output.put(item, Double.valueOf(values[0]));
            }
        }
        br.close();
        return output;


        // while((line = br.readLine()) != null){
        //     if(line.length() > 0){
        //         char firstChar = line.charAt(0);
        //         if(firstChar == percent){
        //             String item = line.replaceAll("%", "");
        //             String[] values = new String[3];
        //             while (true){
        //                 line = br.readLine();
        //                 firstChar = line.charAt(0);
        //                 if(firstChar == percent){
        //                     item = line.replaceAll("%", "");
        //                     break;
        //                 }
        //                 values = line.split(",");
        //             }
        //             System.out.println(item + ": " + Double.parseDouble(values[0]));
        //             output.put(item, Double.parseDouble(values[0]));
        //         }    
        //     } 
        // }
        // br.close();
        // return output;
    }  

}  
