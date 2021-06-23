package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;

public class EconomyInfoTimePeriod implements Serializable{

    @Getter
    private double sellPriceDifference;

    public EconomyInfoTimePeriod(){
        this.sellPriceDifference = Main.getCache().getECONOMYINFO().getSellPriceDifference();
    }
    
}
