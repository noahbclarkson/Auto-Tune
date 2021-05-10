package unprotesting.com.github.Data.Ephemeral.Data;

import java.time.LocalDateTime;

import org.bukkit.entity.Player;

import lombok.Getter;
import unprotesting.com.github.Data.Util.LocalDateTimeArrayUtilizer;

//  Loan data class for storing player loan info

public class LoanData extends LocalDateTimeArrayUtilizer implements Comparable<LoanData>{

    @Getter
    private LocalDateTime date;
    @Getter
    private Double value, intrest_rate;
    @Getter
    private Player player;

    //  Create new loan data
    public LoanData(Double value, double intrest_rate, Player player){
        this.date = LocalDateTime.now();
        this.intrest_rate = intrest_rate;
        this.value = value;
        this.player = player;
    }

    //  Create new loan data using previous persistent data
    public LoanData(Double value, double intrest_rate, Player player, int[] time){
        this.date = arrayToDate(time);
        this.intrest_rate = intrest_rate;
        this.value = value;
        this.player = player;
    }

    @Override
    public int compareTo(LoanData o) {
        return this.getDate().compareTo(o.getDate());
    }

}
