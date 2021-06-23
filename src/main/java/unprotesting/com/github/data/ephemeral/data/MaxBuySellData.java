package unprotesting.com.github.data.ephemeral.data;

import lombok.Getter;

public class MaxBuySellData {

    @Getter
    private int buys,
                sells;

    public MaxBuySellData(int buys, int sells){
        this.buys = buys;
        this.sells = sells;
    }
    
}
