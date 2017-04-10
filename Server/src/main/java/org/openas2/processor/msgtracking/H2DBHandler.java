package org.openas2.processor.msgtracking;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.h2.jdbcx.JdbcConnectionPool;
import org.openas2.OpenAS2Exception;

class H2DBHandler implements IDBHandler {

    @Nullable
    private JdbcConnectionPool cp = null;

    private String connectString = "jdbc:h2:file:DB/openas2";

    public void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception
    {
        // Check that a connection pool is not already running
        if (cp != null)
        {
            throw new OpenAS2Exception(
                    "Connection pool already initialized. Cannot create a new connection pool. Stop current one first. DB connect string:"
                            + connectString + " :: Active pool connect string: " + this.connectString);
        }
        this.connectString = connectString;

        cp = JdbcConnectionPool.create(connectString, userName, pwd);
    }

    public void destroyConnectionPool()
    {
        if (cp == null)
        {
            return;
        }
        cp.dispose();
        cp = null;
    }

    public Connection getConnection() throws SQLException, OpenAS2Exception
    {
        // Check that a connection pool is running
        if (cp == null)
        {
            throw new OpenAS2Exception("Connection pool not initialized.");
        }
        return cp.getConnection();
    }

    public boolean shutdown(String connectString) throws SQLException, OpenAS2Exception
    {
        // Wait briefly if there are active connections
        int waitCount = 0;
        try
        {
            while (cp != null && cp.getActiveConnections() > 0 && waitCount < 10)
            {
                TimeUnit.MILLISECONDS.sleep(100);
                waitCount++;
            }
        } catch (InterruptedException e)
        {
            // Do nothing
        }
        Connection c = getConnection();
        Statement st = c.createStatement();

        boolean result = st.execute("SHUTDOWN");
        c.close();
        return result;
    }

}
