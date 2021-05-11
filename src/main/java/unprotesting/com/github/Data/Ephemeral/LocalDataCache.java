package unprotesting.com.github.Data.Ephemeral;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Commands.Objects.Section;
import unprotesting.com.github.Data.Ephemeral.Data.EnchantmentData;
import unprotesting.com.github.Data.Ephemeral.Data.ItemData;
import unprotesting.com.github.Data.Ephemeral.Data.LoanData;
import unprotesting.com.github.Data.Ephemeral.Data.TransactionData;
import unprotesting.com.github.Data.Ephemeral.Data.TransactionData.TransactionPositionType;
import unprotesting.com.github.Data.Ephemeral.Other.PlayerSaleData;
import unprotesting.com.github.Data.Ephemeral.Other.Sale.SalePositionType;
import unprotesting.com.github.Data.Persistent.Database;
import unprotesting.com.github.Data.Persistent.TimePeriods.EnchantmentsTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.ItemTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.LoanTimePeriod;
import unprotesting.com.github.Data.Persistent.TimePeriods.TransactionsTimePeriod;

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

    private int size;

    public LocalDataCache(){
        this.ITEMS = new ConcurrentHashMap<String, ItemData>();
        this.ENCHANTMENTS = new ConcurrentHashMap<String, EnchantmentData>();
        this.LOANS = new ArrayList<LoanData>();
        this.TRANSACTIONS = new ArrayList<TransactionData>();
        this.PLAYER_SALES = new ConcurrentHashMap<Player, PlayerSaleData>();
        this.SECTIONS = new ArrayList<Section>();
        this.size = Main.database.map.size();
        init();
    }

    //  Add a new sale to related maps depending on type, item, etc.
    public void addSale(Player player, String item, double price, int amount, SalePositionType position){
        PlayerSaleData playerSaleData = getPlayerSaleData(player);
        playerSaleData.addSale(item, amount, position);
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

    //  Add a new loan to ephemeral storage
    public void addLoan(double value, double intrest_rate, Player player){
        this.LOANS.add(new LoanData(value, intrest_rate, player));
        Collections.sort(LOANS);
    }

    //  Get item price
    public double getItemPrice(String item){
        return this.ITEMS.get(item).getPrice();
    }

    public double getEnchantmentPrice(String enchantment){
        return this.ENCHANTMENTS.get(enchantment).getPrice();
    }

    public double getEnchantmentRatio(String enchantment){
        return this.ENCHANTMENTS.get(enchantment).getRatio();
    }


    //  Initialize cache from configurations and relavent files
    private void init(){
        loadShopDataFromFile();
        loadShopDataFromData();
        loadEnchantmentDataFromFile();
        loadEnchantmentDataFromData();
        loadLoanDataFromData();
        loadTransactionDataFromData();
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
        for (String key : set){
            ItemData data = new ItemData(config.getConfigurationSection(key).getDouble("price"));
            this.ITEMS.put(key, data);
        }
    }

    private void loadShopDataFromData(){
        if (size < 1){
            return;
        }
        ItemTimePeriod ITP = Main.database.map.get(size-1).getItp();
        int i = 0;
        for (String item : ITP.getItems()){
            ItemData data = new ItemData(ITP.getPrices()[i]);
            this.ITEMS.put(item, data);
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
            SECTIONS.add(new Section(section, icsection.getString("block"), icsection.getBoolean("back-menu-button-enabled")));
        }
    }
    
}
