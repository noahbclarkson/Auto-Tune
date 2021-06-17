package unprotesting.com.github.Commands;

import java.text.DecimalFormat;
import java.util.Arrays;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.Util.CommandUtil;
import unprotesting.com.github.Config.Config;

public class GDPCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String gdp, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender, args);
    }

    private boolean interpretCommand(CommandSender sender, String[] args){
        openGDPGui(sender);
        return true;
    }

    private void openGDPGui(CommandSender sender){
        ChestGui GUI = new ChestGui(3, "GDP and Economy Info");
        StaticPane pane = new StaticPane(9, 3);
        pane.addItem(getGDPGuiItem(), 1, 1);
        pane.addItem(getBalanceGuiItem(), 1, 3);
        pane.addItem(getDebtGuiItem(), 1, 6);
        pane.addItem(getLossGuiItem(), 1, 8);
        GUI.addPane(pane);
        GUI.show((HumanEntity) sender);
    }

    private GuiItem getGDPGuiItem(){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        double GDP = Main.getCache().getGDPDATA().getGDP();
        double GDPperCapita = GDP/Main.getCache().getGDPDATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "GDP");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "GDP: " + Config.getCurrencySymbol() + df.format(GDP) + ".",
            ChatColor.GOLD + "GDP per Capita: " + df.format(GDPperCapita) + ".",
            ChatColor.WHITE + "GDP is the total value of all transactions on the server.",
            ChatColor.WHITE + "GDP per Capita is the average GDP a player has contributed to the server."
        }));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
        });
        return gItem;
    }

    private GuiItem getBalanceGuiItem(){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        double bal = Main.getCache().getGDPDATA().getBalance();
        double balPerCapita = bal/Main.getCache().getGDPDATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "Balance");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "Balance: " + Config.getCurrencySymbol() + df.format(bal) + ".",
            ChatColor.GOLD + "Balance per Capita: " + df.format(balPerCapita) + ".",
            ChatColor.WHITE + "Balance is the total server balance (all player balances combined).",
            ChatColor.WHITE + "Balance per Capita is the average players balance."
        }));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
        });
        return gItem;
    }

    private GuiItem getDebtGuiItem(){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        double debt = Main.getCache().getGDPDATA().getDebt();
        double debtPerCapita = debt/Main.getCache().getGDPDATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "Debt");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "Debt: " + Config.getCurrencySymbol() + df.format(debt) + ".",
            ChatColor.GOLD + "Debt per Capita: " + df.format(debtPerCapita) + ".",
            ChatColor.WHITE + "Debt is the total server debt.",
            ChatColor.WHITE + "Debt per Capita is the average debt of each player."
        }));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
        });
        return gItem;
    }

    private GuiItem getLossGuiItem(){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        double loss = Main.getCache().getGDPDATA().getLoss();
        double lossPerCapita = loss/Main.getCache().getGDPDATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "Loss");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "Loss: " + Config.getCurrencySymbol() + df.format(loss) + ".",
            ChatColor.GOLD + "Loss per Capita: " + df.format(lossPerCapita) + ".",
            ChatColor.WHITE + "Loss is the total balance lost to fees.",
            ChatColor.WHITE + "Loss per Capita is the average loss per player."
        }));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
        });
        return gItem;
    }
    
}
