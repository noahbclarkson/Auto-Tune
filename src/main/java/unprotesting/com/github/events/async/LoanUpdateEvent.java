package unprotesting.com.github.events.async;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.economy.EconomyFunctions;

public class LoanUpdateEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Updates the loan data.
   * @param isAsync Whether the event is being run async or not.
   */
  public LoanUpdateEvent(boolean isAsync) {

    super(isAsync);
    updateLoans();

  }

  private void updateLoans() {

    List<LoanData> output = new ArrayList<>();

    // Loop through all loans and update them.
    for (LoanData loan : Main.getInstance().getCache().getLoans()) {

      loan.setValue(loan.getValue() + loan.getValue() * 0.01 * loan.getInterestRate());

      OfflinePlayer offlinePlayer = Main.getInstance().getServer()
          .getOfflinePlayer(UUID.fromString(loan.getPlayer()));

      double balance = EconomyFunctions.getEconomy().getBalance(offlinePlayer);

      // If the loan is overpaid then remove it.
      if (balance - loan.getValue() < Config.getConfig().getMaxDebt()) {
        loan.payBackLoan();
      }

      output.add(loan);

    }

    Main.getInstance().getCache().setLoans(output);

  }

}
