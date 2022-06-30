package unprotesting.com.github.events;

import java.util.Map;

import lombok.Getter;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Loan;

public class LoanInterestEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Updates the loan data.
   * @param isAsync Whether the event is being run async or not.
   */
  public LoanInterestEvent(boolean isAsync) {
    super(isAsync);
    updateLoans();
  }

  /**
   * Updates the loan data.
   */
  public void updateLoans() {
    for (Map.Entry<Long, Loan> entry : Database.get().getLoans().entrySet()) {
      Loan loan = entry.getValue();
      loan.update();
      Database.get().updateLoan(entry.getKey(), loan);
    }
  }
  
}
