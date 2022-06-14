package unprotesting.com.github;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import unprotesting.com.github.data.persistent.Database;
import unprotesting.com.github.data.persistent.TimePeriod;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auto-Tune Tests")
class AutoTuneTest {

  private long startTime;
  private static Database db;
  private static List<Long> times = new ArrayList<>();

  @BeforeAll
  static void beforeAll() {
    File file = new File("./tests/");
    file.mkdirs();
    db = new Database("./tests/");
    db.getMap().clear();
    
  }


  @AfterAll
  static void afterAll() {

    db.getDb().close();
    File testFolder = new File("./tests/");
    testFolder.delete();

    long total = 0;
    for (Long time : times) {
      total += time;
    }
    total /= times.size();
    System.out.println("Average Test Duration: " + total + " nanoseconds or " 
        + total / 1000000 + " milliseconds or " + total / 1000000000 + " seconds.");

  }

  @BeforeEach
  void beforeEach() {

    db.getMap().clear();
    startTime = System.nanoTime();

  }

  @AfterEach
  void afterEach() {

    long endTime = System.nanoTime();
    assertTrue(db.getMap().size() == 100);
    long duration = (endTime - startTime);

    System.out.println("Test Duration: " + duration + " nanoseconds or " 
        + duration / 1000000 + " milliseconds or " + duration / 1000000000 + " seconds.");
    times.add(duration);
      
  }

  @Test
  @Order(1)
  @DisplayName("Data entry test")
  @RepeatedTest(2)
  void firstTest() {

    System.out.println("Testing 100 empty data inputs.");

    for (int i = 0; i < 100; i++) {

      TimePeriod tp = new TimePeriod(true);
      db.getMap().put(db.getMap().size(), tp);

    }

    System.out.println(db.getMap().size());
    
  }

}
