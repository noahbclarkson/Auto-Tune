package unprotesting.com.github.events.async;

import java.text.DecimalFormat;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.AutosellData;
import unprotesting.com.github.economy.EconomyFunctions;

public class AutosellProfitUpdateEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public AutosellProfitUpdateEvent(boolean isAsync){
        super(isAsync);
        depositCachedMoney();
        Main.setAutosellData(new AutosellData());
    }

    private void depositCachedMoney(){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        for (String player_uuid : Main.getAutosellData().getData().keySet()){
            OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(UUID.fromString(player_uuid));
            double amount = Main.getAutosellData().getData().get(player_uuid);
            EconomyFunctions.getEconomy().depositPlayer(offPlayer, amount);
            if (offPlayer.isOnline()){
                Player player = (Player) offPlayer;
                player.sendMessage(ChatColor.GOLD + "Your items were sold and " + Config.getCurrencySymbol() + df.format(amount) + 
                " has been added to your account");
            }
        }
    }


}