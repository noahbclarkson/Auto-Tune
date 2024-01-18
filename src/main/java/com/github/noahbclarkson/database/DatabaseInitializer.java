package com.github.noahbclarkson.database;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

import com.github.noahbclarkson.AutoTune;

public class DatabaseInitializer {

    public void initializeDatabase(Connection connection) {
        String sql = readSqlFile("/create-tables.sql");
        executeSql(connection, sql);
    }

    private String readSqlFile(String filePath) {
        try (InputStream inputStream = getClass().getResourceAsStream(filePath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (Exception e) {
            AutoTune.getLog().severe("Failed to read SQL file: " + filePath + "\nError: " + e);
            AutoTune.getInstance().getServer().getPluginManager().disablePlugin(AutoTune.getInstance());
            return null;
        }
    }

    private void executeSql(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            String[] commands = sql.split(";");
            for (String command : commands) {
                if (!command.trim().isEmpty()) {
                    statement.execute(command);
                }
            }
        } catch (Exception e) {
            AutoTune.getLog().severe("Failed to execute SQL: " + sql + "\nError: " + e);
            AutoTune.getInstance().getServer().getPluginManager().disablePlugin(AutoTune.getInstance());
        }
    }

}
