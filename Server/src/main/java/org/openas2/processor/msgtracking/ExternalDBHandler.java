/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.msgtracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;
import java.util.Map;

class ExternalDBHandler extends DbTrackingModule implements IDBHandler {

    @Nullable
    private HikariDataSource ds = null;

    private String connectString = null;

    public void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception {
        // Check that a connection pool is not already running
        if (ds != null) {
            throw new OpenAS2Exception(
                "Connection pool already initialized. Cannot create a new connection pool. Stop current one first. DB connect string:"
                + connectString + " :: Active pool connect string: " + this.connectString);
        }
        this.connectString = connectString;

        String configDir = Properties.getProperty(Properties.APP_BASE_DIR_PROP, null);
        // Create the config from properties file if it exists then overwrite with the explicitly configured OpenAS2 properties
        HikariConfig config = null;
        if (configDir != null) {
            String configFilePath = configDir + "hikari.properties";
            Path path = Paths.get(configFilePath);
            if (Files.exists(path)) {
                config = new HikariConfig(configFilePath);
            }
        }
        if (config == null) {
            config = new HikariConfig();
        }
        config.setJdbcUrl(connectString);
        config.setUsername(userName);
        config.setPassword(pwd);
        ds = new HikariDataSource(config);

    }

    public void start(String connectString, String userName, String pwd, Map<String, String> params) throws OpenAS2Exception {
        createConnectionPool(connectString, userName, pwd);
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            Driver driver = drivers.nextElement();// home into first and the only
            Logger logger = LoggerFactory.getLogger(getName());
            logger.info("Using JDBC driver: " + driver.getClass().getName());
    }

    public void stop() {
        destroyConnectionPool();
    }

    public void destroyConnectionPool() {
        if (ds == null) {
            return;
        }
        ds.close();
        ds = null;
    }

    public Connection getConnection() throws SQLException, OpenAS2Exception {
        // Check that a connection pool is running
        if (ds == null) {
            throw new OpenAS2Exception("Connection pool not initialized.");
        }
        return ds.getConnection();
    }

    public boolean shutdown(String connectString) {
        destroyConnectionPool();
        return true;
    }

}
