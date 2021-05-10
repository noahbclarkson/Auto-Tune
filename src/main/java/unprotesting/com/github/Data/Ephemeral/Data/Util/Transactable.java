package unprotesting.com.github.Data.Ephemeral.Data.Util;

import lombok.Getter;

//  Abstract class for transactable objects

public abstract class Transactable {

    @Getter
    private int buys, sells;

    public void increaseBuys(int amount){
        this.buys = this.buys+amount;
    }

    public void increaseSells(int amount){
        this.sells = this.sells + amount;
    }
    
}
