package unprotesting.com.github.events;

import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Loan;

import java.util.Map;

/**
 * The event for updating the value of a loan.
 */
public class LoanInterestEvent extends AutoTuneEvent {

    /**
     * Updates the loan data.
     *
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
        Database database = Database.get();
        for (Map.Entry<Long, Loan> entry : Database.get().getLoans().entrySet()) {
            Loan loan = entry.getValue();
            loan.update();
            database.updateLoan(entry.getKey(), loan);
        }
    }

}
