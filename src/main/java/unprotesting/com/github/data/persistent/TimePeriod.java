package unprotesting.com.github.data.persistent;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.persistent.timeperiods.EconomyInfoTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.GDPTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.ItemTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.LoanTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.TransactionsTimePeriod;

//  Time period object to be stored in database

public class TimePeriod implements Serializable{

    //  Contains all relavent time-periods in persistent form

    private static final long serialVersionUID = -1102531403L;

    @Getter @Setter
    private ItemTimePeriod itp;
    @Getter @Setter
    private EnchantmentsTimePeriod etp;
    @Getter @Setter
    private TransactionsTimePeriod ttp;
    @Getter @Setter
    private LoanTimePeriod ltp;
    @Getter @Setter
    private GDPTimePeriod gtp;
    @Getter @Setter
    private EconomyInfoTimePeriod eitp;

    public TimePeriod(){
        getFromCache();
    }

    public TimePeriod(boolean empty){
        if (!empty){
            getFromCache();
        }
    }

    public void addToMap(){
        int size = Main.getDatabase().map.size();
        Main.getDatabase().map.put(size, this);
    }

    private void getFromCache(){
        getITPFromCache();
        getETPFromCache();
        getTTPFromCache();
        getLTPFromCache();
        getGTPFromCache();
        getEITPFromCache();
    }

    private void getITPFromCache(){
        this.itp = new ItemTimePeriod();
    }

    private void getETPFromCache(){
        this.etp = new EnchantmentsTimePeriod();
    }

    private void getTTPFromCache(){
        this.ttp = new TransactionsTimePeriod();
    }

    private void getLTPFromCache(){
        this.ltp = new LoanTimePeriod();
    }

    private void getGTPFromCache(){
        this.gtp = new GDPTimePeriod();
    }

    private void getEITPFromCache(){
        this.eitp = new EconomyInfoTimePeriod();
    }
    
}
