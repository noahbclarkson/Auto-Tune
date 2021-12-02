package unprotesting.com.github.data.ephemeral.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.util.LocalDateTimeArrayUtilizer;
import unprotesting.com.github.economy.EconomyFunctions;

//  Loan data class for storing player loan info

public class LoanData extends LocalDateTimeArrayUtilizer implements Comparable<LoanData>{

    @Getter
    private LocalDateTime date;
    @Getter @Setter
    private Double value,
                   interest_rate,
                   base_value;
    @Getter
    private String player;

    //  Create new loan data
    public LoanData(Double value, double interest_rate, String player_uuid){
        this.date = LocalDateTime.now();
        this.interest_rate = interest_rate;
        this.value = value;
        this.player = player_uuid;
        this.base_value = value;
    }

    //  Create new loan data using previous persistent data
    public LoanData(Double value, double interest_rate, String player_uuid, int[] time){
        this.date = arrayToDate(time);
        this.interest_rate = interest_rate;
        this.value = value;
        this.player = player_uuid;
        this.base_value = value;
    }

    @Override
    public int compareTo(LoanData o) {
        return o.getDate().compareTo(getDate());
    }

    public boolean payBackLoan(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        OfflinePlayer offPlayer = Bukkit.getPlayer(UUID.fromString(player));
        double balance = EconomyFunctions.getEconomy().getBalance(offPlayer);
        if (balance < getValue()){
            return false;
        }
        EconomyFunctions.getEconomy().withdrawPlayer(offPlayer, value);
        if (offPlayer.isOnline()){
            Player onlinePlayer = (Player) offPlayer;
            onlinePlayer.getOpenInventory().close();
            onlinePlayer.sendMessage(ChatColor.GREEN + "Loan created on " + date.format(formatter) + " has been paid back.");
            onlinePlayer.sendMessage(ChatColor.GREEN + Config.getCurrencySymbol() + value + " has been withdrawn from your balance.");
            
        }
        Main.getCache().getLOANS().remove(this);
        Main.getCache().getNEW_LOANS().remove(this);
        GDPData data = Main.getCache().getGDP_DATA();
        data.increaseLoss(value-base_value);
        Main.getCache().setGDP_DATA(data);
        return true;
    }

}
