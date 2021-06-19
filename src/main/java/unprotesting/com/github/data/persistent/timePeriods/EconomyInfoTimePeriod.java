package unprotesting.com.github.data.persistent.timePeriods;

import lombok.Getter;
import unprotesting.com.github.Main;

public class EconomyInfoTimePeriod {

    @Getter
    private double sellPriceDifference;

    public EconomyInfoTimePeriod(){
        this.sellPriceDifference = Main.getCache().getECONOMYINFO().getSellPriceDifference();
    }
    
}
