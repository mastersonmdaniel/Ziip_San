package br.com.sigsan.platform.tenant;

import org.hibernate.HibernateException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
public class MultiTenantConnectionProvider
        implements org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider {

    private final DataSource dataSource;

    public MultiTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        try {
            connection.setSchema("public");
        } catch (SQLException e) {
            // Ignore
        }
        connection.close();
    }

    @Override
    public Connection getConnection(Object tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            connection.setSchema(String.valueOf(tenantIdentifier));
        } catch (SQLException e) {
            connection.close();
            throw new HibernateException("Could not set schema to " + tenantIdentifier, e);
        }
        return connection;
    }

    @Override
    public void releaseConnection(Object tenantIdentifier, Connection connection) throws SQLException {
        try {
            connection.setSchema("public");
        } catch (SQLException e) {
            // Ignore
        }
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return true;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}