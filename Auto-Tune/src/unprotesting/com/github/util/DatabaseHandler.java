package unprotesting.com.github.util;
import org.mapdb.DB;
import org.mapdb.DBMaker;

public class DatabaseHandler {

    public void makeMemoryDatabse(){
        DB db = DBMaker.memoryDB().make();
        String welcomeMessageKey = "Welcome Message";
        String welcomeMessageString = "Hello Baeldung!";
        db.close();
    }
    
}