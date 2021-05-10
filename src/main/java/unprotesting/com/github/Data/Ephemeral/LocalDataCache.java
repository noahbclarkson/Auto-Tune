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

    static @Getter
    private ConcurrentHashMap<String, ItemData> ITEMS;
    static @Getter
    private ConcurrentHashMap<String, EnchantmentData> ENCHANTMENTS;
    static @Getter
    private List<LoanData> LOANS;
    static @Getter
    private List<TransactionData> TRANSACTIONS;
    static @Getter
    private ConcurrentHashMap<Player, PlayerSaleData> PLAYER_SALES;

    private int size;

    public LocalDataCache(){
        ITEMS = new ConcurrentHashMap<String, ItemData>();
        ENCHANTMENTS = new ConcurrentHashMap<String, EnchantmentData>();
        LOANS = new ArrayList<LoanData>();
        TRANSACTIONS = new ArrayList<TransactionData>();
        PLAYER_SALES = new ConcurrentHashMap<Player, PlayerSaleData>();
        size = Database.map.size();
        init();
    }

    //  Add a new sale to related maps depending on type, item, etc.
    public static void addSale(Player player, String item, double price, int amount, SalePositionType position){
        PlayerSaleData playerSaleData = getPlayerSaleData(player);
        playerSaleData.addSale(item, amount, position);
        switch(position){
            case BUY:
                ITEMS.get(item).increaseBuys(amount);
                TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.BI));
                break;
            case SELL:
                ITEMS.get(item).increaseSells(amount);
                TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.SI));
                break;
            case EBUY:
                ENCHANTMENTS.get(item).increaseBuys(amount);
                TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.BE));
                break;
            case ESELL:
                ENCHANTMENTS.get(item).increaseSells(amount);
                TRANSACTIONS.add(new TransactionData(player, item, amount, price, TransactionPositionType.SE));
                break;
            default:
                break;
        }
    }

    //  Add a new loan to ephemeral storage
    public static void addLoan(double value, double intrest_rate, Player player){
        LOANS.add(new LoanData(value, intrest_rate, player));
        Collections.sort(LOANS);
    }

    //  Get item price
    public static double getItemPrice(String item){
        return ITEMS.get(item).getPrice();
    }

    public static double getEnchantmentPrice(String enchantment){
        return ENCHANTMENTS.get(enchantment).getPrice();
    }

    public static double getEnchantmentRatio(String enchantment){
        return ENCHANTMENTS.get(enchantment).getRatio();
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
    private static PlayerSaleData getPlayerSaleData(Player player){
        PlayerSaleData playerSaleData = new PlayerSaleData();
        if (PLAYER_SALES.contains(player)){
            playerSaleData = PLAYER_SALES.get(player);
        }
        return playerSaleData;
    }

    private void loadShopDataFromFile(){
        ConfigurationSection config = Main.dfiles.getShops().getConfigurationSection("shops");
        Set<String> set = config.getKeys(false);
        for (String key : set){
            ItemData data = new ItemData(config.getConfigurationSection(key).getDouble("price"));
            ITEMS.put(key, data);
        }
    }

    private void loadShopDataFromData(){
        ItemTimePeriod ITP = Database.map.get(size-1).getItp();
        int i = 0;
        for (String item : ITP.getItems()){
            ItemData data = new ItemData(ITP.getPrices()[i]);
            ITEMS.put(item, data);
            i++;
        }
    }

    private void loadEnchantmentDataFromFile(){
        ConfigurationSection config = Main.dfiles.getEnchantments().getConfigurationSection("enchantments");
        Set<String> set = config.getKeys(false);
        for (String key : set){
            ConfigurationSection sec = config.getConfigurationSection(key);
            EnchantmentData data = new EnchantmentData(sec.getDouble("price"), sec.getDouble("ratio"));
            ENCHANTMENTS.put(key, data);
        }
    }

    private void loadEnchantmentDataFromData(){
        EnchantmentsTimePeriod ETP = Database.map.get(size-1).getEtp();
        int i = 0;
        for (String item : ETP.getItems()){
            EnchantmentData data = new EnchantmentData(ETP.getPrices()[i], ETP.getRatios()[i]);
            ENCHANTMENTS.put(item, data);
            i++;
        }
    }

    private void loadLoanDataFromData(){
        LOANS.clear();
        for (Integer pos : Database.map.keySet()){
            LoanTimePeriod LTP = Database.map.get(pos).getLtp();
            for (int i = 0; i < LTP.getValues().length; i++){
                UUID uuid = UUID.fromString(LTP.getPlayers()[i]);
                Player player = Bukkit.getPlayer(uuid);
                LoanData data = new LoanData(LTP.getValues()[i], LTP.getIntrest_rates()[i], player, LTP.getTime()[i]);
                LOANS.add(data);
            }
        }
        Collections.sort(LOANS);
    }

    private void loadTransactionDataFromData(){
        TRANSACTIONS.clear();
        for (Integer pos : Database.map.keySet()){
            TransactionsTimePeriod TTP = Database.map.get(pos).getTtp();
            for (int i = 0; i < TTP.getPrices().length; i++){
                UUID uuid = UUID.fromString(TTP.getPlayers()[i]);
                Player player = Bukkit.getPlayer(uuid);
                TransactionPositionType position = TransactionPositionType.valueOf(TTP.getPositions()[i]);
                TransactionData data = new TransactionData(player, TTP.getItems()[i], TTP.getAmounts()[0], TTP.getPrices()[i], position, TTP.getTime()[i]);
                TRANSACTIONS.add(data);
            }
        }
        Collections.sort(TRANSACTIONS);
    }
    
}
