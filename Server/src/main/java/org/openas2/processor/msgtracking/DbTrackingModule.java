package org.openas2.processor.msgtracking;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.h2.tools.Server;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.database.H2DBHandler;
import org.openas2.message.Message;
import org.openas2.params.ComponentParameters;
import org.openas2.params.CompositeParameters;
import org.openas2.params.ParameterParser;
import org.openas2.util.DateUtil;
import org.openas2.util.Properties;

public class DbTrackingModule extends BaseMsgTrackingModule
{
	public static final String PARAM_TCP_SERVER_START = "tcp_server_start";
	public static final String PARAM_TCP_SERVER_PORT = "tcp_server_port";
	public static final String PARAM_TCP_SERVER_PWD = "tcp_server_password";
	public static final String PARAM_DB_USER = "db_user";
	public static final String PARAM_DB_PWD = "db_pwd";
	public static final String PARAM_DB_NAME = "db_name";
	public static final String PARAM_DB_DIRECTORY = "db_directory";
	public static final String PARAM_JDBC_CONNECT_STRING = "jdbc_connect_string";
	public static final String PARAM_JDBC_DRIVER = "jdbc_driver";
	public static final String PARAM_JDBC_SERVER_URL = "jdbc_server_url";
	public static final String PARAM_JDBC_PARAMS = "jdbc_extra_paramters";

	public static final String DEFAULT_TRACKING_DB_HANDLER_CLASS = "org.openas2.processor.msgtracking.DBHandler";
	public static final String PARAM_TRACKING_DB_HANDLER_CLASS = "tracking_db_handler_class";

	private String dbUser = null;
	private String dbPwd = null;
	private String dbDirectory = null;
	private String jdbcConnectString = null;
	private String configBaseDir = null;
	//private String jdbcDriver = null;
	private boolean isRunning = false;
	private String sqlEscapeChar = "'";
	Server server = null;

	private Log logger = LogFactory.getLog(DbTrackingModule.class.getSimpleName());

	public void init(Session session, Map<String, String> options) throws OpenAS2Exception
	{
		super.init(session, options);
		CompositeParameters paramParser = createParser();
		dbUser = getParameter(PARAM_DB_USER, true);
		dbPwd = getParameter(PARAM_DB_PWD, true);
		dbDirectory = getParameter(PARAM_DB_DIRECTORY, true);
		configBaseDir = session.getBaseDirectory();
		jdbcConnectString = getParameter(PARAM_JDBC_CONNECT_STRING, true);
		jdbcConnectString.replace("%home%", configBaseDir);
		// Support component attributes in connect string
		jdbcConnectString = ParameterParser.parse(jdbcConnectString, paramParser);
		//jdbcDriver = getParameter(PARAM_JDBC_DRIVER, false);
		sqlEscapeChar = Properties.getProperty("sql_escape_character", "'");
	}

	protected String getModuleAction()
	{
		return DO_TRACK_MSG;
	}

	protected CompositeParameters createParser()
	{
		CompositeParameters params = new CompositeParameters(true);

		params.add("component", new ComponentParameters(this));
		return params;
	}

