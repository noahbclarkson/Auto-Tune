package unprotesting.com.github.data;

import lombok.experimental.UtilityClass;

/**
 * Utility class for the servers economy.
 */
@UtilityClass
public class EconomyDataUtil {

    /**
     * Update the economy data of ta given economy data setting.
     *
     * @param key   The key of the economy data setting.
     * @param value The new value of the economy data setting.
     */
    public void updateEconomyData(String key, double value) {
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
    public void increaseEconomyData(String key, double value) {
        double[] data = Database.get().economyData.get(key);
        data[data.length - 1] += value;
        Database.get().economyData.put(key, data);
    }

    public double getGdp() {
        double[] data = Database.get().economyData.get("GDP");
        return data[data.length - 1];
    }

    public double getBalance() {
        double[] data = Database.get().economyData.get("BALANCE");
        return data[data.length - 1];
    }

    public int getPopulation() {
        double[] data = Database.get().economyData.get("POPULATION");
        return (int) data[data.length - 1];
    }

    public double getLoss() {
        double[] data = Database.get().economyData.get("LOSS");
        return data[data.length - 1];
    }

    public double getDebt() {
        double[] data = Database.get().economyData.get("DEBT");
        return data[data.length - 1];
    }

    public double getInflation() {
        double[] data = Database.get().economyData.get("INFLATION");
        return data[data.length - 1];
    }

}
