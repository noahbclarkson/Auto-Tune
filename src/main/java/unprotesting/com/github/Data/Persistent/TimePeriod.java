package unprotesting.com.github.data.persistent;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.data.persistent.timePeriods.EconomyInfoTimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.GDPTimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.ItemTimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.LoanTimePeriod;
import unprotesting.com.github.data.persistent.timePeriods.TransactionsTimePeriod;

//  Time period object to be stored in database

public class TimePeriod implements Serializable{

    //  Contains all relavent time-periods in persistent form

    @Getter
    private ItemTimePeriod itp;
    @Getter
    private EnchantmentsTimePeriod etp;
    @Getter
    private TransactionsTimePeriod ttp;
    @Getter
    private LoanTimePeriod ltp;
    @Getter
    private GDPTimePeriod gtp;
    @Getter
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
