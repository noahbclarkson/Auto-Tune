package com.noahblclarkson.autotune.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("PMD")
class MigrationScriptTest {

    // Same order DatabaseManager.runMigrations() applies them in.
    private static final List<String> MIGRATIONS = List.of(
            "db/V1__Initial_Schema.sql",
            "db/V2__Player_Onboarding.sql",
            "db/V3__Player_Streaks.sql",
            "db/V4__Admin_Audit_Log.sql",
            "db/V5__Player_Types.sql",
            "db/V5b__Watched_Auctions.sql",
            "db/V7__Circuit_Events.sql",
            "db/V8__Auction_Fill_Status.sql",
            "db/V9__Auction_Pending_Returns.sql");

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("All migrations apply in order against a fresh SQLite database")
    void allMigrationsApplyCleanly() throws SQLException {
        String url = "jdbc:sqlite:" + tempDir.resolve("migration-test.db");
        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            for (String resource : MIGRATIONS) {
                for (String statement : DatabaseManager.splitSqlStatements(readResource(resource))) {
                    assertDoesNotThrow(() -> stmt.execute(statement),
                            () -> "Failed executing statement from " + resource + ":\n" + statement);
                }
            }

            // Regression: V5 previously targeted a non-existent "at_player_data" table.
            assertTrue(columnExists(conn, "at_players", "player_type"),
                    "player_type column should exist on at_players");
            assertTrue(columnExists(conn, "at_players", "last_onboarding_milestone_sent"),
                    "V2 onboarding column should exist on at_players");
            // Regression: V9's inline comment containing ';' truncated the CREATE TABLE.
            assertTrue(tableExists(conn, "at_auction_pending_returns"),
                    "V9 pending-returns table should exist");
        }
    }

    @Test
    @DisplayName("Splitter strips inline comments and ignores semicolons inside them")
    void splitterStripsInlineCommentsWithSemicolons() {
        List<String> statements = DatabaseManager.splitSqlStatements(
                "CREATE TABLE t (\n  a TEXT, -- nullable; not all rows have it\n  b TEXT\n);");
        assertEquals(1, statements.size());
        assertFalse(statements.get(0).contains("nullable"), "inline comment should be stripped");
        assertTrue(statements.get(0).contains("a TEXT"));
        assertTrue(statements.get(0).contains("b TEXT"));
    }

    @Test
    @DisplayName("Splitter does not split on semicolons inside string literals")
    void splitterIgnoresSemicolonsInStringLiterals() {
        List<String> statements = DatabaseManager.splitSqlStatements(
                "INSERT INTO t (msg) VALUES ('a;b');\nINSERT INTO t (msg) VALUES ('c');");
        assertEquals(2, statements.size());
        assertTrue(statements.get(0).contains("'a;b'"));
    }

    @Test
    @DisplayName("Splitter handles escaped quotes and full-line comments")
    void splitterHandlesEscapedQuotesAndFullLineComments() {
        List<String> statements = DatabaseManager.splitSqlStatements(
                "-- a leading comment;\nINSERT INTO t (msg) VALUES ('it''s; fine');");
        assertEquals(1, statements.size());
        assertTrue(statements.get(0).startsWith("INSERT"));
        assertTrue(statements.get(0).contains("'it''s; fine'"));
    }

    private String readResource(String path) {
        try (InputStream is = DatabaseManager.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Missing migration resource: " + path);
            }
            return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read migration resource: " + path, e);
        }
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
