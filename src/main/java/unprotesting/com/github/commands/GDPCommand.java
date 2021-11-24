package unprotesting.com.github.commands;

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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import unprotesting.com.github.Main;
import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.config.Config;

public class GDPCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String gdp, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender, args);
    }

    private boolean interpretCommand(CommandSender sender, String[] args){
        Player player = CommandUtil.closeInventory(sender);
        if (!(player.hasPermission("at.gdp") || player.hasPermission("at.admin"))){CommandUtil.noPermission(player);return true;}
        openGDPGui(sender);
        return true;
    }

    private void openGDPGui(CommandSender sender){
        CommandUtil.closeInventory(sender);
        ChestGui GUI = new ChestGui(5, "GDP and Economy Info");
        StaticPane pane = new StaticPane(9, 5);
        Main.getCache().getGDP_DATA().updateBalance();
        Main.getCache().getGDP_DATA().updateDebt();
        pane.addItem(getGDPGuiItem(), 3, 1);
        pane.addItem(getBalanceGuiItem(), 5, 1);
        pane.addItem(getDebtGuiItem(), 2, 2);
        pane.addItem(getLossGuiItem(), 4, 2);
        pane.addItem(getInflationGuiItem(), 6, 2);
        GUI.addPane(pane);
        GUI.show((HumanEntity) sender);
    }

    private GuiItem getGDPGuiItem(){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        double GDP = Main.getCache().getGDP_DATA().getGDP();
        double GDPperCapita = GDP/Main.getCache().getGDP_DATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "GDP");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "-> GDP: " + Config.getCurrencySymbol() + df.format(GDP) + ".",
            ChatColor.GOLD + "-> GDP per Capita: " + Config.getCurrencySymbol() + df.format(GDPperCapita) + ".",
            ChatColor.WHITE + "-> GDP is the total value of all",
            ChatColor.WHITE + "transactions on the server.",
            ChatColor.WHITE + "-> GDP per Capita is the average GDP a ",
            ChatColor.WHITE + "player has contributed to the server."
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
        double bal = Main.getCache().getGDP_DATA().getBalance();
        double balPerCapita = bal/Main.getCache().getGDP_DATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "Balance");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "-> Balance: " + Config.getCurrencySymbol() + df.format(bal) + ".",
            ChatColor.GOLD + "-> Balance per Capita: " + Config.getCurrencySymbol() + df.format(balPerCapita) + ".",
            ChatColor.WHITE + "-> Balance is the total server balance",
            ChatColor.WHITE + "(all player balances combined).",
            ChatColor.WHITE + "-> Balance per Capita is the average",
            ChatColor.WHITE + "players balance."
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
        double debt = Main.getCache().getGDP_DATA().getDebt();
        double debtPerCapita = debt/Main.getCache().getGDP_DATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "Debt");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "-> Debt: " + Config.getCurrencySymbol() + df.format(debt) + ".",
            ChatColor.GOLD + "-> Debt per Capita: " + Config.getCurrencySymbol() + df.format(debtPerCapita) + ".",
            ChatColor.WHITE + "-> Debt is the total server debt.",
            ChatColor.WHITE + "-> Debt per Capita is the average",
            ChatColor.WHITE +  "debt of each player."
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
        double loss = Main.getCache().getGDP_DATA().getLoss();
        double lossPerCapita = loss/Main.getCache().getGDP_DATA().getPlayerCount();
        meta.setDisplayName(ChatColor.AQUA + "Loss");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "-> Loss: " + Config.getCurrencySymbol() + df.format(loss) + ".",
            ChatColor.GOLD + "-> Loss per Capita: " + Config.getCurrencySymbol() + df.format(lossPerCapita) + ".",
            ChatColor.WHITE + "-> Loss is the total balance",
            ChatColor.WHITE + "lost to fees.",
            ChatColor.WHITE + "-> Loss per Capita is the average",
            ChatColor.WHITE + "loss per player."
        }));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
        });
        return gItem;
    }

    private GuiItem getInflationGuiItem(){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        ItemStack item = new ItemStack(Material.BAMBOO);
        ItemMeta meta = item.getItemMeta();
        double inflation = Main.getCache().getGDP_DATA().getInflation();
        meta.setDisplayName(ChatColor.AQUA + "Inflation");
        meta.setLore(Arrays.asList(new String[]{
            ChatColor.GOLD + "-> Inflation: " + df.format(inflation) + "%.",
            ChatColor.WHITE + "-> Inflation is the average change",
            ChatColor.WHITE + "in prices in the last 24 hours.",
        }));
        item.setItemMeta(meta);
        GuiItem gItem = new GuiItem(item, event ->{
            event.setCancelled(true);
        });
        return gItem;
    }


    
}
