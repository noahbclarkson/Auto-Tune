package unprotesting.com.github.data;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.EconomyUtil;

/**
 * The class that represents a Loan.
 */
@AllArgsConstructor
@Data
@Builder
public class Loan implements Serializable {

    private static final long serialVersionUID = -5882241259956156012L;

    protected double value;
    protected double base;
    protected UUID player;
    protected boolean paid;

    /**
     * Pay back the given loan.
     *
     * @return Whether or not the loan was paid back.
     */
    public boolean payBack() {
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player);
        double balance = EconomyUtil.getEconomy().getBalance(offPlayer);

        if (balance < value) {
            return false;
        }

        EconomyUtil.getEconomy().withdrawPlayer(offPlayer, value);
        paid = true;
        EconomyDataUtil.increaseEconomyData("LOSS", value - base);
        return true;
    }

    /**
     * Update the value of the loan.
     */
    public void update() {
        value += value * 0.01 * Config.get().getInterest();
        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player);
        double balance = EconomyUtil.getEconomy().getBalance(offPlayer);

        if (balance <= value + value * 0.01 * Config.get().getInterest()) {
            payBack();
        }
    }

}
