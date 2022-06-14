package unprotesting.com.github.data.persistent;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.persistent.timeperiods.EconomyInfoTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.GdpTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.ItemTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.LoanTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.TransactionsTimePeriod;

@Getter
@Setter
public class TimePeriod implements Serializable {

  private static final long serialVersionUID = -1102531403L;

  private ItemTimePeriod itemTP;
  private EnchantmentsTimePeriod enchantmentsTP;
  private TransactionsTimePeriod transactionsTP;
  private LoanTimePeriod loanTP;
  private GdpTimePeriod gdpTP;
  private EconomyInfoTimePeriod economyInfoTP;

  /**
   * Initializes the time periods.
   */
  public TimePeriod() {
    getFromCache();
  }

  /**
   * Initializes the time periods.
   * @param empty Whether the time periods are empty or not.
   */
  public TimePeriod(boolean empty) {

    if (!empty) {
      getFromCache();
    }

  }

  /**
   * Adds the time periods to the database.
   */
  public void addToMap() {

    int size = Main.getInstance().getDatabase().getMap().size();
    Main.getInstance().getDatabase().getMap().put(size, this);
    
  }

  /**
   * Initializes the time periods.
   */
  private void getFromCache() {

    itemTP = new ItemTimePeriod();
    enchantmentsTP = new EnchantmentsTimePeriod();
    transactionsTP = new TransactionsTimePeriod();
    loanTP = new LoanTimePeriod();
    gdpTP = new GdpTimePeriod();
    economyInfoTP = new EconomyInfoTimePeriod();

  }


}
