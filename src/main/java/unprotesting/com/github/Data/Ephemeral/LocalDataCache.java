package unprotesting.com.github.Data.Ephemeral;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.Objects.Section;
import unprotesting.com.github.Config.Config;
import unprotesting.com.github.Data.CSV.CSVReader;
import unprotesting.com.github.Data.Ephemeral.Data.EnchantmentData;
import unprotesting.com.github.Data.Ephemeral.Data.ItemData;
import unprotesting.com.github.Data.Ephemeral.Data.LoanData;
import unprotesting.com.github.Data.Ephemeral.Data.MaxBuySellData;
import unprotesting.com.github.Data.Ephemeral.Data.TransactionData;
import unprotesting.com.github.Data.Ephemeral.Data.TransactionData.TransactionPositionType;
import unprotesting.com.github.Data.Ephemeral.Other.PlayerSaleData;
import unprotesting.com.github.Data.Ephemeral.Other.Sale;
import unprotesting.com.github.Data.Ephemeral.Other.Sale.SalePositionType;
import unprotesting.com.github.Data.Persistent.TimePeriods.EnchantmentsTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.ItemTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.LoanTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.TransactionsTimePeriod;
import unprotesting.com.github.Logging.Logging;

//  Global functions file between ephemeral and persistent storage

public class LocalDataCache {

    //  Globally accessable caches for persistent storage

    @Getter
    private ConcurrentHashMap<String, ItemData> ITEMS;
    @Getter
    private ConcurrentHashMap<String, EnchantmentData> ENCHANTMENTS;
    @Getter
    private List<LoanData> LOANS;
    @Getter
    private List<TransactionData> TRANSACTIONS;
    @Getter
    private ConcurrentHashMap<Player, PlayerSaleData> PLAYER_SALES;
    @Getter
    private List<Section> SECTIONS;
    @Getter
    private ConcurrentHashMap<String, MaxBuySellData> MAX_PURCHASES;
    @Getter
    private ConcurrentHashMap<String, Double> PERCENTAGE_CHANGES;

    private int size;

    public LocalDataCache(){
        this.ITEMS = new ConcurrentHashMap<String, ItemData>();
        this.ENCHANTMENTS = new ConcurrentHashMap<String, EnchantmentData>();
        this.LOANS = new ArrayList<LoanData>();
        this.TRANSACTIONS = new ArrayList<TransactionData>();
        this.PLAYER_SALES = new ConcurrentHashMap<Player, PlayerSaleData>();
        this.SECTIONS = new ArrayList<Section>();
        this.MAX_PURCHASES = new ConcurrentHashMap<String, MaxBuySellData>();
        this.PERCENTAGE_CHANGES = new ConcurrentHashMap<String, Double>();
        this.size = Main.database.map.size();
        init();
    }

