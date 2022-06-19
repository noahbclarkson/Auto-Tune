package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
import unprotesting.com.github.data.objects.Loan;
import unprotesting.com.github.economy.EconomyFunctions;

public class LoanCommand implements CommandExecutor {

  private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  @Override
  public boolean onCommand(CommandSender sender, Command command, String loan, String[] args) {

    if (!CommandUtil.checkIfSenderPlayer(sender)) {
      return true;
    }

    return interpretCommand(sender, args);
  }

  @Deprecated
  private boolean interpretCommand(CommandSender sender, String[] args) {

    Player player = CommandUtil.closeInventory(sender);

    if (!(player.hasPermission("at.loan") || player.hasPermission("at.admin"))) {

      CommandUtil.noPermission(player);
      return true;

    }

    ChestGui gui = new ChestGui(6, "Loans");
    PaginatedPane pages = new PaginatedPane(0, 0, 9, 6);
    List<OutlinePane> panes = new ArrayList<OutlinePane>();
    List<GuiItem> items;
    UUID uuid = player.getUniqueId();

    if (args.length < 1) {

      if (!player.hasPermission("at.loan.other") && !player.hasPermission("at.admin")) {
        items = getGuiItemsFromLoans(uuid);
      } else {
        items = getGuiItemsFromLoans(null);
      }

    } else if (args[0].equals("-p")) {

      if (args[1].equals(player.getName())) {
        items = getGuiItemsFromLoans(uuid);
      } else if (!args[1].equals(player.getName())
          && (!player.hasPermission("at.loan.other") && !player.hasPermission("at.admin"))) {

        CommandUtil.noPermission(player);
        return true;

      } else {

        OfflinePlayer offPlayer = Bukkit.getOfflinePlayer(args[1]);
        items = getGuiItemsFromLoans(offPlayer.getUniqueId());

      }

    } else {

      double loanAmount;

      try {
        loanAmount = Double.parseDouble(args[0]);
      } catch (NumberFormatException e) {
        return false;
      }

      if (loanAmount < 0) {

        player.sendMessage(ChatColor.RED  
            + "Loan amount must be greater than " + Config.getConfig().getCurrencySymbol()
            + "0.0 to take out a loan.");

        return true;
      }

      double bal = EconomyFunctions.getEconomy().getBalance(player);

      if (bal - loanAmount < Config.getConfig().getMaxDebt()) {

        if (bal < Config.getConfig().getMaxDebt()) {

          player.sendMessage(ChatColor.RED + "Your balance must be more than " 
              + Config.getConfig().getCurrencySymbol() + Config.getConfig().getMaxDebt() 
              + " to take out a loan.");

          return true;
        }

        player.sendMessage(ChatColor.RED + "Your balance minus your loans cannot be less than "
            + Config.getConfig().getCurrencySymbol() + Config.getConfig().getMaxDebt() + ".");

        return true;
      }

      EconomyFunctions.getEconomy().depositPlayer(player, loanAmount);

      Main.getInstance().getDb().addLoan(loanAmount, player);

      return true;

    }

    CommandUtil.loadGuiItemsIntoPane(items, gui, pages, panes,
        Material.GRAY_STAINED_GLASS_PANE, sender);

    return true;
  }

  private List<GuiItem> getGuiItemsFromLoans(UUID uuid) {

    List<GuiItem> output = new ArrayList<GuiItem>();
    DecimalFormat df = new DecimalFormat(Config.getConfig().getNumberFormat());

    for (long time : Main.getInstance().getDb().getLoans().keySet()) {

      for (Loan loan : Main.getInstance().getDb().getLoans().get(time)) {

        if (uuid != null && !loan.getPlayer().equals(uuid)) {
          continue;
        }

        ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        OfflinePlayer player = Bukkit.getPlayer(loan.getPlayer());
        LocalDateTime date = LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());

        meta.setDisplayName(ChatColor.GREEN 
            + Config.getConfig().getCurrencySymbol() + df.format(loan.getValue()));

        meta.setLore(Arrays.asList(new String[] {

            ChatColor.WHITE + "Player: " + ChatColor.GOLD + player.getName(),
          
            ChatColor.WHITE + "Interest Rate: " + ChatColor.GOLD + Config.getConfig().getInterestRate()
                + "% per " + df.format(
                Config.getConfig().getInterestRateUpdateRate() / 1200) + "min has been created.",

            ChatColor.WHITE + "Date: " + ChatColor.GOLD + date.format(formatter),
            ChatColor.GREEN + "Click to pay-back loan!"

        }));

        item.setItemMeta(meta);

        GuiItem guiItem = new GuiItem(item, event -> {

          loan.payBackLoan();
          event.setCancelled(true);

        });

        output.add(guiItem);
      }
    }
    return output;
  }

}
