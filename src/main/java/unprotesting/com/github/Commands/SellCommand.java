package unprotesting.com.github.commands;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import unprotesting.com.github.commands.util.CommandUtil;
import unprotesting.com.github.commands.util.FunctionsUtil;

public class SellCommand implements CommandExecutor{

    @Override
    public boolean onCommand(CommandSender sender, Command command, String sell, String[] args) {
        if (!CommandUtil.checkIfSenderPlayer(sender)){return true;}
        return interpretCommand(sender);
    }

    private boolean interpretCommand(CommandSender sender){
        Player player = CommandUtil.closeInventory(sender);
        if (!(player.hasPermission("at.sell") || player.isOp())){CommandUtil.noPermssion(player);return true;}
        setupSellGUI(sender);
        return true;
    }

    private void setupSellGUI(CommandSender sender){
        Player player = CommandUtil.closeInventory(sender);
        ChestGui gui = new ChestGui(5, "Sell Panel");
        gui.setOnClose(event ->{
            for (ItemStack item : gui.getInventory().getStorageContents()){
                FunctionsUtil.sellCustomItem(player, item, false);
            }
        });
        gui.show((HumanEntity)sender);
    }
    
}
