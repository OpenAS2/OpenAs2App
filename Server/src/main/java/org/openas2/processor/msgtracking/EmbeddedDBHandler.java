package org.openas2.processor.msgtracking;

import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.tools.Server;
import org.openas2.OpenAS2Exception;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class EmbeddedDBHandler extends DbTrackingModule implements IDBHandler {

    @Nullable
    private JdbcConnectionPool cp = null;

    private Server server = null;

    private String connectString = "jdbc:h2:file:DB/openas2";

    public void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception {
        // Check that a connection pool is not already running
        if (cp != null) {
            throw new OpenAS2Exception("Connection pool already initialized. Cannot create a new connection pool. Stop current one first. DB connect string:" + connectString + " :: Active pool connect string: " + this.connectString);
        }
        this.connectString = connectString;

        cp = JdbcConnectionPool.create(connectString, userName, pwd);
    }

    public void start(String connectString, String userName, String pwd, Map<String, String> params) throws OpenAS2Exception {
        createConnectionPool(connectString, userName, pwd);
        String isStartSrvr = params.get(PARAM_TCP_SERVER_START);
        if (isStartSrvr == null || "true".equalsIgnoreCase(isStartSrvr)) {
            String tcpPort = params.get(PARAM_TCP_SERVER_PORT);
            if (tcpPort == null || tcpPort.length() < 1) {
                tcpPort = "9092";
            }
            String tcpPwd = params.get(PARAM_TCP_SERVER_PWD);
            if (tcpPwd == null || tcpPwd.length() < 1) {
                tcpPwd = "OpenAS2";
            }
            String dbDirectory = params.get(PARAM_DB_DIRECTORY);
            if (dbDirectory == null || dbDirectory.length() < 1) {
                throw new OpenAS2Exception("TCP server requireds parameter: " + PARAM_DB_DIRECTORY);
            }

            try {
                server = Server.createTcpServer("-tcpPort", tcpPort, "-tcpPassword", tcpPwd, "-baseDir", dbDirectory, "-tcpAllowOthers").start();
            } catch (SQLException e) {
                throw new OpenAS2Exception("Failed to start TCP server", e);
            }
        }
    }

    public void stop() {
        // Stopping the TCP server will stop the database so only do one of them
        if (server != null) {
            server.stop();
        } else {
            try {
                shutdown(connectString);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            destroyConnectionPool();
        }
    }

    public void destroyConnectionPool() {
        if (cp == null) {
            return;
        }
        cp.dispose();
        cp = null;
    }

    public Connection getConnection() throws SQLException, OpenAS2Exception {
        // Check that a connection pool is running
        if (cp == null) {
            throw new OpenAS2Exception("Connection pool not initialized.");
        }
        return cp.getConnection();
    }

    public boolean shutdown(String connectString) throws SQLException, OpenAS2Exception {
        // Wait briefly if there are active connections
        int waitCount = 0;
        try {
            while (cp != null && cp.getActiveConnections() > 0 && waitCount < 10) {
                TimeUnit.MILLISECONDS.sleep(100);
                waitCount++;
            }
        } catch (InterruptedException e) {
            // Do nothing
        }
        Connection c = getConnection();
        Statement st = c.createStatement();

        boolean result = st.execute("SHUTDOWN");
        c.close();
        return result;
    }

}
