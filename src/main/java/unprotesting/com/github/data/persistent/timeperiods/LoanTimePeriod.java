package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;

@Getter 
public class LoanTimePeriod extends LocalDateTimeArrayUtilizer implements Serializable {

  private static final long serialVersionUID = -1102531408L;

  private double[] values;
  private double[] interestRates;
  private double[] baseValues;
  private String[] players;
  private int[][] time;

  /**
   * Initializes the loan time period.
   */
  public LoanTimePeriod() {

    init(Main.getInstance().getCache().getNewLoans().size());
    int i = 0;

    for (LoanData data : Main.getInstance().getCache().getNewLoans()) {

      setVars(i, data);
      i++;

    }

  }

  /**
   * Set the variables for the time period.
   * @param pos The index of the time period.
   * @param data The loan data.
   */
  private void setVars(int pos, LoanData data) {

    values[pos] = data.getValue();
    interestRates[pos] = data.getInterestRate();
    players[pos] = data.getPlayer();
    time[pos] = dateToIntArray(data.getDate());
    values[pos] = data.getBaseValue();

  }

  /**
   * Initializes the time period.
   * @param size The size of the time period.
   */
  private void init(int size) {
    values = new double[size];
    interestRates = new double[size];
    players = new String[size];
    time = new int[size][6];
    baseValues = new double[size];
  }

}
