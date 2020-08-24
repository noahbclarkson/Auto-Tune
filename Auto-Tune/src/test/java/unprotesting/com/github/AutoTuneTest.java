package test.java.unprotesting.com.github;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Random;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auto-Tune Tests")
class AutoTuneTest {

    public long startTime;
    public long endTime;
    public long duration;
    public Random random = new Random();
    public File f = new File("data.db");

    @BeforeEach
    void beforeEach() {
    }

    @BeforeAll
    static void beforeAll() {
        System.out.println("Running tests....");
    }

    @AfterEach
    void afterEach() {
        endTime = System.nanoTime();
        duration = (endTime - startTime); // divide by 1000000 to get milliseconds.
        System.out.println("Test Duration: " + duration + " nanoseconds or " + duration / 1000000 + " milliseconds");
    }

    @AfterAll
    static void afterAll() {
        System.out.println(" <-Tests Complete-> ");
    }

    @Test
    @Order(1)
    @DisplayName("Test, test method")
    void firstTest() {
        System.out.println("Running Test, test method");
        startTime = System.nanoTime();
    }

    @Test
    @Order(2)
    @DisplayName("Math Respond Test")
    void secondTest() {
        System.out.println("Running Test, math respond test");
        startTime = System.nanoTime();
        int a = 10;
        int i = 2;
        for (; i < 10000; i++) {
            int temp = a + 10;
            a = temp;
        }
        assertEquals(99990, a, "Check that math test is correct");
    }

    @Test
    @Order(3)
    @DisplayName("Memory MapDB Test")
    void thirdTest() {
        System.out.println("Running Test, memory MapDB test");
        HTreeMap<Integer, Integer> memoryDataTest;
        DB memDB = DBMaker.memoryDB().closeOnJvmShutdown().make();
        memoryDataTest = memDB.hashMap("memoryDataTest", Serializer.INTEGER, Serializer.INTEGER).createOrOpen();
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            memoryDataTest.put(i, random.nextInt(1000));
        }
        for (int i = 0; i < 10000; i++) {
            int a = memoryDataTest.get(i);
        }
    }

    @Test
    @Order(4)
    @DisplayName("MapDB Test")
    void fourthTest() {
        System.out.println("Running Test, MapDB test");
        HTreeMap<Integer, Integer> memoryDataTest;
        DB localDB = DBMaker.fileDB("target/data.db").checksumHeaderBypass().closeOnJvmShutdown().make();
        memoryDataTest = localDB.hashMap("memoryDataTest", Serializer.INTEGER, Serializer.INTEGER).createOrOpen();
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            memoryDataTest.put(i, random.nextInt(1000));
        }
        for (int i = 0; i < 10000; i++) {
            int a = memoryDataTest.get(i);
        }
    }
}
