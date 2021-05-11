package unprotesting.com.github.Data.Persistent;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Data.Persistent.TimePeriods.EnchantmentsTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.ItemTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.LoanTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.TransactionsTimePeriod;

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

    public TimePeriod(){
        getFromCache();
    }

    public void addToMap(){
        int size = Main.database.map.size();
        Main.database.map.put(size, this);
    }

    private void getFromCache(){
        getITPFromCache();
        getETPFromCache();
        getTTPFromCache();
        getLTPFromCache();
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

    
}
