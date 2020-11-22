package unprotesting.com.github.Commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.stefvanschie.inventoryframework.Gui;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.OutlinePane;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import unprotesting.com.github.Main;
import unprotesting.com.github.util.ChatHandler;
import unprotesting.com.github.util.Config;
import unprotesting.com.github.util.TextHandler;

public class AutoTuneAutoTuneConfigCommand implements CommandExecutor {

    public static List<Player> pList = new ArrayList<Player>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String atconfig, String[] args) {
        if (command.getName().equalsIgnoreCase("atconfig")) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(sender, "&cPlayers only.");
                return true;
            }
            if (sender instanceof Player) {
                Player p = (Player) sender;
                if (args.length == 0){
                    if (p.hasPermission("at.atconfig") || p.isOp()) {
                        Config.loadDefaults();
                        openConfigGUI(p, sender);
                        return true;
                    } else {
                        TextHandler.noPermssion(p);
                        return true;
                    }
                }
                if (args.length > 0){
                    return false;
                }
            }
        }
        return false;
    }

    public void openConfigGUI(Player player, CommandSender sender) {
        OutlinePane configPane = new OutlinePane(0, 0, 5, 1);
        Gui configGUI = new Gui(1, "Configuration Panel");
        GuiItem[] guiItemArr = createConfigMenuGuiItems(sender);
        for (int i = 0; i < guiItemArr.length; i++) {
            configPane.addItem(guiItemArr[i]);
        }
        configGUI.addPane(configPane);
        configGUI.update();
        configGUI.show((HumanEntity) sender);
    }

    public GuiItem[] createConfigMenuGuiItems(CommandSender sender) {
        GuiItem[] guiItemArr = new GuiItem[5];
        guiItemArr[0] = new GuiItem(createGeneralSetting(), event -> {
            event.getWhoClicked().getOpenInventory().close();
            loadGeneralSettings((Player) event.getWhoClicked(), sender);
        });
        guiItemArr[1] = new GuiItem(createBasicAdvancedPricingModelSetting(), event -> {
            event.getWhoClicked().getOpenInventory().close();
            loadBasicAdvancedPricingModelSettings((Player) event.getWhoClicked(), sender);
        });
        guiItemArr[2] = new GuiItem(createExponentialPricingModelSetting(), event -> {
            event.getWhoClicked().getOpenInventory().close();
            loadExponentialPricingModelSettings((Player) event.getWhoClicked(), sender);
        });
        guiItemArr[3] = new GuiItem(createOtherEcononomySettings(), event -> {
            event.getWhoClicked().getOpenInventory().close();
            loadOtherEcononomySettings((Player) event.getWhoClicked(), sender);
        });
        guiItemArr[4] = new GuiItem(createOtherSettings(), event -> {
            event.getWhoClicked().getOpenInventory().close();
            loadOtherSettings((Player) event.getWhoClicked(), sender);
        });
        return guiItemArr;
    }

    public void loadGeneralSettings(Player p, CommandSender sender) {
        OutlinePane generalConfigPane = new OutlinePane(0, 0, 9, 2);
        Gui generalConfigGUI = new Gui(2, "General-Configuration Panel");
        createGeneralConfigMenuGuiItems(generalConfigGUI, generalConfigPane, p, sender);
        generalConfigGUI.addPane(generalConfigPane);
        generalConfigGUI.update();
        generalConfigGUI.show((HumanEntity) sender);
    }

    public void loadBasicAdvancedPricingModelSettings(Player p, CommandSender sender) {
        OutlinePane BAConfigPane = new OutlinePane(0, 0, 9, 1);
        Gui BAConfigGUI = new Gui(1, "Basic/Advanced Pricing-Model-Configuration Panel");
        createBasicAdvancedPricingModelConfigMenuGuiItems(BAConfigGUI, BAConfigPane, p, sender);
        BAConfigGUI.addPane(BAConfigPane);
        BAConfigGUI.update();
        BAConfigGUI.show((HumanEntity) sender);
    }

    public void loadExponentialPricingModelSettings(Player p, CommandSender sender){
        OutlinePane EPConfigPane = new OutlinePane(0, 0, 9, 1);
        Gui EPConfigGUI = new Gui(1, "Exponential Pricing-Model-Configuration Panel");
        createExponentialPricingModelMenuGuiItems(EPConfigGUI, EPConfigPane, p, sender);
        EPConfigGUI.addPane(EPConfigPane);
        EPConfigGUI.update();
        EPConfigGUI.show((HumanEntity) sender);
    }

    public void loadOtherEcononomySettings(Player p, CommandSender sender){
        OutlinePane OEConfigPane = new OutlinePane(0, 0, 9, 1);
        Gui OEConfigGUI = new Gui(1, "Other Econonomy Configuration Panel");
        createOtherEcononomyMenuGuiItems(OEConfigGUI, OEConfigPane, p, sender);
        OEConfigGUI.addPane(OEConfigPane);
        OEConfigGUI.update();
        OEConfigGUI.show((HumanEntity) sender);
    }

    public void loadOtherSettings(Player p, CommandSender sender){
        OutlinePane OConfigPane = new OutlinePane(0, 0, 9, 1);
        Gui OConfigGUI = new Gui(1, "Other-Configuration Panel");
        createOtherMenuGuiItems(OConfigGUI, OConfigPane, p, sender);
        OConfigGUI.addPane(OConfigPane);
        OConfigGUI.update();
        OConfigGUI.show((HumanEntity) sender);
    }

    public ItemStack createOtherSettings(){
        ItemStack is = new ItemStack(Material.matchMaterial("STONE"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + "Other Settings");
        im.setLore(Arrays.asList(ChatColor.WHITE + "Click to Change Settings"));
        is.setItemMeta(im);
        return is;
    }

    public ItemStack createOtherEcononomySettings(){
        ItemStack is = new ItemStack(Material.matchMaterial("GOLD_INGOT"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + "Other Econonomy Settings");
        im.setLore(Arrays.asList(ChatColor.WHITE + "Click to Change Settings"));
        is.setItemMeta(im);
        return is;
    }

    public ItemStack createExponentialPricingModelSetting(){
        ItemStack is = new ItemStack(Material.matchMaterial("Piston"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + "Exponential Pricing-Model Settings");
        im.setLore(Arrays.asList(ChatColor.WHITE + "Click to Change Settings"));
        is.setItemMeta(im);
        return is;
    }

    public ItemStack createBasicAdvancedPricingModelSetting(){
        ItemStack is = new ItemStack(Material.matchMaterial("REDSTONE"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + "Basic/Advanced Pricing-Model Settings");
        im.setLore(Arrays.asList(ChatColor.WHITE + "Click to Change Settings"));
        is.setItemMeta(im);
        return is;
    }

    public ItemStack createGeneralSetting(){
        ItemStack is = new ItemStack(Material.matchMaterial("GRASS_BLOCK"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + "General Settings");
        im.setLore(Arrays.asList(ChatColor.WHITE + "Click to Change Settings"));
        is.setItemMeta(im);
        return is;
    }

    public void createGeneralConfigMenuGuiItems(Gui configGui, OutlinePane generalConfigPane, Player player, CommandSender sender) {
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Web Server", String.valueOf(Config.isWebServer())), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Web Server", "web-server-enabled", "boolean", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Port", Config.getPort()), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Port", "port", "integer", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Server Name", Config.getServerName()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Server Name", "server-name", "string", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Pricing Model", Config.getPricingModel()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Pricing Model", "pricing-model", "string", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Time Period", Config.getTimePeriod()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Time Period", "time-period", "integer", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Menu Rows", Config.getMenuRows()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Menu Rows", "menu-rows", "integer", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Menu Title", Config.getMenuTitle()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Menu Title", "menu-title", "string", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("No Permission Message", Config.getNoPermission()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "No Permission Message", "no-permission", "string", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Auto Sell Update Period", Config.getAutoSellUpdatePeriod()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Auto Sell Update Period", "auto-sell-update-period", "integer", sender);}));
        generalConfigPane.addItem(new GuiItem(createItemStackWithMeta("Auto Sell Profit Update Period", Config.getAutoSellProfitUpdatePeriod()),
                event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Auto Sell Profit Update Period", "auto-sell-profit-update-period", "integer", sender);
                    resetPlayerToSettings(player, sender);}));
    }

    public void createBasicAdvancedPricingModelConfigMenuGuiItems(Gui configGui, OutlinePane BAConfigPane, Player player, CommandSender sender) {
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Volatility Algorithm", String.valueOf(Config.getBasicVolatilityAlgorithim())), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Volatility Algorithm", "Volatility-Algorithim", "string", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Sell Price Difference", Config.getSellPriceDifference()), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Sell Price Difference", "sell-price-difference", "double", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Fixed Max Volatility", Config.getBasicMaxFixedVolatility()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Fixed Max Volatility", "Fixed-Max-Volatility", "double", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Fixed Min Volatility", Config.getBasicMinFixedVolatility()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Fixed Min Volatility", "Fixed-Min-Volatility", "double", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Variable Max Volatility", Config.getBasicMaxVariableVolatility()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Variable Max Volatility", "Variable-Max-Volatility", "double", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Variable Min Volatility", Config.getBasicMinVariableVolatility()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Variable Min Volatility", "Variable-Min-Volatility", "double", sender);}));
    }

    public void createExponentialPricingModelMenuGuiItems(Gui configGui, OutlinePane BAConfigPane, Player player, CommandSender sender) {
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Data Selection M", Config.getDataSelectionM()), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Data Selection M", "data-selection-m", "double", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Data Selection Z", Config.getDataSelectionZ()), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Data Selection Z", "data-selection-z", "double", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Data Selection C", Config.getDataSelectionC()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Data Selection C", "data-selection-c", "double", sender);}));
    }

    public void createOtherEcononomyMenuGuiItems(Gui configGui, OutlinePane BAConfigPane, Player player, CommandSender sender) {
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Update Prices When Inactive", String.valueOf(Config.isUpdatePricesWhenInactive())), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Update Prices When Inactive", "update-prices-when-inactive", "boolean", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Currency Symbol", String.valueOf(Config.getCurrencySymbol())), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Currency Symbol", "currency-symbol", "string", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Sell Price Difference Variation Enabled", String.valueOf(Config.isSellPriceDifferenceVariationEnabled())), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Sell Price Difference Variation Enabled", "sell-price-difference-variation-enabled", "boolean", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Sell Price Difference Variation Start", Config.getSellPriceDifferenceVariationStart()), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Sell Price Difference Variation Start", "sell-price-differnence-variation-start", "double", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Sell Price Variation Time Period", Config.getSellPriceVariationTimePeriod()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Sell Price Variation Time Period", "sell-price-variation-time-period", "integer", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Sell Price Variation Update Period", Config.getSellPriceVariationUpdatePeriod()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Sell Price Variation Update Period", "sell-price-variation-update-period", "integer", sender);}));    
    }

    public void createOtherMenuGuiItems(Gui configGui, OutlinePane BAConfigPane, Player player, CommandSender sender) {
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Debug Enabled", String.valueOf(Config.isDebugEnabled())), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Debug Enabled", "debug-enabled", "boolean", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Checksum Header Bypass", String.valueOf(Config.isChecksumHeaderBypass())), event -> {
            Player p = (Player) event.getWhoClicked();
            checkForMessage(p, "Checksum Header Bypass", "checksum-header-bypass", "boolean", sender);}));
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Economy Shop Config", Config.getEconomyShopConfig()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Economy GUI-Shop Config", "economy-shop-config", "string", sender);}));  
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Economy Shop Config Sell Value", Config.getShopConfigGUIShopSellValue()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Economy GUI-Shop Config Sell Value", "economy-shop-config", "double", sender);}));  
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Tutorial", String.valueOf(Config.isTutorial())), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Tutorial", "tutorial", "boolean", sender);}));  
        BAConfigPane.addItem(new GuiItem(createItemStackWithMeta("Tutorial Message Period", Config.getTutorialMessagePeriod()), event -> {
                    Player p = (Player) event.getWhoClicked();
                    checkForMessage(p, "Tutorial Message Period", "tutorial-message-period", "integer", sender);}));         
    }

    public void resetPlayerToSettings(Player p, CommandSender sender){
        Config.loadDefaults();
        p.getOpenInventory().close();
        openConfigGUI(p, sender);
    }

    public void checkForMessage(Player p, String type, String configSetting, String setting, CommandSender sender) {
        p.getOpenInventory().close();
        if (ChatHandler.message == null){
            p.sendMessage(ChatColor.GOLD + "Enter a new value for " + ChatColor.GREEN + type + ChatColor.GOLD + " in chat: ");
            pList.add(p);
            messageCheckRunnable(p, type, configSetting, setting, sender);
        }
        else{
            p.sendMessage(ChatColor.RED + "Please complete the answer for the previous setting first..");
        }
    }

    public void messageCheckRunnable(Player p, String type, String configSetting, String setting, CommandSender sender){
        new BukkitRunnable(){
			@Override
			public void run() {
                if (ChatHandler.message != null) {
                    if (setting.contains("string")){
                        Main.getMainConfig().set(configSetting, ChatHandler.message);
                        resetPlayerToSettings(p, sender);
                        this.cancel();
                    }
                    if (setting.contains("boolean")){
                        ChatHandler.message.toLowerCase();
                        Main.getMainConfig().set(configSetting, Boolean.parseBoolean(ChatHandler.message));
                        resetPlayerToSettings(p, sender);
                        this.cancel();
                    }
                    if (setting.contains("integer")){
                        Main.getMainConfig().set(configSetting, Integer.parseInt(ChatHandler.message));
                        resetPlayerToSettings(p, sender);
                        this.cancel();
                    }
                    if (setting.contains("double")){
                        Main.getMainConfig().set(configSetting, Double.parseDouble(ChatHandler.message));
                        resetPlayerToSettings(p, sender);
                        this.cancel();
                    }
                    else{
                        resetPlayerToSettings(p, sender);
                        this.cancel();
                    }
                    try {
                        Main.getMainConfig().save(Main.getConfigf());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                        p.sendMessage(ChatColor.GOLD + "Changed " + ChatColor.GREEN + type + ChatColor.GOLD + " to " + ChatColor.GREEN + ChatHandler.message);
                        pList.remove(p);
                        ChatHandler.message = null;
                }
			}
        }.runTaskTimer(Main.getINSTANCE(), 0L, 5L);
    }

    public ItemStack createItemStackWithMeta(String setting, int configSetting){
        ItemStack is = new ItemStack(Material.matchMaterial("LEVER"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + setting + " : " + configSetting);
        List<String> list = new ArrayList<String>();
        list.add(" ");
        list.add(ChatColor.WHITE + "Left click to change");
        list.add(ChatColor.GRAY + "Changing a setting will remove all \'##\' config notes!");
        im.setLore(list);
        is.setItemMeta(im);
        return is;
    }

    public ItemStack createItemStackWithMeta(String setting, String configSetting){
        ItemStack is = new ItemStack(Material.matchMaterial("LEVER"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + setting + " : " + configSetting);
        List<String> list = new ArrayList<String>();
        list.add(" ");
        list.add(ChatColor.WHITE + "Left click to change");
        list.add(ChatColor.GRAY + "Changing a setting will remove all \'##\' config notes!");
        im.setLore(list);
        is.setItemMeta(im);
        return is;
    }

    public ItemStack createItemStackWithMeta(String setting, double configSetting){
        ItemStack is = new ItemStack(Material.matchMaterial("LEVER"));
        ItemMeta im = is.getItemMeta();
        im.setDisplayName(ChatColor.GREEN + setting + " : " + configSetting);
        List<String> list = new ArrayList<String>();
        list.add(" ");
        list.add(ChatColor.WHITE + "Left click to change");
        list.add(ChatColor.GRAY + "Changing a setting will remove all \'##\' config notes!");
        im.setLore(list);
        is.setItemMeta(im);
        return is;
    }


    
}