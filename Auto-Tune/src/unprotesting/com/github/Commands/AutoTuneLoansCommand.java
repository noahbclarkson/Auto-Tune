package unprotesting.com.github.Commands;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.HoverEvent.Action;
import net.md_5.bungee.api.ChatColor;
import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneLoansCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String loans, String[] args) {
        if (command.getName().equalsIgnoreCase("loans")){
            Player p = (Player) sender;
            if (p.hasPermission("at.loan") || p.isOp()){
                UUID uuid = p.getUniqueId();
                boolean loanpresent = false;
                Set<String> set = Main.loanMap.getKeys();
                for (String str : set){
                    if (str.contains(uuid.toString())){
                        sendLoanInfo(p, str);
                        loanpresent = true;
                    }
                }
                if (!loanpresent){
                    p.sendMessage(ChatColor.RED + "No loans currently active. Do /loan <amount> to make a loan");
                }
            }
            else if (!(p.hasPermission("at.loan")) && !(p.isOp())){
                TextHandler.noPermssion(p);
                return true;
            }
        }
        return true;
    }

    public void sendLoanInfo(Player p, String str){
        double[] dArray = Main.loanMap.get(str);
        double initalTime = dArray[3];
        Date initialDate = new Date((long) initalTime);
        String initialDateString = Main.dateFormat.format(initialDate);
        float sec = (((float)(Long.valueOf(Instant.now().toEpochMilli()).doubleValue() - initalTime))/1000);
        String lastChar = str.substring(str.length() - 1);
        String currentPrice = AutoTuneGUIShopUserCommand.df3.format(dArray[0]);
        TextComponent loanMessage = new TextComponent(ChatColor.GOLD + "Loan No: " + lastChar + " - Current Value: "+ Config.getCurrencySymbol() + currentPrice + " - Intrest Rate: %" + dArray[1] + " - Initial Value: "+ Config.getCurrencySymbol() + dArray[2] + " - Creation Date: " + initialDateString + " - Elapsed Time: " + sec + "s");
        loanMessage.setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new ComponentBuilder("Click to payback loan").create()));
        loanMessage.setClickEvent(new ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/payloan " + lastChar));
        p.spigot().sendMessage(loanMessage);
    }
    
}