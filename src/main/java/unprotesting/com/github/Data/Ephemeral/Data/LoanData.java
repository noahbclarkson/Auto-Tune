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
                   interest_rate;
    @Getter
    private String player;

    //  Create new loan data
    public LoanData(Double value, double interest_rate, String player_uuid){
        this.date = LocalDateTime.now();
        this.interest_rate = interest_rate;
        this.value = value;
        this.player = player_uuid;
    }

    //  Create new loan data using previous persistent data
    public LoanData(Double value, double interest_rate, String player_uuid, int[] time){
        this.date = arrayToDate(time);
        this.interest_rate = interest_rate;
        this.value = value;
        this.player = player_uuid;
    }

    @Override
    public int compareTo(LoanData o) {
        return o.getDate().compareTo(this.getDate());
    }

    public boolean payBackLoan(){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        OfflinePlayer player = Bukkit.getPlayer(UUID.fromString(this.player));
        double balance = EconomyFunctions.getEconomy().getBalance(player);
        if (balance < this.getValue()){
            return false;
        }
        EconomyFunctions.getEconomy().withdrawPlayer(player, this.value);
        if (player.isOnline()){
            Player onlinePlayer = (Player) player;
            onlinePlayer.getOpenInventory().close();
            onlinePlayer.sendMessage(ChatColor.GREEN + "Loan created on " + this.date.format(formatter) + " has been payed-back.");
            onlinePlayer.sendMessage(ChatColor.GREEN + Config.getCurrencySymbol() + this.value + " has been withdrawn from your balance.");
            
        }
        Main.getCache().getLOANS().remove(this);
        Main.getCache().getNEW_LOANS().remove(this);
        return true;
    }

}
