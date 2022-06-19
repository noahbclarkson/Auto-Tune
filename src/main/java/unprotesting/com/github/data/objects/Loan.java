package unprotesting.com.github.data.objects;

import java.io.IOException;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import lombok.Data;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.economy.EconomyFunctions;
import unprotesting.com.github.util.UtilFunctions;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.mapdb.DataInput2;
import org.mapdb.DataOutput2;
import org.mapdb.Serializer;

@Data
public class Loan implements Serializable {

  private static final long serialVersionUID = -5882241259956156012L;

  private Double value;
  private Double baseValue;
  private UUID player;
  private boolean paid = false;

  /**
   * Constructor.
   */
  public Loan(double value, double baseValue, UUID player, boolean paid) {

    this.value = value;
    this.baseValue = baseValue;
    this.player = player;
    this.paid = paid;

  }

  /**
   * Pay back the given loan.
   * @return true if the loan was paid back, false if it was not.
   */
  public boolean payBackLoan() {

    OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(player);
    double balance = EconomyFunctions.getEconomy().getBalance(offPlayer);
    
    if (balance < getValue()) {
      return false;
    }

    EconomyFunctions.getEconomy().withdrawPlayer(offPlayer, value);

    if (offPlayer.isOnline()) {

      Player onlinePlayer = (Player) offPlayer;
      onlinePlayer.getOpenInventory().close();

      onlinePlayer.sendMessage(ChatColor.GREEN + "Loan repaid! You have paid "
          + ChatColor.RED + "$" + UtilFunctions.getDf().format(value) + ChatColor.GREEN + ".");

    }

    Main.getInstance().getDb().getEconomyData().get("LOSS").increase(value);
    this.paid = true;
    return true;

  }

  /**
   * Update the value of the loan.
   */
  public void update() {

    this.value += this.value * 0.01 * Config.getConfig().getInterestRate();
    OfflinePlayer offlinePlayer = Main.getInstance().getServer().getOfflinePlayer(player);
    double balance = EconomyFunctions.getEconomy().getBalance(offlinePlayer);

    // If the loan is overpaid then remove it.
    if (balance - value < Config.getConfig().getMaxDebt()) {
      payBackLoan();
    }

  }



  public static class LoanSerializer implements Serializer<Loan> {

    @Override
    public void serialize(DataOutput2 out, Loan value) throws IOException {
        
      out.writeDouble(value.getValue());
      out.writeDouble(value.getBaseValue());
      UUID.serialize(out, value.getPlayer());
      out.writeBoolean(value.isPaid());

    }

    @Override
    public Loan deserialize(DataInput2 input, int available) throws IOException {
      
      double value = input.readDouble();
      double baseValue = input.readDouble();
      UUID player = UUID.deserialize(input, available);
      boolean paid = input.readBoolean();
      return new Loan(value, baseValue, player, paid);

    }

  }



  
}
