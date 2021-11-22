package unprotesting.com.github.data.persistent.timeperiods;

import java.io.Serializable;

import lombok.Getter;
import unprotesting.com.github.Main;

public class EconomyInfoTimePeriod implements Serializable{

    private static final long serialVersionUID = -1102531404L;

    @Getter
    private double sellPriceDifference;

    public EconomyInfoTimePeriod(){
        this.sellPriceDifference = Main.getCache().getECONOMY_INFO().getSellPriceDifference();
    }
    
}
