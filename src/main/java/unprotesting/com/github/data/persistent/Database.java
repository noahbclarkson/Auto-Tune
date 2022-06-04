package unprotesting.com.github.data.persistent;


import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.DBMaker.Maker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.data.persistent.timeperiods.EconomyInfoTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.EnchantmentsTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.GDPTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.ItemTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.LoanTimePeriod;
import unprotesting.com.github.data.persistent.timeperiods.TransactionsTimePeriod;

//  Database object for storing all time-period object in persistent data

public class Database {

    private DB database;
    public HTreeMap<Integer, TimePeriod> map;

    @SuppressWarnings("unchecked")
    public Database(){
        createDB(Config.getDataLocation());
        this.map = database.hashMap("map", Serializer.INTEGER,Serializer.JAVA).createOrOpen();
    }

    @SuppressWarnings("unchecked")
    public Database(String location){
        createDB(location);
        this.map = database.hashMap("map", Serializer.INTEGER,Serializer.JAVA).createOrOpen();
    }

    public void close(){
        map.close();
        database.close();
    }

    //  Method to build and create or link database to file
    private void createDB(String location){
        Maker maker = DBMaker.fileDB(location + "data.db");
        database = checkDataTransactions(checkChecksumHeaderBypass(maker))
        .fileMmapEnableIfSupported()
        .cleanerHackEnable()
        .closeOnJvmShutdown().make();
    }

    // Check if checksumHeaderBypass is enabled in config
    private Maker checkChecksumHeaderBypass(Maker maker){
        if (Config.isChecksumHeaderBypass()){
            return maker.checksumHeaderBypass();
        }
        return maker;
    }

    // Check if dataTransactions is enabled in config
    private Maker checkDataTransactions(Maker maker){
        if (Config.isDataTransactions()){
            return maker.transactionEnable();
        }
        return maker;
    }

    public void saveCacheToLastTP(){
        int size = map.size()-1;
        if (size < 1){
            return;
        }
        TimePeriod TP = map.get(size);
        ItemTimePeriod ITP = TP.getItp();
        String[] items = ITP.getItems();
        int[] buys = ITP.getBuys();
        int[] sells = ITP.getSells();
        int pos = 0;
        for (String item : items){
            buys[pos] = buys[pos] + Main.getCache().getITEMS().get(item).getBuys();
            sells[pos] = sells[pos] + Main.getCache().getITEMS().get(item).getSells();
            ITP.setBuys(buys);
            ITP.setSells(sells);
            pos++;
        }
        TP.setItp(ITP);
        EnchantmentsTimePeriod ETP = TP.getEtp();
        String[] enchantments = ETP.getItems();
        int[] ebuys = ETP.getBuys();
        int[] esells = ETP.getSells();
        pos = 0;
        for (String enchantment : enchantments){
            ebuys[pos] = ebuys[pos] + Main.getCache().getENCHANTMENTS().get(enchantment).getBuys();
            esells[pos] = esells[pos] + Main.getCache().getENCHANTMENTS().get(enchantment).getSells();
            ETP.setBuys(ebuys);
            ETP.setSells(esells);
            pos++;
        }
        TP.setEtp(ETP);
        TP.setGtp(new GDPTimePeriod());
        TP.setLtp(new LoanTimePeriod());
        TP.setTtp(new TransactionsTimePeriod());
        TP.setEitp(new EconomyInfoTimePeriod());
        map.put(map.size()-1, TP);
    }



    
}
