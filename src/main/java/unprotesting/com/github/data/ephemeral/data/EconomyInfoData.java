package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.config.Config;

//  Economy info data class for storing general economy data

public class EconomyInfoData {

    @Getter @Setter
    private double sellPriceDifference;

    public EconomyInfoData(double sellPriceDifference){
        this.sellPriceDifference = sellPriceDifference;
        Config.setSellPriceDifference(this.sellPriceDifference);
    }

    public void updateSellPriceDifference(double newSPD){
        sellPriceDifference = newSPD;
        Config.setSellPriceDifference(newSPD);
    }
    
}
