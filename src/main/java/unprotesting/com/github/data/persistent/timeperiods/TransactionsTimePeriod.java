package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.TransactionData;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;

@Getter 
public class TransactionsTimePeriod extends LocalDateTimeArrayUtilizer implements Serializable {

  private static final long serialVersionUID = -1102531409L;

  private double[] prices;
  private int[] amounts;
  private int[][] time;
  private String[] players;
  private String[] items;
  private String[] positions;

  /**
   * Initializes the transaction time period.
   */
  public TransactionsTimePeriod() {

    init(Main.getInstance().getCache().getNewTransactions().size());
    int i = 0;

    // Add all transactions in cache to the time period.
    for (TransactionData data : Main.getInstance().getCache().getNewTransactions()) {

      setVars(i, data);
      i++;

    }

  }

  /**
   * Set the variables for the time period.
   * @param pos The index of the time period.
   * @param data The transaction data.
   */
  private void setVars(int pos, TransactionData data) {

    prices[pos] = data.getPrice();
    amounts[pos] = data.getAmount();
    players[pos] = data.getPlayer();
    items[pos] = data.getItem();
    time[pos] = dateToIntArray(data.getDate());
    positions[pos] = data.getPosition().toString();

    // If the player is null throw an exception.
    if (players[pos] == null) {
      throw new NullPointerException("Transaction player can't be null in database");
    }

  }

  private void init(int size) {

    prices = new double[size];
    players = new String[size];
    items = new String[size];
    time = new int[size][6];
    amounts = new int[size];
    positions = new String[size];

  }

}
