package org.openas2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Luc Guinchard
 */
public class DBFactory {

	private static final Log logger = LogFactory.getLog(DBFactory.class.getSimpleName());
	public static final String PARAM_JDBC = "jdbc:";
	public static final int COMMENT_MAX_LENTGH = 2000;

	public static final String CONFIG_NAMED_NODE_NAME = "name";
	public static final String CONFIG_NAMED_NODE_URL = "url";
	public static final String CONFIG_NAMED_NODE_USER = "user";
	public static final String CONFIG_NAMED_NODE_PASSWORD = "password";

	public static HashMap<String, DBFactory> DBFactoryList = new HashMap();
	private final String url;
	private final String user;
	private final String password;
	private Connection connection;

	public DBFactory(String url, String user, String password) {
		if (!url.startsWith(PARAM_JDBC)) {
			this.url = PARAM_JDBC + url;
		} else {
			this.url = url;
		}
		this.user = user;
		this.password = password;
	}

	public static DBFactory getDBFactory(String dbConfig) throws OpenAS2Exception {
		DBFactory dBFactory = null;
		if (dbConfig != null) {
			logger.debug(XMLSession.EL_DATABASECONFIG + ":" + dbConfig);
			dBFactory = DBFactoryList.get(dbConfig);
			if (dBFactory == null) {
				if (DBFactoryList.size() != 1) {
					throw new OpenAS2Exception("A " + XMLSession.EL_DATABASECONFIG + " '" + dbConfig + "' is missing!");
				}
				dBFactory = DBFactoryList.values().iterator().next();
				logger.info("Connection to default DBFactory: " + DBFactoryList.keySet().iterator().next());
			}
			logger.debug("Connection to URL: " + dBFactory.getUrl());
		} else {
			if (DBFactoryList.size() == 1) {
				dBFactory = DBFactoryList.values().iterator().next();
				logger.info("Connection to default DBFactory: " + DBFactoryList.keySet().iterator().next());
			} else {
				logger.info("No DBFactory.");
			}
		}
		return dBFactory;
	}

	public Connection getConnection() throws SQLException {
		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection(url, user, password);
			connection.setAutoCommit(true);
		}
		return connection;

	}

	/**
	 * @return the url
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * @return the user
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}
}
