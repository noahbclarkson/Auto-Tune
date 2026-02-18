package com.github.noahbclarkson.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.github.noahbclarkson.AutoTune;

public class AutoTuneDatabase {

    @FunctionalInterface
    public interface ResultSetMapper<T> {
        T map(ResultSet resultSet) throws SQLException;
    }

    private final DataSource dataSource;

    public AutoTuneDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Connect to the database
     *
     * @return The Connection object
     * @throws SQLException if the connection fails
     */
    public Connection connect() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Executes a SELECT query and maps the result while JDBC resources are still open.
     *
     * @param query  SQL query to execute
     * @param mapper callback for mapping the ResultSet
     * @param params prepared statement parameters
     * @return mapped result
     */
    public <T> T query(String query, ResultSetMapper<T> mapper, Object... params) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
            setParameters(statement, params);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapper.map(resultSet);
            }
        } catch (SQLException e) {
            AutoTune.getLog().severe("Failed to execute SQL: " + query + "\nError: " + e);
            return null;
        }
    }

    /**
     * Execute a query on the database (INSERT, UPDATE, DELETE)
     *
     * @param query  The sql query to execute
     * @param params The parameters to set in the query
     * @return True if the query was successful
     */
    public boolean updateData(String query, Object... params) {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(query)) {
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
