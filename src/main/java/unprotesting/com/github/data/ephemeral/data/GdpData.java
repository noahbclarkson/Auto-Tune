package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import unprotesting.com.github.Main;
import unprotesting.com.github.economy.EconomyFunctions;

@Getter
public class GdpData {

  @Setter
  private double gdp;
  @Setter
  private double balance;
  @Setter
  private double debt;
  @Setter
  private double loss;
  @Setter
  private double inflation;
  private int playerCount;

  /**
   * Initializes the GDP data.
   */
  public GdpData(double gdp, double balance, double loss,
       double debt, double inflation, int playerCount) {

    this.gdp = gdp;
    this.loss = loss;
    this.balance = balance;
    this.playerCount = playerCount;
    this.debt = debt;
    this.inflation = inflation;

  }

  /**
   * Increases the GDP.
   * @param amount The amount to increase by.
   */
  public void increaseGdp(double amount) {
    gdp += amount;
  }

  /**
   * Increases the loss.
   * @param amount The amount to increase by.
   */
  public void increaseLoss(double amount) {
    loss += amount;
  }

  /**
   * Update the server total balance.
   */
  public void updateBalance() {

    double serverBalance = 0;
    int serverPlayerCount = 0;

    // Loop through all joined players.
    for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {

      // If the player is null, continue.
      if (player == null) {
        continue;
      }

      try {
        serverBalance += EconomyFunctions.getEconomy().getBalance(player);
        serverPlayerCount++;
      } catch (Exception e) {
        return;
      }

    }

    balance = serverBalance;
    playerCount = serverPlayerCount;

  }

  /**
   * Update the server debt.
   */
  public void updateDebt() {

    double serverDebt = 0;

    // Loop through loans and add value to server debt.
    for (LoanData data : Main.getInstance().getCache().getLoans()) {
      serverDebt += data.getValue();
    }

    debt = serverDebt;

  }

  /**
   * Update the server inflation.
   */
  public void updateInflation() {

    double inflationTotal = 0.0;
    double i = 0;

    // Loop through all percentage changes and add them to the total.
    for (String str : Main.getInstance().getCache().getPercentageChanges().keySet()) {
      inflationTotal += Main.getInstance().getCache().getPercentageChanges().get(str);
      i++;
    }

    inflation = (inflationTotal / i);

  }

}
