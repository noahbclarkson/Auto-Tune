package unprotesting.com.github.Data.Persistent;


import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.mapdb.DBMaker.Maker;

import unprotesting.com.github.Config.Config;

//  Database object for storing all time-period object in persistent data

public class Database {

    private DB database;
    public HTreeMap<Integer, TimePeriod> map;

    @SuppressWarnings("unchecked")
    public Database(){
        createDB();
        this.map = database.hashMap("map", Serializer.INTEGER,Serializer.JAVA).createOrOpen();
    }

    //  Method to build and create or link database to file
    private void createDB(){
        Maker maker = DBMaker.fileDB(Config.getDataLocation() + "data.db");
        database = checkDataTransactions(checkChecksumHeaderBypass(maker))
        .fileChannelEnable()
        .fileMmapEnable()
        .fileMmapEnableIfSupported()
        .cleanerHackEnable()
        .allocateStartSize(10485760)
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
