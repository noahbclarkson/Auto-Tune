package unprotesting.com.github.data.persistent;


import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.DBMaker.Maker;

import unprotesting.com.github.config.Config;

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
        this.map.close();
        this.database.close();
    }

    //  Method to build and create or link database to file
    private void createDB(String location){
        Maker maker = DBMaker.fileDB(location + "data.db");
        database = checkDataTransactions(checkChecksumHeaderBypass(maker))
        .fileChannelEnable()
        .fileMmapEnable()
        .fileMmapEnableIfSupported()
        .cleanerHackEnable()
        .allocateStartSize(1048576)
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



    
}
