package org.openas2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
	public static final String CONFIG_NAMED_NODE_TABLE_MESSAGE = "table_message";
	//Fields of table : AS2_Message
	public static final String TABLE_FORMAT = "`{0}`";
	public static final String TABLE_MESSAGE = "as2_message";
	public static final String FIELD_MESSAGE_PARTNERSHIP = "`partnership`";
	public static final String FIELD_MESSAGE_ID = "`message_id`";
	public static final String FIELD_MESSAGE_FILENAME = "`filename`";
	public static final String FIELD_MESSAGE_DATE = "`date`";
	public static final String FIELD_MESSAGE_MDN_ID = "`mdn_id`";
	public static final String FIELD_MESSAGE_MDN_FILENAME = "`mdn_filename`";
	public static final String FIELD_MESSAGE_MDN_DATE = "`mdn_date`";
	public static final String FIELD_MESSAGE_STATUS = "`status`";
	public static final String FIELD_MESSAGE_COMMENT = "`comment`";

	/**
	 * @return the tableMessage
	 */
	public String getTableMessage() {
		return tableMessage;
	}

	public static enum MSG_STATUS {

		//Envoi:

		/**
		 * Fichier soumis dans répertoire d'envoi
		 */
		FILE_SUBMITTED,
		/**
		 * Message en attente d'un MDN
		 */
		MDN_WAITING,
		/**
		 * Message envoyé sans demande de MDN [statut final]
		 */
		MSG_SENT,
		/**
		 * Impossibilité d'envoyer le message [statut final]
		 */
		MSG_NOTSENT,
		/**
		 * Impossibilité d'envoyer le message, le message sera à nouveau envoyé
		 */
		MSG_RETRY,
		/**
		 * MDN reçu [statut final]
		 */
		MDN_RECEIVED,
		/**
		 * Message envoyé mais MDN non reçu [statut final]
		 */
		MDN_NOT_RECEIVED,
		/**
		 * Réception d'un MDN du message en erreur [statut final]
		 */
		MSG_ERROR,
		/**
		 * MDN en erreur
		 */
		MDN_ERROR,
		//Réception
		/**
		 * message AS2 reçu sans demande de MDN [statut final
		 */
		MSG_RECEIVED,
		/**
		 * message AS2 reçu, MDN en cours d'envoi
		 */
		MDN_SENDING,
		/**
		 * MDN envoyé [statut final]
		 */
		MDN_SENT,
		/**
		 * Impossibilité d'envoyer le MDN [statut final]
		 */
		MDN_NOTSENT,
		/**
		 * Impossibilité d'envoyer le MDN, Le MDN sera à nouveau envoyé
		 */
		MDN_RETRY
	};
	public static HashMap<String, DBFactory> DBFactoryList = new HashMap();
	private final String url;
	private final String user;
	private final String password;
	private Connection connection;
	private String tableMessage = TABLE_MESSAGE;

	public DBFactory(String url, String user, String password) {
		if (!url.startsWith(PARAM_JDBC)) {
			this.url = PARAM_JDBC + url;
		} else {
			this.url = url;
		}
		this.user = user;
		this.password = password;
	}

	DBFactory(String url, String user, String password, String tableMessage) {
		this(url, user, password);
		if (tableMessage != null) {
			this.tableMessage = tableMessage;
		}
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

	public static void addMessageZ(String dbConfig, String messageId, String partnershipName, String payload, MSG_STATUS status, String statusComment) throws OpenAS2Exception {
		DBFactory dBFactory = DBFactory.getDBFactory(dbConfig);
		if (dBFactory != null) {
			Connection connection = null;
			String requete = null;
			try {
				List<Object> vars = new ArrayList();
				connection = dBFactory.getConnection();
				requete = "INSERT INTO " + MessageFormat.format(TABLE_FORMAT, dBFactory.getTableMessage()) + " ("
						+ FIELD_MESSAGE_PARTNERSHIP + ", "
						+ FIELD_MESSAGE_ID + ", "
						+ FIELD_MESSAGE_FILENAME + ", "
						+ FIELD_MESSAGE_STATUS + ", "
						+ FIELD_MESSAGE_COMMENT + ") "
						+ "VALUES (?, ?, ?, ?, ?);";
				vars.add(partnershipName);
				vars.add(messageId);
				vars.add(payload);
				vars.add(status.name());
				if (statusComment != null && statusComment.length() > COMMENT_MAX_LENTGH) {
					statusComment = statusComment.substring(0, COMMENT_MAX_LENTGH);
				}
				vars.add(statusComment);

				PreparedStatement statement = connection.prepareStatement(requete);
				for (int i = 0; i < vars.size(); i++) {
					statement.setObject(i + 1, vars.get(i));
				}
				logger.debug("requete: " + requete);
				logger.debug("status: " + status + " -> " + statusComment);
				statement.executeUpdate();
			} catch (SQLException e) {
				logger.error("requete: " + requete);
				logger.error(e.getMessage());
				logger.error(e.getSQLState());
			}
		}
	}

	public static void updateMessageZ(String dbConfig, String messageId, String fileName) throws OpenAS2Exception {
		DBFactory dBFactory = DBFactory.getDBFactory(dbConfig);
		if (dBFactory != null) {
			Connection connection = null;
			String requete = null;
			try {
				List<Object> vars = new ArrayList();
				connection = dBFactory.getConnection();
				requete = "UPDATE `" + dBFactory.getTableMessage() + "` SET "
						+ FIELD_MESSAGE_FILENAME + " = ?";
				vars.add(fileName);
				requete += " WHERE " + FIELD_MESSAGE_ID + " = ?;";
				vars.add(messageId);
				PreparedStatement statement = connection.prepareStatement(requete);
				for (int i = 0; i < vars.size(); i++) {
					statement.setObject(i + 1, vars.get(i));
				}
				logger.debug("requete: " + requete);

				statement.executeUpdate();
			} catch (SQLException e) {
				logger.error("requete: " + requete);
				logger.error(e.getMessage());
				logger.error(e.getSQLState());
			}
		}
	}

	public static void updateMessageZ(String dbConfig, String messageId, MSG_STATUS status, String statusComment) throws OpenAS2Exception {
		updateMessageZ(dbConfig, messageId, null, status, statusComment);
	}

	public static void updateMessageZ(String dbConfig, String messageId, String filename, MSG_STATUS status, String statusComment) throws OpenAS2Exception {
		updateMessageZ(dbConfig, messageId, filename, status, statusComment, null, null);
	}

	public static void updateMessageZ(String dbConfig, String messageId, MSG_STATUS status, String statusComment, String mdn, Date mdnDate) throws OpenAS2Exception {
		updateMessageZ(dbConfig, messageId, null, status, statusComment, mdn, mdnDate);
	}

	public static void updateMessageZ(String dbConfig, String messageId, String filename, MSG_STATUS status, String statusComment, String mdn, Date mdnDate) throws OpenAS2Exception {
		DBFactory dBFactory = DBFactory.getDBFactory(dbConfig);
		if (dBFactory != null) {
			Connection connection = null;
			String requete = null;
			try {
				List<Object> vars = new ArrayList();
				connection = dBFactory.getConnection();
				requete = "UPDATE `" + dBFactory.getTableMessage() + "` SET "
						+ FIELD_MESSAGE_STATUS + " = ?,"
						+ FIELD_MESSAGE_COMMENT + " = ?";
				vars.add(status.name());
				if (statusComment != null && statusComment.length() > COMMENT_MAX_LENTGH) {
					statusComment = statusComment.substring(0, COMMENT_MAX_LENTGH);
				}
				vars.add(statusComment);
				if (filename != null) {
					requete += " ," + FIELD_MESSAGE_FILENAME + " = ?";
					vars.add(filename);
				}
				if (mdn != null) {
					requete += " ," + FIELD_MESSAGE_MDN_ID + " = ?";
					vars.add(mdn);
				}
				if (mdnDate != null) {
					requete += " ," + FIELD_MESSAGE_MDN_DATE + " = ?";
					vars.add(new java.sql.Timestamp(mdnDate.getTime()));
				}
				requete += " WHERE " + FIELD_MESSAGE_ID + " = ?;";
				vars.add(messageId);
				PreparedStatement statement = connection.prepareStatement(requete);
				for (int i = 0; i < vars.size(); i++) {
					statement.setObject(i + 1, vars.get(i));
				}
				logger.debug("requete: " + statement.toString());
				statement.executeUpdate();
				logger.debug("status: " + status + " -> " + statusComment);
			} catch (SQLException e) {
				logger.error("requete: " + requete);
				logger.error(e.getMessage());
				logger.error(e.getSQLState());
			}
		}
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
