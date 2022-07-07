package unprotesting.com.github.data;

/**
 * Utility class for the servers economy.
 */
public class EconomyDataUtil {

  /**
   * Update the economy data of ta given economy data setting.
   *
   * @param key   The key of the economy data setting.
   * @param value The new value of the economy data setting.
   */
  public static void updateEconomyData(String key, double value) {
    double[] data = Database.get().economyData.get(key);
    data[data.length - 1] = value;
    Database.get().economyData.put(key, data);
  }

  /**
   * Increase the economy data of a given economy data setting.
   *
   * @param key   The key of the economy data setting.
   * @param value The value to increase the economy data setting by.
   */
  public static void increaseEconomyData(String key, double value) {
    double[] data = Database.get().economyData.get(key);
    data[data.length - 1] += value;
    Database.get().economyData.put(key, data);
  }

  public static double getGdp() {
    double[] data = Database.get().economyData.get("GDP");
    return data[data.length - 1];
  }

  public static double getBalance() {
    double[] data = Database.get().economyData.get("BALANCE");
    return data[data.length - 1];
  }

  public static int getPopulation() {
    double[] data = Database.get().economyData.get("POPULATION");
    return (int) data[data.length - 1];
  }

  public static double getLoss() {
    double[] data = Database.get().economyData.get("LOSS");
    return data[data.length - 1];
  }

  public static double getDebt() {
    double[] data = Database.get().economyData.get("DEBT");
    return data[data.length - 1];
  }

  public static double getInflation() {
    double[] data = Database.get().economyData.get("INFLATION");
    return data[data.length - 1];
  }

}
