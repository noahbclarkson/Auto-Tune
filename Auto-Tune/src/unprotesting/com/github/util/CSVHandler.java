package unprotesting.com.github.util;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import unprotesting.com.github.Main;

public class CSVHandler {

    public static void writeCSV() throws InterruptedException,
  IOException {
    FileWriter csvWriter = new FileWriter("plugins/Auto-Tune/web/trade.csv");

    Set < String > strSet = Main.map.keySet();
    for (String str: strSet) {
      ConcurrentHashMap < Integer,
      Double[] > item = Main.map.get(str);

      csvWriter.append("\n");
      csvWriter.append("%" + str);
      csvWriter.append(",");
      csvWriter.append("\n");

      for (int i = 0; i > -100; i++) {
        String k = String.valueOf(i);
        csvWriter.append(k);
        Double[] l = (item.get(i));
        if (l == null) {
          break;
        }
        double SP = l[0];
        String parsedSP = String.valueOf(SP);
        csvWriter.append(",");
        csvWriter.append(parsedSP);
        double Buy = l[1];
        String parsedBuy = String.valueOf(Buy);
        csvWriter.append(",");
        csvWriter.append(parsedBuy);
        double Sell = l[2];
        String parsedSell = String.valueOf(Sell);
        csvWriter.append(",");
        csvWriter.append(parsedSell);
        csvWriter.append("\n");
      }
      csvWriter.append("\n");
    }
    // for (List<String> rowData : rows) {
    //     csvWriter.append(String.join(",", rowData));
    //     csvWriter.append("\n");
    // }
    csvWriter.flush();
    csvWriter.close();

  }

  public static void writeShortCSV() throws InterruptedException,
  IOException {
    FileWriter csvWriter = new FileWriter("plugins/Auto-Tune/web/trade-short.csv");

    Set < String > strSet = Main.map.keySet();
    for (String str: strSet) {
      ConcurrentHashMap < Integer,
      Double[] > item = Main.map.get(str);

      csvWriter.append("\n");
      csvWriter.append("%" + str);
      csvWriter.append(",");
      csvWriter.append("\n");

      int size = item.size();

      for (int i = size-Config.getMaximumShortTradeLength(); i > -100; i++) {
        String k = String.valueOf(i);
        csvWriter.append(k);
        Double[] l = (item.get(i));
        if (l == null) {
          break;
        }
        double SP = l[0];
        String parsedSP = String.valueOf(SP);
        csvWriter.append(",");
        csvWriter.append(parsedSP);
        double Buy = l[1];
        String parsedBuy = String.valueOf(Buy);
        csvWriter.append(",");
        csvWriter.append(parsedBuy);
        double Sell = l[2];
        String parsedSell = String.valueOf(Sell);
        csvWriter.append(",");
        csvWriter.append(parsedSell);
        csvWriter.append("\n");
      }
      csvWriter.append("\n");
    }
    // for (List<String> rowData : rows) {
    //     csvWriter.append(String.join(",", rowData));
    //     csvWriter.append("\n");
    // }
    csvWriter.flush();
    csvWriter.close();

  }
    
}