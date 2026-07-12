package br.com.schf.migration.source.firebird;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class FirebirdConnectionFactory {
    private final FirebirdSourceConfiguration config;

    public FirebirdConnectionFactory(FirebirdSourceConfiguration config) {
        this.config = config;
        try {
            Class.forName("org.firebirdsql.jdbc.FBDriver");
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Firebird JDBC driver not found", ex);
        }
    }

    public Connection openReadOnly() throws SQLException {
        var conn = DriverManager.getConnection(config.connectionUrl(), config.username(), config.password());
        conn.setReadOnly(true);
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try (var stmt = conn.createStatement()) {
            stmt.execute("SET TRANSACTION NO WRITE READ COMMITTED");
        } catch (SQLException ignored) {
        }
        return conn;
    }

    public void validateConnection() {
        try (var conn = openReadOnly()) {
            if (!conn.isValid(5)) {
                throw new IllegalStateException("Firebird connection is not valid");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Cannot connect to Firebird: " + ex.getMessage(), ex);
        }
    }

    public FirebirdSourceConfiguration config() {
        return config;
    }
}