    //  Add a new sale to related maps depending on type, item, etc.
    public void addSale(Player player, String item, double price, int amount, SalePositionType position){
        PlayerSaleData playerSaleData = getPlayerSaleData(player);
        playerSaleData.addSale(item, amount, position);
        try{
            switch(position){
                case BUY:
                    this.ITEMS.get(item).increaseBuys(amount);
                    this.TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.BI));
                    break;
                case SELL:
                    this.ITEMS.get(item).increaseSells(amount);
                    this.TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.SI));
                    break;
                case EBUY:
                    this.ENCHANTMENTS.get(item).increaseBuys(amount);
                    this.TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.BE));
                    break;
                case ESELL:
                    this.ENCHANTMENTS.get(item).increaseSells(amount);
                    this.TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.SE));
                    break;
                default:
                    break;
            }
        }
        catch(NullPointerException | IllegalArgumentException e){
            Logging.error(4);
            System.out.println("Cannot parse " + item + " into " + position.toString());
        }
    }

    //  Add a new loan to ephemeral storage
    public void addLoan(double value, double intrest_rate, Player player){
        this.LOANS.add(new LoanData(value, intrest_rate, player));
        Collections.sort(LOANS);
    }

    //  Get item price
    public double getItemPrice(String item, boolean sell){
        Double price;
        try{
            price = this.ITEMS.get(item).getPrice();
        }
        catch(NullPointerException e){
            try{
                price = getEnchantmentPrice(item, sell);
                return price;
            }
            catch(NullPointerException e2){
                return 0;
            }
        };
        if (!sell){
            return price;
        }
        Double spd = Config.getSellPriceDifference();
        if (Main.dfiles.getShops().getConfigurationSection("shops").getConfigurationSection(item).contains("sell-difference")){
            spd = Main.dfiles.getShops().getConfigurationSection("shops").getConfigurationSection(item).getDouble("sell-difference");
        }
        return (price - price*spd*0.01);
    }

    //  Get enchantment price
    public double getEnchantmentPrice(String enchantment, boolean sell){
        Double price;
        try{
            price = this.ENCHANTMENTS.get(enchantment).getPrice();
        }
        catch(NullPointerException e){
            return 0;
        };
        if (!sell){
            return price;
        }
        Double spd = Config.getSellPriceDifference();
        if (Main.dfiles.getEnchantments().getConfigurationSection("enchantments").getConfigurationSection(enchantment).contains("sell-difference")){
            spd = Main.dfiles.getEnchantments().getConfigurationSection("enchantments").getConfigurationSection(enchantment).getDouble("sell-difference");
        }
        return (price - price*spd*0.01);
    }

    //  Get enchantement ratio
    public double getEnchantmentRatio(String enchantment){
        Double price;
        try{
            price = this.ENCHANTMENTS.get(enchantment).getRatio();
        }
        catch(NullPointerException e){
            return 0;
        };
        return price;
    }

    //  Get price for adding an enchantment to an item
    public double getOverallEnchantmentPrice(String enchantment, double item_price, boolean sell){
        double price = getEnchantmentPrice(enchantment, sell);
        double ratio = getEnchantmentRatio(enchantment);
        return (price + ratio*item_price);
    }

    public int getBuysLeft(String item, Player player){
        PlayerSaleData pdata = PLAYER_SALES.get(player);
        Integer max;
        try{
            max = MAX_PURCHASES.get(item).getBuys();
        }
        catch(NullPointerException e){
            return 9999;
        }
        try{
            pdata.getBuys().isEmpty();
        }
        catch(NullPointerException e){
            return max;
        }
        int amount = 0;
        for (Sale sale : pdata.getBuys()){
            if (sale.getItem().equals(item)){
                amount += sale.getAmount();
            }
        }
        return max-amount;
    }

    public int getSellsLeft(String item, Player player){
        PlayerSaleData pdata = PLAYER_SALES.get(player);
        Integer max;
        try{
            max = MAX_PURCHASES.get(item).getSells();
        }
        catch(NullPointerException e){
            return 999;
        }
        try{
            pdata.getSells().isEmpty();
        }
        catch(NullPointerException e){
            return max;
        }
        int amount = 0;
        for (Sale sale : pdata.getSells()){
            if (sale.getItem().equals(item)){
                amount += sale.getAmount();
            }
        }
        return max-amount;
    }

    public String getPChangeString(String item){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        Double change = this.PERCENTAGE_CHANGES.get(item);
        if (change == null){
            return (ChatColor.GRAY + "%" + 0.0);
        }
        if (change < 0){
            return (ChatColor.RED + "%" + df.format(change));
        }
        if (change > 0){
            return (ChatColor.GREEN + "%" + df.format(change));
        }
        else{
            return (ChatColor.GRAY + "%" + 0.0);
        }
    }


    //  Initialize cache from configurations and relavent files
    private void init(){
        loadShopDataFromFile();
        loadShopDataFromData();
        loadEnchantmentDataFromFile();
        loadEnchantmentDataFromData();
        loadLoanDataFromData();
        loadTransactionDataFromData();
        loadSectionDataFromFile();
    }

    //  Get current cache for a players PlayerData object
    private PlayerSaleData getPlayerSaleData(Player player){
        PlayerSaleData playerSaleData = new PlayerSaleData();
        if (this.PLAYER_SALES.contains(player)){
            playerSaleData = this.PLAYER_SALES.get(player);
        }
        return playerSaleData;
    }

    private void loadShopDataFromFile(){
        ConfigurationSection config = Main.dfiles.getShops().getConfigurationSection("shops");
        Set<String> set = config.getKeys(false);
        ConcurrentHashMap<String, Double> map = new ConcurrentHashMap<String, Double>();
        try {
            if (Config.isReadFromCSV()){
                map = CSVReader.readData();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (String key : set){
            ConfigurationSection section = config.getConfigurationSection(key);
            ItemData data = new ItemData(section.getDouble("price"));
            if (Config.isReadFromCSV()){
                data = new ItemData(map.get(key));
            }
            MaxBuySellData mbsdata = new MaxBuySellData(section.getInt("max-buy"), section.getInt("max-sell"));
            this.MAX_PURCHASES.put(key, mbsdata);
            this.ITEMS.put(key, data);
        }
    }

    private void loadShopDataFromData(){
        if (size < 1){
            for (String str : this.ITEMS.keySet()){
                this.PERCENTAGE_CHANGES.put(str, 0.0);
            }
            return;
        }
        double a = Config.getTimePeriod()/1440;
        double b = 1/a;
        int tpInDay = (int) Math.floor(b);
        ItemTimePeriod ITP2;
        ItemTimePeriod ITP = Main.database.map.get(size-1).getItp();
        ITP2 = ITP;
        if (size-1 > tpInDay){
            ITP2 = Main.database.map.get(size-tpInDay).getItp();
        }
        else if (size-1 <= tpInDay){
            ITP2 = Main.database.map.get(0).getItp();
        }
        int i = 0;
        for (String item : ITP.getItems()){
            ItemData data = new ItemData(ITP.getPrices()[i]);
            this.ITEMS.put(item, data);
            double pChange = (ITP2.getPrices()[i]-ITP.getPrices()[i])/ITP.getPrices()[i]*100;
            this.PERCENTAGE_CHANGES.put(item, pChange);
            i++;
        }
    }

    private void loadEnchantmentDataFromFile(){
        ConfigurationSection config = Main.dfiles.getEnchantments().getConfigurationSection("enchantments");
        Set<String> set = config.getKeys(false);
        for (String key : set){
            ConfigurationSection sec = config.getConfigurationSection(key);
            EnchantmentData data = new EnchantmentData(sec.getDouble("price"), sec.getDouble("ratio"));
            this.ENCHANTMENTS.put(key, data);
        }
    }

    private void loadEnchantmentDataFromData(){
        if (size < 1){
            return;
        }
        EnchantmentsTimePeriod ETP = Main.database.map.get(size-1).getEtp();
        int i = 0;
        for (String item : ETP.getItems()){
            EnchantmentData data = new EnchantmentData(ETP.getPrices()[i], ETP.getRatios()[i]);
            this.ENCHANTMENTS.put(item, data);
            i++;
        }
    }

    private void loadLoanDataFromData(){
        this.LOANS.clear();
        for (Integer pos : Main.database.map.keySet()){
            LoanTimePeriod LTP = Main.database.map.get(pos).getLtp();
            for (int i = 0; i < LTP.getValues().length; i++){
                UUID uuid = UUID.fromString(LTP.getPlayers()[i]);
                Player player = Bukkit.getPlayer(uuid);
                LoanData data = new LoanData(LTP.getValues()[i], LTP.getIntrest_rates()[i], player, LTP.getTime()[i]);
                this.LOANS.add(data);
            }
        }
        Collections.sort(this.LOANS);
    }

    private void loadTransactionDataFromData(){
        if (size < 1){
            return;
        }
        this.TRANSACTIONS.clear();
        for (Integer pos : Main.database.map.keySet()){
            TransactionsTimePeriod TTP = Main.database.map.get(pos).getTtp();
            for (int i = 0; i < TTP.getPrices().length; i++){
                UUID uuid = UUID.fromString(TTP.getPlayers()[i]);
                Player player = Bukkit.getPlayer(uuid);
                TransactionPositionType position = TransactionPositionType.valueOf(TTP.getPositions()[i]);
                TransactionData data = new TransactionData(player, TTP.getItems()[i], TTP.getAmounts()[0], TTP.getPrices()[i], position, TTP.getTime()[i]);
                this.TRANSACTIONS.add(data);
            }
        }
        Collections.sort(this.TRANSACTIONS);
    }

    private void loadSectionDataFromFile(){
        ConfigurationSection csection = Main.dfiles.getShops().getConfigurationSection("sections");
        for (String section : csection.getKeys(false)){
            ConfigurationSection icsection = csection.getConfigurationSection(section);
            SECTIONS.add(new Section(section, icsection.getString("block"), icsection.getBoolean("back-menu-button-enabled"),
             icsection.getInt("position"), icsection.getString("background")));
        }
        csection = Main.dfiles.getEnchantments().getConfigurationSection("config");
        SECTIONS.add(new Section("Enchantments", csection.getString("block"), csection.getBoolean("back-menu-button-enabled"),
             csection.getInt("position"), csection.getString("background")));
    }

}
