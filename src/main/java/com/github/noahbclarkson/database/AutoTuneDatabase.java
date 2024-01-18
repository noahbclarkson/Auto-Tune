package com.github.noahbclarkson.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import com.github.noahbclarkson.AutoTune;

public class AutoTuneDatabase {

    private DataSource dataSource;

    public AutoTuneDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Connect to the database
     * @return The Connection object
     * @throws SQLException if the connection fails
     */
    public Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Execute a query on the database
     * @param query The sql query to execute
     * @return The ResultSet of the query
     */
    public ResultSet executeQuery(String query) {
        try (Connection connection = connect()) {
            Statement statement = connection.createStatement();
            return statement.executeQuery(query);
        } catch (SQLException e) {
            AutoTune.getLog().severe("Failed to execute SQL: " + query + "\nError: " + e);
            return null;
        }
    }

    /**
     * Execute a query on the database (INSERT, UPDATE, DELETE)
     * @param query The sql query to execute
     * @param params The parameters to set in the query
     * @return True if the query was successful
     */
    public boolean updateData(String query, Object... params) {
        try (Connection connection = connect()) {
            PreparedStatement statement = connection.prepareStatement(query);
            setParameters(statement, params);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            AutoTune.getLog().severe("Failed to execute SQL: " + query + "\nError: " + e);
            return false;
        }
    }

    private void setParameters(PreparedStatement pstmt, Object... params) throws SQLException {
        int paramIndex = 1;
        for (Object param : params) {
            pstmt.setObject(paramIndex++, param);
        }
    }

}
