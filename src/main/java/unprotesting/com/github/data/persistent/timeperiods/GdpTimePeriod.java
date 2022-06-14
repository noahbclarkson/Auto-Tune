package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.ephemeral.data.GdpData;

@Getter
public class GdpTimePeriod implements Serializable {

  private static final long serialVersionUID = -1102531406L;

  private double gdp;
  private double balance;
  private double debt;
  private double loss;
  private double inflation;
  private int playerCount;

  /**
   * Initializes the GDP time period.
   */
  public GdpTimePeriod() {

    Main.getInstance().getCache().getGdpData().updateDebt();
    Main.getInstance().getCache().getGdpData().updateBalance();
    Main.getInstance().getCache().getGdpData().updateInflation();
    GdpData data = Main.getInstance().getCache().getGdpData();
    this.gdp = data.getGdp();
    this.balance = data.getBalance();
    this.playerCount = data.getPlayerCount();
    this.debt = data.getDebt();
    this.loss = data.getLoss();
    this.inflation = data.getInflation();

  }

}
