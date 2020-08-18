package unprotesting.com.github.util;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.AutoTuneSellCommand;

public class AutoTunePlayerAutoSellEventHandler implements Runnable{

    @Override
    public void run() {
        for (String uuid : Main.tempdatadata.keySet()){
            if (uuid == "SellPriceDifferenceDifference"){
                continue;
            }
            Player p;
            try{
            p = Bukkit.getPlayer(UUID.fromString(uuid));
            }
            catch (IllegalArgumentException e){
                continue;
            }
            AutoTuneSellCommand.roundAndGiveMoney(p, Main.tempdatadata.get(uuid), false);
            Main.tempdatadata.remove(uuid);
        }
    }
}