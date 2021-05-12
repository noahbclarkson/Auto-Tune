package unprotesting.com.github.Data.Ephemeral.Data;

import lombok.Getter;

public class MaxBuySellData {

    @Getter
    private int buys, sells;

    public MaxBuySellData(int buys, int sells){
        this.buys = buys;
        this.sells = sells;
    }
    
}
