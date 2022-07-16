package unprotesting.com.github.commands;

import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import unprotesting.com.github.AutoTune;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.Database;
import unprotesting.com.github.data.Loan;
import unprotesting.com.github.util.EconomyUtil;
import unprotesting.com.github.util.Format;

/**
 * The command for creating, paying back and viewing loans.
 */
public class LoanCommand implements CommandExecutor {

    public LoanCommand(@NotNull AutoTune plugin) {
        plugin.getCommand("loan").setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player)) {
            Format.sendMessage(sender, "<red>This command is for players only.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            getTotalLoans(player);
            Format.sendMessage(player, "<gold>Usage: /loan <amount>/pay");
        } else if (args[0].equalsIgnoreCase("pay") || args[0].equalsIgnoreCase("payback")) {
            Database database = Database.get();
            for (Map.Entry<Long, Loan> entry : Database.get().getLoans().entrySet()) {
                Loan loan = entry.getValue();
                if (loan.getPlayer().equals(player.getUniqueId())) {

                    if (loan.isPaid()) {
                        continue;
                    }

                    if (loan.payBack()) {
                        Format.sendMessage(player, "<green>You have paid back your loan of "
                                + Format.currency(loan.getValue()) + ".");
                    } else {
                        Format.sendMessage(player,
                                "<red>You do not have enough money to pay back your loan.");
                    }
                    database.updateLoan(entry.getKey(), loan);

                }
            }
        } else {
            double value = 0;
            try {
                value = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                Format.sendMessage(player, "<red>Invalid amount.");
                return true;
            }

            if (value <= 0) {
                Format.sendMessage(player, "<red>Invalid amount.");
                return true;
            }

            if (EconomyUtil.getEconomy().getBalance(player) 
                <= value + value * 0.05 * Config.get().getInterest()) {
                Format.sendMessage(player, "<red>You do not have enough money.");
                return true;
            }

            double base = value;
            value += value * 0.01 * Config.get().getInterest();
            Loan loan = Loan.builder()
                    .value(value)
                    .base(base)
                    .player(player.getUniqueId())
                    .paid(false)
                    .build();
            Database.get().getLoans().put(System.currentTimeMillis(), loan);
            EconomyUtil.getEconomy().depositPlayer(player, base);
            getTotalLoans(player);
        }
        return true;
    }

    private void getTotalLoans(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        double total = 0;

        for (Loan loan : Database.get().getLoans().values()) {
            if (loan.getPlayer().equals(uuid)) {
                if (loan.isPaid()) {
                    continue;
                }

                total += loan.getValue();
            }
        }

        Format.sendMessage(player, "<green>You have " + Format.currency(total) + " in loans.");
    }

}
