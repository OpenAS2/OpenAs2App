/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.msgtracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.params.ComponentParameters;
import org.openas2.params.CompositeParameters;
import org.openas2.params.ParameterParser;
import org.openas2.util.DateUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbTrackingModule extends BaseMsgTrackingModule {
    public static final String PARAM_TCP_SERVER_START = "tcp_server_start";
    public static final String PARAM_TCP_SERVER_PORT = "tcp_server_port";
    public static final String PARAM_TCP_SERVER_PWD = "tcp_server_password";
    public static final String PARAM_DB_USER = "db_user";
    public static final String PARAM_DB_PWD = "db_pwd";
    public static final String PARAM_DB_NAME = "db_name";
    public static final String PARAM_TABLE_NAME = "table_name";
    public static final String PARAM_DB_DIRECTORY = "db_directory";
    public static final String PARAM_JDBC_CONNECT_STRING = "jdbc_connect_string";
    public static final String PARAM_JDBC_DRIVER = "jdbc_driver";
    public static final String PARAM_JDBC_SERVER_URL = "jdbc_server_url";
    public static final String PARAM_JDBC_PARAMS = "jdbc_extra_parameters";
    public static final String PARAM_JDBC_POOL_PROPS_FILE = "jdbc_pool_properties_file";
    public static final String PARAM_SQL_ESCAPE_CHARACTER = "sql_escape_character";
    public static final String PARAM_USE_EMBEDDED_DB = "use_embedded_db";
    public static final String PARAM_FORCE_LOAD_JDBC_DRIVER = "force_load_jdbc_driver";

    private String dbUser = null;
    private String dbPwd = null;
    private String jdbcConnectString = null;
    private String configBaseDir = null;
    private String jdbcDriver = null;
    private boolean isRunning = false;
    private String sqlEscapeChar = "'";
    private boolean useEmbeddedDB = true;
    private boolean forceLoadJdbcDriver = false;
    private String dbPlatform = "h2";
    private String tableName = null;
    IDBHandler dbHandler = null;

    private Logger logger = LoggerFactory.getLogger(DbTrackingModule.class);

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        CompositeParameters paramParser = createParser();
        dbUser = getParameter(PARAM_DB_USER, true);
        dbPwd = getParameter(PARAM_DB_PWD, true);
        configBaseDir = session.getBaseDirectory();
        jdbcConnectString = getParameter(PARAM_JDBC_CONNECT_STRING, true);
        jdbcConnectString.replace("%home%", configBaseDir);
        // Support component attributes in connect string
        jdbcConnectString = ParameterParser.parse(jdbcConnectString, paramParser);
        dbPlatform = jdbcConnectString.replaceAll(".*jdbc:([^:]*):.*", "$1");
        jdbcDriver = getParameter(PARAM_JDBC_DRIVER, false);
        sqlEscapeChar = getParameter(PARAM_SQL_ESCAPE_CHARACTER, "'");
        useEmbeddedDB = "true".equals(getParameter(PARAM_USE_EMBEDDED_DB, "true"));
        forceLoadJdbcDriver = "true".equals(getParameter(PARAM_FORCE_LOAD_JDBC_DRIVER, "false"));
        tableName = getParameter(PARAM_TABLE_NAME, "msg_metadata");
        if (!useEmbeddedDB && forceLoadJdbcDriver) {
            try {

                Class.forName(jdbcDriver);

            } catch (ClassNotFoundException e) {

                logger.error("Failed to load JDBC driver: " + jdbcDriver, e);
                e.printStackTrace();
                return;

            }
        }
    }

    protected CompositeParameters createParser() {
        CompositeParameters params = new CompositeParameters(true);

        params.add("component", new ComponentParameters(this));
        return params;
    }

    protected void persist(Message msg, Map<String, String> map) {
        try (Connection conn = dbHandler.getConnection()) {
            Statement s = conn.createStatement();
            String msgIdField = FIELDS.MSG_ID;
            ResultSet rs = s.executeQuery(
                    "SELECT * FROM " + tableName + " WHERE " + msgIdField + " = '" + map.get(msgIdField) + "'");
            ResultSetMetaData meta = rs.getMetaData();
            boolean isUpdate = rs.next(); // Record already exists so update
            if (logger.isTraceEnabled()) {
                logger.trace(
                        "\t\t *** Tracking record found: " + isUpdate + "\n\t\t *** Tracking record metadata: " + meta);
            }
            StringBuffer fieldStmt = new StringBuffer();
            StringBuffer valuesStmt = new StringBuffer();
            for (int i = 0; i < meta.getColumnCount(); i++) {
                String colName = meta.getColumnLabel(i + 1);
                if (colName.equalsIgnoreCase("id")) {
                    continue;
                } else if (colName.equalsIgnoreCase(FIELDS.UPDATE_DT)) {
                    // Ignore if not update mode
                    if (isUpdate) {
                        appendFieldForUpdate(colName, DateUtil.getSqlTimestamp(), fieldStmt, meta.getColumnType(i + 1));
                    }
                } else if (colName.equalsIgnoreCase(FIELDS.CREATE_DT)) {
                    if (isUpdate) {
                        map.remove(FIELDS.CREATE_DT);
                    } else {
                        appendFieldForInsert(colName, DateUtil.getSqlTimestamp(), fieldStmt, valuesStmt,
                                meta.getColumnType(i + 1));
                    }
                } else if (isUpdate) {
                    /*
                     * Only write unchanged field values. Map is field names in LOWER case so
                     * convert in case DB server returns column names in uppercase
                     */
                    String mapVal = map.get(colName.toLowerCase());
                    if (mapVal == null) {
                        continue;
                    }
                    String dbVal = rs.getString(colName);
                    if (dbVal != null && mapVal.equals(dbVal)) {
                        // Unchanged value so remove from map
                        continue;
                    }
                    appendFieldForUpdate(colName, mapVal, fieldStmt, meta.getColumnType(i + 1));
                } else {
                    // For new record add every field that is not NULL
                    String mapVal = map.get(colName.toLowerCase());
                    if (mapVal == null) {
                        continue;
                    }
                    appendFieldForInsert(colName, mapVal, fieldStmt, valuesStmt, meta.getColumnType(i + 1));
                }
            }
            if (fieldStmt.length() > 0) {
                String stmt = "";
                if (isUpdate) {
                    stmt = "UPDATE " + tableName + " SET " + fieldStmt.toString() + " WHERE " + FIELDS.MSG_ID + " = '"
                            + map.get(msgIdField) + "'";
                } else {
                    stmt = "INSERT INTO " + tableName + " (" + fieldStmt.toString() + ") VALUES ("
                            + valuesStmt.toString() + ")";
                }
                if (s.executeUpdate(stmt) > 0) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Tracking record SQL statement: " + stmt);
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Tracking record successfully persisted to database: " + map);
                    }
                } else {
                    throw new OpenAS2Exception("Failed to persist tracking record to DB: " + map);
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("No change from existing record in DB. Tracking record not updated: " + map);
                }
            }
        } catch (Exception e) {
            msg.setLogMsg("Failed to persist a tracking event: " + org.openas2.util.Logging.getExceptionMsg(e)
                    + " ::: Data map: " + map);
            logger.error(msg.getLogMsg(), e);
        }

    }

    public ArrayList<HashMap<String, String>> listMessages() {

        ArrayList<HashMap<String, String>> rows = new ArrayList<HashMap<String, String>>();

        try (Connection conn = dbHandler.getConnection()) {
            Statement s = conn.createStatement();
            ResultSet rs = s.executeQuery("SELECT " + FIELDS.MSG_ID
                    + ",CREATE_DT,SENDER_ID,RECEIVER_ID,MSG_ID,FILE_NAME,ENCRYPTION_ALGORITHM,SIGNATURE_ALGORITHM,MDN_MODE,STATE FROM "
                    + tableName);
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                HashMap<String, String> row = new HashMap<String, String>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String key = meta.getColumnName(i);
                    String value = rs.getString(key);
                    row.put(key, value);
                }
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

    public HashMap<String, String> showMessage(String msg_id) {

        HashMap<String, String> row = new HashMap<String, String>();
        try (Connection conn = dbHandler.getConnection()) {
            PreparedStatement s = conn
                    .prepareStatement("SELECT * FROM " + tableName + " WHERE " + FIELDS.MSG_ID + " = ?");
            s.setString(1, msg_id);
            ResultSet rs = s.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String key = meta.getColumnName(i);
                    String value = rs.getString(key);
                    row.put(key, value);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return row;
    }

    public ArrayList<HashMap<String, String>> getDataCharts(HashMap<String, String> map) {
        ArrayList<HashMap<String, String>> rows = new ArrayList<HashMap<String, String>>();
        try (Connection conn = dbHandler.getConnection()) {
            Statement s = conn.createStatement();
            ResultSet rs = s.executeQuery("SELECT " + FIELDS.MSG_ID + ",STATE,STATUS,CREATE_DT FROM " + tableName
                    + " WHERE CREATE_DT BETWEEN CAST('" + map.get("startDate").toString()
                    + " 00:00:00' as TIMESTAMP) AND CAST('" + map.get("endDate").toString()
                    + " 23:59:59' as TIMESTAMP)");
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                HashMap<String, String> row = new HashMap<String, String>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String key = meta.getColumnName(i);
                    String value = rs.getString(key);
                    row.put(key, value);
                }
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

    private String formatField(String value, int dataType) {
        if (value == null) {
            return "NULL";
        }
        switch (dataType) {
        case Types.BIGINT:
        case Types.DECIMAL:
        case Types.DOUBLE:
        case Types.FLOAT:
        case Types.INTEGER:
        case Types.NUMERIC:
        case Types.REAL:
        case Types.SMALLINT:
        case Types.BINARY:
        case Types.TINYINT:
            // case Types.ROWID:
            return value;
        case Types.TIME_WITH_TIMEZONE:
        case Types.TIMESTAMP_WITH_TIMEZONE:
        case Types.DATE:
        case Types.TIME:
        case Types.TIMESTAMP:
            if ("oracle".equalsIgnoreCase(dbPlatform)) {
                if (value.length() > 19) {
                    return ("TO_TIMESTAMP('" + value + "','YYYY-MM-DD HH24:MI:SS.FF')");
                } else {
                    return ("TO_DATE('" + value + "','YYYY-MM-DD HH24:MI:SS')");
                }
            } else if ("mssql".equalsIgnoreCase(dbPlatform)) {
                return ("CAST('" + value + "' AS DATETIME)");
            } else {
                return "'" + value + "'";
            }
        }
        // Must be some kind of string value if it gets here
        return "'" + value.replaceAll("'", sqlEscapeChar + "'") + "'";
    }

    private void appendFieldForUpdate(String name, String value, StringBuffer sb, int dataType) {
        if (sb.length() > 0) {
            sb.append(",");
        }
        sb.append(name).append("=").append(formatField(value, dataType));
    }

    private void appendFieldForInsert(String name, String value, StringBuffer names, StringBuffer values,
            int dataType) {
        if (names.length() > 0) {
            names.append(",");
            values.append(",");
        }
        names.append(name);
        values.append(formatField(value, dataType));
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void start() throws OpenAS2Exception {
        if (useEmbeddedDB) {
            dbHandler = new EmbeddedDBHandler();
        } else {
            dbHandler = new ExternalDBHandler();
        }
        dbHandler.start(jdbcConnectString, dbUser, dbPwd, getParameters());
        isRunning = true;

    }

    public void stop() {
        dbHandler.stop();
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        try (Connection conn = dbHandler.getConnection()) {
            Statement s = conn.createStatement();
            s.executeQuery("SELECT COUNT(*) FROM " + tableName);
        } catch (Exception e) {
            failures.add(this.getClass().getSimpleName() + " - Failed to check DB tracking module connection to DB: "
                    + e.getMessage() + " :: Connect String: " + jdbcConnectString);
            return false;
        }
        return true;
    }

}
