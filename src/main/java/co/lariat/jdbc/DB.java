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

import co.lariat.jdbc.exception.ClassNotFoundException;
import co.lariat.jdbc.generic.GenericConnection;

import java.sql.DriverManager;
import java.sql.SQLException;
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

    public static Connection getConnection(final String url) {
        try {
            loadDriverClass(url);
            java.sql.Connection connection = DriverManager.getConnection(url);
            return new GenericConnection(connection);
        } catch (SQLException e) {
            // Re-throw as a runtime exception
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public static Connection getConnection(final String url, final Properties info) {
        try {
            loadDriverClass(url);
            java.sql.Connection connection = DriverManager.getConnection(url, info);
            return new GenericConnection(connection);
        } catch (SQLException e) {
            // Re-throw as a runtime exception
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public static Connection getConnection(final String url, final String user, final String password) {
        java.sql.Connection connection;

        try {
            loadDriverClass(url);
            connection = DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            // Re-throw as a runtime exception
            throw new co.lariat.jdbc.exception.SQLException(e);
        }

        return new GenericConnection(connection);
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

    protected static void loadDriverClass(final String url) {
        try {
            Vendor vendor = resolveVendor(url);

            // Attempt to load the driver class
            if (vendor.equals(POSTGRESQL)) {
                Class.forName("org.postgresql.Driver");
            } else if (vendor.equals(ORACLE)) {
                Class.forName("oracle.jdbc.driver.OracleDriver");
            } else if (vendor.equals(MYSQL)) {
                Class.forName("com.mysql.jdbc.Driver");
            } else if (vendor.equals(HSQLDB)) {
                Class.forName("org.hsqldb.jdbcDriver");
            }

        } catch (java.lang.ClassNotFoundException e) {
            throw new ClassNotFoundException(e);
        }
    }
}
