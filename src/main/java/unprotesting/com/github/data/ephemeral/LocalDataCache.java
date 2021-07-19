package unprotesting.com.github.data.ephemeral;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import lombok.Getter;
import lombok.Setter;
import unprotesting.com.github.Main;
import unprotesting.com.github.commands.objects.Section;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.csv.CSVReader;
import unprotesting.com.github.data.ephemeral.data.EconomyInfoData;
import unprotesting.com.github.data.ephemeral.data.EnchantmentData;
import unprotesting.com.github.data.ephemeral.data.GDPData;
import unprotesting.com.github.data.ephemeral.data.ItemData;
import unprotesting.com.github.data.ephemeral.data.LoanData;
import unprotesting.com.github.data.ephemeral.data.MaxBuySellData;
import unprotesting.com.github.data.ephemeral.data.TransactionData;
import unprotesting.com.github.data.ephemeral.data.TransactionData.TransactionPositionType;
import unprotesting.com.github.data.ephemeral.other.PlayerSaleData;
import unprotesting.com.github.data.ephemeral.other.Sale;
import unprotesting.com.github.data.ephemeral.other.Sale.SalePositionType;
import unprotesting.com.github.data.persistent.timeperiods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.GDPTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.ItemTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.LoanTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.TransactionsTimePeriod;
import unprotesting.com.github.logging.Logging;

//  Global functions file between ephemeral and persistent storage

public class LocalDataCache {

    //  Globally accessable caches for persistent storage

    @Getter
    private ConcurrentHashMap<String, ItemData> ITEMS;
    @Getter
    private ConcurrentHashMap<String, EnchantmentData> ENCHANTMENTS;
    @Getter @Setter
    private List<LoanData> LOANS,
                           NEW_LOANS;
    @Getter
    private List<TransactionData> TRANSACTIONS,
                                  NEW_TRANSACTIONS;
    @Getter
    private ConcurrentHashMap<String, PlayerSaleData> PLAYER_SALES;
    @Getter
    private List<Section> SECTIONS;
    @Getter
    private ConcurrentHashMap<String, MaxBuySellData> MAX_PURCHASES;
    @Getter
    private ConcurrentHashMap<String, Double> PERCENTAGE_CHANGES;
    @Getter @Setter
    private GDPData GDPDATA;
    @Getter
    private EconomyInfoData ECONOMYINFO;

    private int size;

    public LocalDataCache(){
        this.ITEMS = new ConcurrentHashMap<String, ItemData>();
        this.ENCHANTMENTS = new ConcurrentHashMap<String, EnchantmentData>();
        this.LOANS = new ArrayList<LoanData>();
        this.NEW_LOANS = new ArrayList<LoanData>();
        this.TRANSACTIONS = new ArrayList<TransactionData>();
        this.NEW_TRANSACTIONS = new ArrayList<TransactionData>();
        this.PLAYER_SALES = new ConcurrentHashMap<String, PlayerSaleData>();
        this.SECTIONS = new ArrayList<Section>();
        this.MAX_PURCHASES = new ConcurrentHashMap<String, MaxBuySellData>();
        this.PERCENTAGE_CHANGES = new ConcurrentHashMap<String, Double>();
        this.size = Main.getDatabase().map.size();
        init();
    }

