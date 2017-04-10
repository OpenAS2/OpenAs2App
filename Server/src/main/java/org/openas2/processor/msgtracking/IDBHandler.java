package org.openas2.processor.msgtracking;

import java.sql.Connection;
import java.sql.SQLException;

import org.openas2.OpenAS2Exception;

interface IDBHandler {

    void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception;

    void destroyConnectionPool();

    Connection getConnection() throws SQLException, OpenAS2Exception;

    boolean shutdown(String connectString) throws SQLException, OpenAS2Exception;
}
