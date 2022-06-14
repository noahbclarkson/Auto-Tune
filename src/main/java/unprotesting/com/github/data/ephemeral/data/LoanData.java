package unprotesting.com.github.data.ephemeral.data;

import java.text.DecimalFormat;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;
import unprotesting.com.github.economy.EconomyFunctions;

@Getter
@Setter
public class LoanData extends LocalDateTimeArrayUtilizer implements Comparable<LoanData> {

  
  private LocalDateTime date;
  private Double value;
  private Double interestRate;
  private Double baseValue;
  private String player;

  /**
   * Initializes the loan data.
   * @param value The value of the loan.
   * @param interestRate The interest rate of the loan.
   * @param uuid The UUID of the player.
   */
  public LoanData(Double value, double interestRate, String uuid) {
    this.date = LocalDateTime.now();
    this.interestRate = interestRate;
    this.value = value;
    this.player = uuid;
    this.baseValue = value;
  }

  /**
   * Initializes the loan data using previous persisted data.
   * @param value The value of the loan.
   * @param interestRate The interest rate of the loan.
   * @param uuid The UUID of the player.
   * @param time The time array of the loan.
   */
  public LoanData(Double value, double interestRate, String uuid, int[] time) {
    this.date = arrayToDate(time);
    this.interestRate = interestRate;
    this.value = value;
    this.player = uuid;
    this.baseValue = value;
  }

  /**
   * Compares the loan data.
   */
  @Override
  public int compareTo(LoanData o) {
    return o.getDate().compareTo(getDate());
  }

  /**
   * Payback the loan.
   * @return Whether the loan was payed back.
   */
  public boolean payBackLoan() {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    OfflinePlayer offPlayer = Bukkit.getPlayer(UUID.fromString(player));
    double balance = EconomyFunctions.getEconomy().getBalance(offPlayer);
    
    if (balance < getValue()) {
      return false;
    }

    EconomyFunctions.getEconomy().withdrawPlayer(offPlayer, value);

    if (offPlayer.isOnline()) {

      Player onlinePlayer = (Player) offPlayer;
      onlinePlayer.getOpenInventory().close();

      onlinePlayer.sendMessage(ChatColor.GREEN + "Loan created on " 
          + date.format(formatter) + " has been paid back.");

      onlinePlayer.sendMessage(ChatColor.GREEN + Config.getConfig().getCurrencySymbol()
          + new DecimalFormat(Config.getConfig().getNumberFormat()).format(value)
          + " has been withdrawn from your balance.");

    }

    Main.getInstance().getCache().getLoans().remove(this);
    Main.getInstance().getCache().getNewLoans().remove(this);
    Main.getInstance().getCache().getGdpData().increaseLoss(value - baseValue);
    return true;

  }

}