    //  Add a new sale to related maps depending on type, item, etc.
    public void addSale(OfflinePlayer player, String item, double price, int amount, SalePositionType position){
        PlayerSaleData playerSaleData = getPlayerSaleData(player);
        playerSaleData.addSale(item, amount, position);
        UUID uuid = player.getUniqueId();
        this.PLAYER_SALES.put(player.getUniqueId().toString(), playerSaleData);
        String uuid_string = uuid.toString();
        try{
            switch(position){
                case BUY:
                    ItemData bdata = this.ITEMS.get(item);
                    bdata.increaseBuys(amount);
                    this.ITEMS.put(item, bdata);
                    TransactionData btdata = new TransactionData(uuid_string, item, amount, price, TransactionPositionType.BI);
                    this.TRANSACTIONS.add(btdata);
                    this.NEW_TRANSACTIONS.add(btdata);
                    this.GDPDATA.increaseGDP((amount*price)/2);
                    break;
                case SELL:
                    ItemData sdata = this.ITEMS.get(item);
                    sdata.increaseSells(amount);
                    this.ITEMS.put(item, sdata);
                    TransactionData stdata = new TransactionData(uuid_string, item, amount, price, TransactionPositionType.SI);
                    this.TRANSACTIONS.add(stdata);
                    this.NEW_TRANSACTIONS.add(stdata);
                    this.GDPDATA.increaseGDP((amount*price)/2);
                    this.GDPDATA.increaseLoss((amount*getItemPrice(item, false))-(amount*price));
                    break;
                case EBUY:
                    EnchantmentData ebdata = this.ENCHANTMENTS.get(item);
                    ebdata.increaseBuys(amount);
                    this.ENCHANTMENTS.put(item, ebdata);
                    TransactionData betdata = new TransactionData(uuid_string, item, amount, price, TransactionPositionType.BE);
                    this.TRANSACTIONS.add(betdata);
                    this.NEW_TRANSACTIONS.add(betdata);
                    this.GDPDATA.increaseGDP((amount*price)/2);
                    break;
                case ESELL:
                    EnchantmentData esdata = this.ENCHANTMENTS.get(item);
                    esdata.increaseSells(amount);
                    this.ENCHANTMENTS.put(item, esdata);
                    TransactionData setdata = new TransactionData(uuid_string, item, amount, price, TransactionPositionType.SE);
                    this.TRANSACTIONS.add(setdata);
                    this.NEW_TRANSACTIONS.add(setdata);
                    this.GDPDATA.increaseGDP((amount*price)/2);
                    if (player.isOnline()){
                        Player onlinePlayer = player.getPlayer();
                        this.GDPDATA.increaseLoss((amount*getOverallEnchantmentPrice(item,
                         getItemPrice(onlinePlayer.getInventory().getItemInMainHand().getType().toString(), false), false)-(amount*price)));
                    }
                    break;
                default:
                    break;
            }
        }
        catch(NullPointerException | IllegalArgumentException e){
            if (Config.isDebugEnabled()){
                e.printStackTrace();
            }
            Logging.error(4);
            System.out.println("Cannot parse " + item + " into " + position.toString());
        }
    }

