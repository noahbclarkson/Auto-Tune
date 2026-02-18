package com.github.noahbclarkson.database;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class DatabaseInitializerTest {

    @Test
    void initializeDatabaseCreatesExpectedTables() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new DatabaseInitializer().initializeDatabase(connection);

            Set<String> actualTables = readTableNames(connection);
            Set<String> expectedTables = Set.of(
                    "players",
                    "sections",
                    "shops",
                    "price_history",
                    "transactions",
                    "autosell",
                    "economy_history");

            assertTrue(actualTables.containsAll(expectedTables),
                    "Expected all Auto-Tune tables to be created. Found: " + actualTables);
        }
    }

    @Test
    void initializeDatabaseIsIdempotent() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            DatabaseInitializer initializer = new DatabaseInitializer();
            initializer.initializeDatabase(connection);
            initializer.initializeDatabase(connection);

            Set<String> actualTables = readTableNames(connection);
            assertTrue(actualTables.contains("shops"));
            assertTrue(actualTables.contains("transactions"));
        }
    }

    private Set<String> readTableNames(Connection connection) throws SQLException {
        Set<String> tableNames = new HashSet<>();

        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table'")) {
            while (resultSet.next()) {
                tableNames.add(resultSet.getString("name"));
            }
        }

        return tableNames;
    }

}