	protected void persist(Message msg, Map<String, String> map)
	{
		Connection conn = null;
		try
		{
			conn = DBConnection.getConnection(jdbcConnectString, dbUser, dbPwd);
			Statement s = conn.createStatement();
			String msgIdField = FIELDS.MSG_ID;
			ResultSet rs = s.executeQuery("select * from msg_metadata where " + msgIdField + " = '"
					+ map.get(msgIdField) + "'");
			ResultSetMetaData meta = rs.getMetaData();
			boolean isUpdate = rs.next(); // Record already exists so update
			StringBuffer fieldStmt = new StringBuffer();
			for (int i = 0; i < meta.getColumnCount(); i++)
			{
				String colName = meta.getColumnLabel(i + 1);
				if (colName.equalsIgnoreCase("ID")) continue;
				else if (colName.equalsIgnoreCase(FIELDS.UPDATE_DT))
				{
					// Ignore if not update mode
					if (isUpdate) appendField(colName, DateUtil.getSqlTimestamp(), fieldStmt, meta.getColumnType(i + 1));
									}
				else if (colName.equalsIgnoreCase(FIELDS.CREATE_DT))
					map.remove(FIELDS.CREATE_DT);
				else if (isUpdate)
				{
					// Only write unchanged field values
					String mapVal = map.get(colName.toUpperCase());
					if (mapVal == null)
					{
						continue;
					}
					String dbVal = rs.getString(colName);
					if (dbVal != null && mapVal.equals(dbVal))
					{
						// Unchanged value so remove from map
						continue;
					}
					appendField(colName, mapVal, fieldStmt, meta.getColumnType(i + 1));
				}
				else
				{
					// For new record add every field
					appendField(colName, map.get(colName.toUpperCase()), fieldStmt, meta.getColumnType(i + 1));
				}
			}
			if (fieldStmt.length() > 0)
			{
				String stmt = "";
				if (isUpdate)
				{
					stmt = "update msg_metadata set " + fieldStmt.toString() + " where " + FIELDS.MSG_ID + " = '" + map.get(msgIdField) + "'"; 
				}
				else
					stmt = "insert into msg_metadata set " + fieldStmt.toString();
				if (s.executeUpdate(stmt) > 0)
				{
					if (logger.isDebugEnabled()) logger.debug("Tracking record successfully persisted to database: " + map);
				}
				else
				{
					throw new OpenAS2Exception("Failed to persist tracking record to DB: " + map);
				}
			}
			else
			{
				if (logger.isInfoEnabled()) logger.info("No change from existing record in DB. Tracking record not updated: " + map);
			}
		} catch (Exception e)
		{
			msg.setLogMsg("Failed to persist a tracking event: " + org.openas2.logging.Log.getExceptionMsg(e)
					+ " ::: Data map: " + map);
			logger.error(msg, e);
		}
		finally
		{
			if (conn != null)
			{
				try
				{
					DBConnection.releaseConnection(conn);
				} catch (OpenAS2Exception e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (SQLException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

	}
	
	private void appendField(String name, String value, StringBuffer sb, int dataType)
	{
		String valueEncap = "'";
		boolean requiresEncap = true; // Assume it is a field requiring encapsulation
		if (value == null) requiresEncap = false; // setting field to NULL 
		else
		{
			switch (dataType)
			{
			case Types.BIGINT:
			case Types.DECIMAL:
			case Types.DOUBLE:
			case Types.FLOAT:
			case Types.INTEGER:
			case Types.NUMERIC:
			case Types.REAL:
			case Types.ROWID:
			case Types.SMALLINT:
				requiresEncap = false;
				break;
			}
		}

		if (sb.length() > 0)
			sb.append(",");

		sb.append(name).append("=");
		if (requiresEncap) sb.append(valueEncap).append(value.replaceAll("'", sqlEscapeChar+"'")).append(valueEncap);
		else sb.append(value);

	}

	public boolean isRunning()
	{
		return isRunning;
	}

	public void start() throws OpenAS2Exception
	{
		DBConnection.start(jdbcConnectString, dbUser, dbPwd);
		isRunning = true;
		if ("true".equalsIgnoreCase(getParameter(PARAM_TCP_SERVER_START, "true")))
		{
			String tcpPort = getParameter(PARAM_TCP_SERVER_PORT, "9092");
			String tcpPwd = getParameter(PARAM_TCP_SERVER_PWD, "OpenAS2");
					
			try
			{
				server = Server.createTcpServer( "-tcpPort", tcpPort, "-tcpPassword", tcpPwd, "-baseDir", dbDirectory, "-tcpAllowOthers").start();
			} catch (SQLException e)
			{
				throw new OpenAS2Exception("Failed to start TCP server", e);
			}
		}
	}

	public void stop()
	{
		try
		{
			// Stopping the TCP server will stop the database so only do one of them
			if (server != null)
			{
				server.shutdown();
			}
			else
			{
				DBConnection.stop(jdbcConnectString);
			}
			isRunning = false;
		} catch (Exception e)
		{
			if (logger.isErrorEnabled())
				logger.error("Failed to stop database for message tracking module.", e);
		}
	}

	/*
	 * Use a static class to make sure we only have one instance of the database
	 * connection pool no matter how many DbTrackingModule instances there are.
	 */
	private static class DBConnection
	{
		private static H2DBHandler dbHandler = null;

		public static Connection getConnection(String connectString, String userId, String pwd)
				throws OpenAS2Exception, SQLException
		{
			if (dbHandler == null)
				throw new OpenAS2Exception("Database has not been started: " + connectString);
			return dbHandler.getConnection();
		}

		public static void releaseConnection(Connection c) throws OpenAS2Exception, SQLException
		{
			if (dbHandler == null)
				throw new OpenAS2Exception("Database has not been started trying to release connection");
			c.close();
		
		}

		public static void start(String connectString, String userId, String pwd) throws OpenAS2Exception
		{
			if (dbHandler == null)
			{
				dbHandler = new H2DBHandler();
				dbHandler.createConnectionPool(connectString, userId, pwd);
			} else
				throw new OpenAS2Exception("Database was already started: " + connectString);
		}

		public static void stop(String connectString) throws OpenAS2Exception, SQLException
		{
			if (dbHandler != null)
			{
				dbHandler.shutdown(connectString);
				dbHandler.destroyConnectionPool();
			}
		}
	}

}