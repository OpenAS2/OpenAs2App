package org.openas2.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.h2.jdbcx.JdbcConnectionPool;
import org.openas2.OpenAS2Exception;

public class H2DBHandler implements IDBHandler
{
	JdbcConnectionPool cp = null;

	private String jdbcDriver = "org.h2.Driver";

	private String connectString = "jdbc:h2:file:DB/openas2";

    public H2DBHandler()
    {
    }

    /**
     * @param jdbcDriver
     */
    public H2DBHandler(String jdbcDriver)
    {
        setJdbcDriver(jdbcDriver);
    }

    /**
	 * @param jdbcDriver
	 */
	public void setJdbcDriver(String jdbcDriver)
	{
		this.jdbcDriver = jdbcDriver;
	}

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
		// Load the Database Engine JDBC driver
		// Class.forName(jdbcDriver);

		cp = JdbcConnectionPool.create(connectString, userName, pwd);
	}

	public void destroyConnectionPool()
	{
		if (cp == null)
			return;
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

	public Connection connect(String connectString, String userName, String password) throws Exception
	{

		// Load the Database Engine JDBC driver
		Class.forName(jdbcDriver);
		try
		{

			return DriverManager.getConnection(connectString, userName, password);
		} catch (SQLException e)
		{
			throw new Exception("Failed to obtain connection to database: " + connectString, e);
		}
	}

	public boolean shutdown(String connectString) throws SQLException, OpenAS2Exception
	{
		// Wait briefly if there are active connections
		int waitCount = 0;
		try
		{
			while (cp.getActiveConnections() > 0 && waitCount < 10)
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