    //  Add a new loan to ephemeral storage
    public void addLoan(double value, double interest_rate, OfflinePlayer player){
        DecimalFormat df = new DecimalFormat(Config.getNumberFormat());
        LoanData data = new LoanData(value, interest_rate, player.getUniqueId().toString());
        this.LOANS.add(data);
        this.NEW_LOANS.add(data);
        if (player.isOnline()){
            Player onlinePlayer = player.getPlayer();
            onlinePlayer.sendMessage(ChatColor.RED + "Loan of " + Config.getCurrencySymbol() + value +
             " with interest-rate: " + interest_rate + " % per " + df.format(Config.getInterestRateUpdateRate()/1200) + "min");
        }
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
        }
        if (!sell){
            return price;
        }
        Double spd = Config.getSellPriceDifference();
        if (Main.getDfiles().getShops().getConfigurationSection("shops").getConfigurationSection(item).contains("sell-difference")){
            spd = Main.getDfiles().getShops().getConfigurationSection("shops").getConfigurationSection(item).getDouble("sell-difference");
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
        }
        if (!sell){
            return price;
        }
        Double spd = Config.getSellPriceDifference();
        if (Main.getDfiles().getEnchantments().getConfigurationSection("enchantments").getConfigurationSection(enchantment).contains("sell-difference")){
            spd = Main.getDfiles().getEnchantments().getConfigurationSection("enchantments").getConfigurationSection(enchantment).getDouble("sell-difference");
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

    //  Get ItemData object for map
    public ItemData getItemData(String item){
        return this.ITEMS.get(item);
    }

    //  Get ItemData object for map
    public EnchantmentData getEnchantmentData(String enchantment){
        return this.ENCHANTMENTS.get(enchantment);
    }

    //  Get price for adding an enchantment to an item
    public double getOverallEnchantmentPrice(String enchantment, double item_price, boolean sell){
        double price = getEnchantmentPrice(enchantment, sell);
        double ratio = getEnchantmentRatio(enchantment);
        return (price + ratio*item_price);
    }

    public int getBuysLeft(String item, OfflinePlayer player){
        if (Config.isDisableMaxBuysSells()){
            return 9999;
        }
        PlayerSaleData pdata = PLAYER_SALES.get(player.getUniqueId().toString());
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

    public int getSellsLeft(String item, OfflinePlayer player){
        if (Config.isDisableMaxBuysSells()){
            return 9999;
        }
        PlayerSaleData pdata = PLAYER_SALES.get(player.getUniqueId().toString());
        Integer max;
        try{
            max = MAX_PURCHASES.get(item).getSells();
        }
        catch(NullPointerException e){
            return 9999;
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
            return (ChatColor.GRAY + "0.0%");
        }
        if (change < -0.005){
            return (ChatColor.RED + df.format(change) + "%");
        }
        if (change > 0.005){
            return (ChatColor.GREEN + df.format(change) + "%");
        }
        else{
            return (ChatColor.GRAY + "0.0%");
        }
    }

    public void updatePrices(ConcurrentHashMap<String, ItemData> data){
        this.ITEMS = data;
    }

    public void updateEnchantments(ConcurrentHashMap<String, EnchantmentData> data){
        this.ENCHANTMENTS = data;
    }

    public void updatePercentageChanges(){
        int tpInDay = (int) Math.floor(1.0/(Config.getTimePeriod()/1440.0));
        ItemTimePeriod Ilatest;
        ItemTimePeriod Ibase;
        EnchantmentsTimePeriod Elatest;
        EnchantmentsTimePeriod Ebase;
        if (this.size < 2){
            return;
        }
        Ilatest = Main.getDatabase().map.get(size-1).getItp();
        Ibase = Main.getDatabase().map.get(0).getItp();
        if (this.size-1 > tpInDay){
            Ibase = Main.getDatabase().map.get(size-tpInDay).getItp();
        }
        for (String item : Ilatest.getItems()){
            int latestPosition = Arrays.asList(Ilatest.getItems()).indexOf(item);
            int basePosition = Arrays.asList(Ibase.getItems()).indexOf(item);
            double latestPrice = Ilatest.getPrices()[latestPosition];
            double basePrice = Ibase.getPrices()[basePosition];
            double pChange = (latestPrice-basePrice)/basePrice*100;
            this.PERCENTAGE_CHANGES.put(item, pChange);
        }
        Elatest = Main.getDatabase().map.get(size-1).getEtp();
        Ebase = Main.getDatabase().map.get(0).getEtp();
        if (this.size-1 > tpInDay){
            Ebase = Main.getDatabase().map.get(size-tpInDay).getEtp();
        }
        for (String enchantment : Elatest.getItems()){
            int latestPosition = Arrays.asList(Elatest.getItems()).indexOf(enchantment);
            int basePosition = Arrays.asList(Ebase.getItems()).indexOf(enchantment);
            double latestPrice = Elatest.getPrices()[latestPosition];
            double basePrice = Ebase.getPrices()[basePosition];
            double pChange = (latestPrice-basePrice)/basePrice*100;
            this.PERCENTAGE_CHANGES.put(enchantment, pChange);
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
        loadGDPDataFromData();
        loadEconomyInfoDataFromFile();
        loadEconomyInfoDataFromData();
        updatePercentageChanges();
    }

    //  Get current cache for a players PlayerData object
    private PlayerSaleData getPlayerSaleData(OfflinePlayer player){
        PlayerSaleData playerSaleData = new PlayerSaleData();
        if (this.PLAYER_SALES.contains(player)){
            playerSaleData = this.PLAYER_SALES.get(player.getUniqueId().toString());
        }
        return playerSaleData;
    }

    private void loadShopDataFromFile(){
        ConfigurationSection config = Main.getDfiles().getShops().getConfigurationSection("shops");
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
            ItemData data = new ItemData(section.getDouble("price", 0.0));
            if (Config.isReadFromCSV()){
                data = new ItemData(map.get(key));
            }
            MaxBuySellData mbsdata = new MaxBuySellData(section.getInt("max-buy", 9999), section.getInt("max-sell", 9999));
            this.MAX_PURCHASES.put(key, mbsdata);
            this.ITEMS.put(key, data);
        }
    }

    private void loadShopDataFromData(){
        if (size == 0){
            for (String str : this.ITEMS.keySet()){
                this.PERCENTAGE_CHANGES.put(str, 0.0);
            }
            return;
        }
        if (size == 1){
            int i = 0;
            ItemTimePeriod ITP = Main.getDatabase().map.get(0).getItp();
            for (String item : Main.getDatabase().map.get(0).getItp().getItems()){
                ItemData data = new ItemData(ITP.getPrices()[i]);
                this.ITEMS.put(item, data);
                i++;
            }
            for (String str : this.ITEMS.keySet()){
                this.PERCENTAGE_CHANGES.put(str, 0.0);
            }
        }
        int i = 0;
        ItemTimePeriod ITP = Main.getDatabase().map.get(size-1).getItp();
        for (String item : Main.getDatabase().map.get(size-1).getItp().getItems()){
            ItemData data = new ItemData(ITP.getPrices()[i]);
            this.ITEMS.put(item, data);
            i++;
        }
    }

    private void loadEnchantmentDataFromFile(){
        ConfigurationSection config = Main.getDfiles().getEnchantments().getConfigurationSection("enchantments");
        Set<String> set = config.getKeys(false);
        for (String key : set){
            ConfigurationSection sec = config.getConfigurationSection(key);
            EnchantmentData data = new EnchantmentData(sec.getDouble("price", 0.0), sec.getDouble("ratio", 0.0));
            this.ENCHANTMENTS.put(key, data);
        }
    }

    private void loadEnchantmentDataFromData(){
        if (size < 1){
            return;
        }
        EnchantmentsTimePeriod ETP = Main.getDatabase().map.get(size-1).getEtp();
        int i = 0;
        for (String item : ETP.getItems()){
            EnchantmentData data = new EnchantmentData(ETP.getPrices()[i], ETP.getRatios()[i]);
            this.ENCHANTMENTS.put(item, data);
            i++;
        }
    }

    private void loadLoanDataFromData(){
        this.LOANS.clear();
        for (Integer pos : Main.getDatabase().map.keySet()){
            LoanTimePeriod LTP = Main.getDatabase().map.get(pos).getLtp();
            for (int i = 0; i < LTP.getValues().length; i++){
                LoanData data = new LoanData(LTP.getValues()[i], LTP.getInterest_rates()[i], LTP.getPlayers()[i], LTP.getTime()[i]);
                this.LOANS.add(data);
            }
        }
        Collections.sort(this.LOANS);
    }

    private void loadTransactionDataFromData(){
        if (this.size < 1){
            return;
        }
        this.TRANSACTIONS.clear();
        for (Integer pos : Main.getDatabase().map.keySet()){
            TransactionsTimePeriod TTP = Main.getDatabase().map.get(pos).getTtp();
            for (int i = 0; i < TTP.getPrices().length; i++){
                TransactionPositionType position = TransactionPositionType.valueOf(TTP.getPositions()[i]);
                try{
                    TransactionData data = new TransactionData(TTP.getPlayers()[i], TTP.getItems()[i],
                     TTP.getAmounts()[i], TTP.getPrices()[i], position, TTP.getTime()[i]);
                    this.TRANSACTIONS.add(data);
                }
                catch(NullPointerException e){
                    Logging.error("Auto-Tune failed to cache data for a transaction!");
                }
            }
        }
        Collections.sort(this.TRANSACTIONS);
    }

    private void loadSectionDataFromFile(){
        ConfigurationSection csection = Main.getDfiles().getShops().getConfigurationSection("sections");
        for (String section : csection.getKeys(false)){
            ConfigurationSection icsection = csection.getConfigurationSection(section);
            SECTIONS.add(new Section(section, icsection.getString("block"), icsection.getBoolean("back-menu-button-enabled"),
             icsection.getInt("position"), icsection.getString("background")));
        }
        csection = Main.getDfiles().getEnchantments().getConfigurationSection("config");
        SECTIONS.add(new Section("Enchantments", csection.getString("block"), csection.getBoolean("back-menu-button-enabled"),
             csection.getInt("position"), csection.getString("background")));
    }

    private void loadGDPDataFromData(){
        if (size < 1){
            this.GDPDATA = new GDPData(0, 0, 0, 0, 0, 0);
            return;
        }
        GDPTimePeriod GTP = Main.getDatabase().map.get(size-1).getGtp();
        this.GDPDATA = new GDPData(GTP.getGDP(), GTP.getBalance(), GTP.getLoss(), GTP.getDebt(), GTP.getInflation(), GTP.getPlayerCount());
    }

    private void loadEconomyInfoDataFromFile(){
        this.ECONOMYINFO = new EconomyInfoData(Config.getSellPriceDifferenceVariationStart());
    }

    private void loadEconomyInfoDataFromData(){
        if (size < 1){
            return;
        }
        this.ECONOMYINFO = new EconomyInfoData(Main.getDatabase().map.get(size-1).getEitp().getSellPriceDifference());
    }

}
