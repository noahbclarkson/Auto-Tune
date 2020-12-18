package unprotesting.com.github.Commands;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.Loan;
import unprotesting.com.github.util.LoanEventHandler;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneLoanCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player){
            Player player = (Player) sender;
            Loan loan = null;
            if (!(player.hasPermission("at.loan") || player.isOp())){
                TextHandler.noPermssion(player);
                return true;
            }
            if (args.length == 0){
                loadLoanGUI(player);
                return true;
            }
            if (args.length == 1){
                if (doesPlayerHaveTooManyLoans(player) == true){
                    return true;
                }
                double value = 0.0;
                try{
                    value = Double.parseDouble(args[0]);
                }
                catch(NumberFormatException ex){
                    player.sendMessage(ChatColor.RED + "Correct Usage: /loan <loan-amount> (Optional)<compund-interest-enabled>");
                    return true;
                }
                loan = new Loan(value, Config.getInterestRate(), (Config.getInterestRateUpdateRate()/20), Instant.now(), player, false);
                return true;
            }
            if (args.length == 2){
                if (doesPlayerHaveTooManyLoans(player) == true){
                    return true;
                }
                double value = 0.0;
                try{
                    value = Double.parseDouble(args[0]);
                }
                catch(NumberFormatException ex){
                    player.sendMessage(ChatColor.RED + "Correct Usage: /loan <loan-amount> (Optional)<compund-interest-enabled>");
                    return true;
                }

                boolean cIntrest = Boolean.parseBoolean(args[1]);
                if (cIntrest == true){
                    loan = new Loan(value, Config.getCompoundInterestRate(), (Config.getInterestRateUpdateRate()/20), Instant.now(), player, true);
                }
                else if(cIntrest == false){
                    loan = new Loan(value, Config.getInterestRate(), (Config.getInterestRateUpdateRate()/20), Instant.now(), player, false);
                }
                return true;
            }
        }
        return false;
    }

    public void loadLoanGUI(Player player){
        Gui output = new Gui(5, "Loaning Panel");
        output = loadLoanBars(output, player);
        int noOfLoans = Main.loanMap.get(player.getUniqueId()).size();
        output = loadPayLoanPanes(noOfLoans, player, output);
        CommandSender cs = (CommandSender) player;
        output.update();
        output.show((HumanEntity)cs);
    }

    public Gui loadPayLoanPanes(int noOfLoans, Player player, Gui gui){
        StaticPane[] arr = generatePayLoanPanes(noOfLoans, player, gui);
        for (int i = 0; i < noOfLoans; i++){
            gui.addPane(arr[i]);
        }
        return gui;
    }

    public StaticPane[] generatePayLoanPanes(int noOfLoans, Player player, Gui gui){
        StaticPane[] output = new StaticPane[noOfLoans];
        for (int i = 0; i < noOfLoans; i++){
            StaticPane pane = new StaticPane((i+1), 3, 1, 1);
            ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
            ItemMeta itemMeta = item.getItemMeta();
            itemMeta.setLore(Arrays.asList(ChatColor.WHITE + "Click to pay-back your loan"));
            itemMeta.setDisplayName(ChatColor.RED + "Pay-Back Loan");
            item.setItemMeta(itemMeta);
            final int finalI = i;
            GuiItem gItem = new GuiItem(item, event ->{
                event.setCancelled(true);
                player.getOpenInventory().close();
                LoanEventHandler.payLoan(player, finalI);
                loadLoanGUI(player);
            });
            pane.addItem(gItem, 0, 0);
            output[i] = pane;
        }
        return output;
    }

    public boolean doesPlayerHaveTooManyLoans(Player player){
        ArrayList<Loan> map = Main.loanMap.get(player.getUniqueId());
        if (map == null){
            map = new ArrayList<Loan>();
            Main.loanMap.put(player.getUniqueId(), map);
            return false;
        }
        if (map.size() > 7){
            player.sendMessage(ChatColor.RED + "Error: " + ChatColor.YELLOW + "You have taken out too many loans already, pay one back to create another one");
            return true;
        }
        else{
            return false;
        }
    }

    public Gui loadLoanBars(Gui gui, Player player){
        int noOfLoans = calcnoOfLoans(player);
        ArrayList<Loan> map = Main.loanMap.get(player.getUniqueId());
        StaticPane[] barArray = new StaticPane[noOfLoans];
        if (noOfLoans < 5){
            for (int i = 0; i < noOfLoans; i++){
                Loan loan = map.get(i);
                StaticPane bar = new StaticPane((i+1), 1, 1, 1);
                ItemStack fill = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
                ItemMeta fillMeta = fill.getItemMeta();
                fillMeta.setDisplayName(ChatColor.GREEN + "Loan " + (i+1));
                Instant now = Instant.now();
                fillMeta.setLore(Arrays.asList(ChatColor.WHITE + "Loan Value: " + ChatColor.GREEN + Config.getCurrencySymbol() + AutoTuneGUIShopUserCommand.df2.format(loan.current_value),
                 ChatColor.WHITE + "Interest Rate: " + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df6.format(loan.interest_rate) + ChatColor.WHITE + "% per " + ChatColor.GREEN + (loan.interest_rate_update/60)+ ChatColor.WHITE + "min",
                 ChatColor.WHITE + "Compound Interest: " + loan.compound_enabled,
                 ChatColor.WHITE + "Duration: " + ChatColor.GREEN + AutoTuneGUIShopUserCommand.df5.format(Duration.between(loan.instant, now).toMinutes()) + "min"));
                fill.setItemMeta(fillMeta);
                bar.addItem(new GuiItem(fill, event ->{
                    event.setCancelled(true);
                }), 0, 0);
                barArray[i] = bar;
            }
            for (int i = 0; i < noOfLoans; i++){
                gui.addPane(barArray[i]);
            }
            return gui;
        }
        return gui;
    }

    public String generateCompoundEnabledLore(Loan loan){
        if (loan.compound_enabled){
            return (ChatColor.GREEN + "Enabled");
        }
        else{
            return (ChatColor.RED + "Disabled");
        }
    }

    public int calcnoOfLoans(Player player){
        ArrayList<Loan> map = Main.loanMap.get(player.getUniqueId());
        if (map == null){
            map = new ArrayList<Loan>();
            Main.loanMap.put(player.getUniqueId(), map);
            return 0;
        }
        return map.size();
    }

    public double largestLoanValue(ArrayList<Loan> map){
        double cache_value = 0.0;
        for (Loan loan : map){
            if (loan.current_value > cache_value){
                cache_value = loan.current_value;
            }
        }
        return cache_value;
    }
    
}
