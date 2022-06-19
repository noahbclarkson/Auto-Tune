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
import unprotesting.com.github.data.objects.Loan;
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

    // Loop through all loans and update them.
    for (Loan[] loan : Main.getInstance().getDb().getLoans().values()) {

      for (int i = 0; i < loan.length; i++) {
        loan[i].update();
      }

    }

  }


}
