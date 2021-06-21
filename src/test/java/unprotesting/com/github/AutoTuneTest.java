package unprotesting.com.github;


import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import unprotesting.com.github.data.persistent.Database;
import unprotesting.com.github.data.persistent.TimePeriod;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auto-Tune Tests")
class AutoTuneTest {

    private long start_time;

    @BeforeEach
    void beforeEach() {
        start_time = System.nanoTime();
    }

    @AfterEach
    void afterEach() {
        long end_time = System.nanoTime();
        long duration = (end_time - start_time);
        System.out.println("Test Duration: " + duration + " nanoseconds or " +
        duration / 1000000 + " milliseconds or " + duration / 1000000000 + " seconds.");
    }

    @Test
    @Order(1)
    @DisplayName("Data entry test")
    void firstTest() {
        File file = new File("./tests/");
        file.mkdirs();
        Database database = new Database("./tests/");
        System.out.println("Testing 1000 empty data inputs.");
        for (int i = 0; i < 1000; i++){
            TimePeriod TP = new TimePeriod(true);
            database.map.put(database.map.size(), TP);
        }
    }

}
