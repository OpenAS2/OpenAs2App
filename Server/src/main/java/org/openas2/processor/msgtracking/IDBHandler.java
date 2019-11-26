package org.openas2.processor.msgtracking;

import org.openas2.OpenAS2Exception;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

interface IDBHandler {

    void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception;

    void destroyConnectionPool();

    Connection getConnection() throws SQLException, OpenAS2Exception;

    boolean shutdown(String connectString) throws SQLException, OpenAS2Exception;

    void start(String jdbcConnectString, String dbUser, String dbPwd, Map<String, String> params) throws OpenAS2Exception;

    void stop();
}
