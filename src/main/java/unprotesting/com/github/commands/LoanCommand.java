package unprotesting.com.github.commands;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.ephemeral.data.LoanData;

public class LoanCommand implements CommandExecutor{

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String loan, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender, args);
    }

    @Deprecated
    private boolean interpretCommand(CommandSender sender, String[] args){
        Player player = CommandUtil.closeInventory(sender);
        if (!(player.hasPermission("at.loan") || player.isOp())){CommandUtil.noPermssion(player);return true;}
        ChestGui gui = new ChestGui(6, "Loans");
        PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
        List<LoanData> loans = Main.getCache().getLOANS();
        List<OutlinePane> panes = new ArrayList<OutlinePane>();
        List<GuiItem> items;
        String player_uuid = player.getUniqueId().toString();
        if (args.length < 1){
            if (!player.hasPermission("at.loan.other") && !player.isOp()){
                items = getGuiItemsFromLoans(loans, player_uuid);
            }
            else{
                items = getGuiItemsFromLoans(loans, null);
            }
        }
        else if (args[0].equals("-p")){
            if (args[1].equals(player.getName())){
                items = getGuiItemsFromLoans(loans, player_uuid);
            }
            else if (!args[1].equals(player.getName()) && (!player.hasPermission("at.loan.other") && !player.isOp())){
                CommandUtil.noPermssion(player);
                return true;
            }
            else{
                OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(args[1]);
                items = getGuiItemsFromLoans(loans, offPlayer.getUniqueId().toString());
            }
        }
        else {
            double loanAmount;
            try{
                loanAmount = Double.parseDouble(args[0]);
            }
            catch(NumberFormatException e){
                return false;
            }
            Main.getCache().addLoan(loanAmount, Config.getInterestRate(), player);
            return true;
        }
        CommandUtil.loadGuiItemsIntoPane(items, gui, pages, panes, "GRAY_STAINED_GLASS_PANE", sender);
        return true;
    }

    private List<GuiItem> getGuiItemsFromLoans(List<LoanData> loans, String player_uuid) {
        List<GuiItem> output = new ArrayList<GuiItem>();
        Collections.sort(loans);
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        for (LoanData data : loans){
            if (player_uuid != null){
                if (!data.getPlayer().equals(player_uuid)){
                    continue;
                }
            }
            ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            OfflinePlayer player = Bukkit.getPlayer(UUID.fromString(data.getPlayer()));
            meta.setDisplayName(ChatColor.GREEN + Config.getCurrencySymbol() + df.format(data.getValue()));
            meta.setLore(Arrays.asList(new String[]{
                ChatColor.WHITE + "Player: " + ChatColor.GOLD + player.getName(),
                ChatColor.WHITE + "Interest Rate: " + ChatColor.GOLD + data.getInterest_rate()
                 + "% per " + df.format(Config.getInterestRateUpdateRate()/60) + "min",
                ChatColor.WHITE + "Date: " + ChatColor.GOLD + data.getDate().format(formatter),
                ChatColor.GREEN + "Click to pay-back loan!"
            }));
            item.setItemMeta(meta);
            GuiItem gItem = new GuiItem(item, event ->{
                data.payBackLoan();
                event.setCancelled(true);
            });
            output.add(gItem);
        }
        return output;
    }



    
    
}
