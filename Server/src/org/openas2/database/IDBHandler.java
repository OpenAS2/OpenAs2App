package org.openas2.database;

import java.sql.Connection;
import java.sql.SQLException;

import org.openas2.OpenAS2Exception;

public interface IDBHandler
{
    public void setJdbcDriver(String jdbcDriver);
    
    public void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception;

    public void destroyConnectionPool();
    
    public Connection getConnection() throws SQLException, OpenAS2Exception;

    public Connection connect(String connectString, String userName, String password) throws Exception;

    public boolean shutdown(String connectString) throws SQLException, OpenAS2Exception;
}
