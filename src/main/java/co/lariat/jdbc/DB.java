package co.lariat.jdbc;

/*-
 * #%L
 * jdbc-simple
 * %%
 * Copyright (C) 2013 - 2016 Lariat
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import co.lariat.jdbc.generic.GenericSimpleConnection;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static co.lariat.jdbc.Vendor.HSQLDB;
import static co.lariat.jdbc.Vendor.MYSQL;
import static co.lariat.jdbc.Vendor.ORACLE;
import static co.lariat.jdbc.Vendor.POSTGRESQL;

/**
 * @author <a href="mailto:john@lariat.co">John D. Dunlap</a>
 * @since 7/25/16 9:44 PM - Created with IntelliJ IDEA.
 */
public class DB {
    private static final Pattern JDBC_URL_PATTERN = Pattern.compile("^jdbc:([a-zA-Z0-9]+):.*$");
    private static PrintStream printStream = System.out;

    public static PrintStream getPrintStream() {
        return printStream;
    }

    public static void setPrintStream(PrintStream printStream) {
        DB.printStream = printStream;
    }

    public static SimpleConnection getConnection(final String url) throws SQLException {
        loadDriverClass(url);
        java.sql.Connection connection = DriverManager.getConnection(url);
        return new GenericSimpleConnection(connection);
    }

    public static SimpleConnection getConnection(final String url, final Properties info) throws SQLException {
        loadDriverClass(url);
        java.sql.Connection connection = DriverManager.getConnection(url, info);
        return new GenericSimpleConnection(connection);
    }

    public static SimpleConnection getConnection(final String url, final String user, final String password) throws SQLException {
        // Attempt to load the appropriate driver class
        loadDriverClass(url);

        // Attempt to connect to the database
        java.sql.Connection connection = DriverManager.getConnection(url, user, password);

        // Return the connection
        return new GenericSimpleConnection(connection);
    }

    protected static Vendor resolveVendor(final String url) {
        Matcher matcher = JDBC_URL_PATTERN.matcher(url);

        if (matcher.matches()) {
            String vendor = matcher.group(1).toLowerCase();

            if (vendor.equals("postgresql")) {
                return POSTGRESQL;
            } else if (vendor.equals("oracle")) {
                return Vendor.ORACLE;
            } else if (vendor.equals("mysql")) {
                return Vendor.MYSQL;
            } else if (vendor.equals("hsqldb")) {
                return Vendor.HSQLDB;
            } else {
                return Vendor.GENERIC;
            }
        }

        // Default to this
        return Vendor.GENERIC;
    }

    protected static void loadDriverClass(final String url) throws SQLException {
        String driverClass = "";

        try {
            Vendor vendor = resolveVendor(url);

            // Attempt to load the driver class
            if (vendor.equals(POSTGRESQL)) {
                driverClass = " org.postgresql.Driver";
            } else if (vendor.equals(ORACLE)) {
                driverClass = " oracle.jdbc.driver.OracleDriver";
            } else if (vendor.equals(MYSQL)) {
                driverClass = " com.mysql.jdbc.Driver";
            } else if (vendor.equals(HSQLDB)) {
                driverClass = " org.hsqldb.jdbcDriver";
            }

            // Attempt to load the driver class
            if (driverClass.length() > 0) {
                Class.forName(driverClass.trim());
            }

        } catch (java.lang.ClassNotFoundException e) {
            throw new SQLException("Failed to load JDBC driver class" + driverClass, e);
        }
    }

    /**
     * This is a utility method for safely closing a connection object. This method should not, under any circumstances,
     * throw an exception
     * @param connection the connection object which should be closed
     */
    public static void closeConnection(final Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                printStream.print("Could not close connection: ");
                e.printStackTrace(printStream);
            } catch (Throwable e) {
                printStream.print("Encountered unexpected error while attempting to close a connection: ");
                e.printStackTrace(printStream);
            }
        }
    }

    /**
     * This is a utility method for safely closing a statement object. This method should not, under any circumstances,
     * throw an exception
     * @param statement the statement which should be closed
     */
    public static void closeStatement(final Statement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException e) {
                printStream.print("Failed to close statement: ");
                e.printStackTrace(printStream);
            } catch (Throwable e) {
                printStream.print("Encountered unexpected error while attempting to close a statement: ");
                e.printStackTrace(printStream);
            }
        }
    }

    /**
     * This is a utility method for safely closing a resultset object. This method should not, under any circumstances,
     * throw an exception
     * @param resultSet the resultset which should be closed
     */
    public static void closeResultSet(final ResultSet resultSet) {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                printStream.print("Failed to close resultset: ");
                e.printStackTrace(printStream);
            } catch (Throwable e) {
                printStream.print("Encountered unexpected error while attempting to close a resultset: ");
                e.printStackTrace(printStream);
            }
        }
    }
}
