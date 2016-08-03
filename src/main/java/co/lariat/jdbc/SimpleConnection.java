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


import javax.persistence.Entity;
import javax.sql.DataSource;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Connection;
import java.sql.Ref;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.Executor;

/**
 * This object implements {@link java.sql.Connection} by accepting a reference to an instance which implements
 * {@link java.sql.Connection}, during construction, and proxying all methods to their counterparts in that instance.
 * This is necessary because we can't can't extend the object which implements {@link java.sql.Connection} interface
 * because it is provided by the JDBC driver and, as such, will be different between databases. Additional methods which
 * simplify database interaction have been added to this object. Further, it is intended that this object be extended to
 * support vendor specific features, which are not available in all databases.
 *
 * @author <a href="mailto:john@lariat.co">John D. Dunlap</a>
 * @since 9/14/15 3:26 PM - Created with IntelliJ IDEA.
 */
public abstract class SimpleConnection implements Connection {
    /**
     * The connection which should be used to interact with the database
     */
    private Connection connection;

    /**
     * Construct an instance of this class and use the provided {@link javax.sql.DataSource} to obtain
     * a {@link java.sql.Connection}
     * @param dataSource the datasource which should be used to obtain a database connection
     * @throws SQLException throw when something exceptional happens
     */
    public SimpleConnection(final DataSource dataSource) throws SQLException {
        this.connection = dataSource.getConnection();
    }

    /**
     * Construct an instance of this class with the provided {@link java.sql.Connection} object
     * @param connection the connection object which should be used to access the database
     */
    public SimpleConnection(final Connection connection) {
        this.connection = connection;
    }

    /**
     * Prepares the specified sql statement for execution and bind the provided arguments to it
     * @param sql the sql which should be prepared
     * @param arguments the arguments which should be bound to the statement
     * @return a reference to the prepared statement after the arguments have been bound to it
     * @throws SQLException thrown when something exceptional happens
     */
    public PreparedStatement prepareStatement(final String sql, final Object... arguments) throws SQLException {
        return bindArguments(connection.prepareStatement(sql), arguments);
    }

    /**
     * This must be implemented by a child class so that the child class can control which child class of
     * {@link SimpleResultSet} is returned
     * @param statement the statement which should be used to obtain the resultset
     * @return reference to the resultset
     * @throws SQLException thrown when something exceptional happens
     */
    protected abstract SimpleResultSet fetch(final PreparedStatement statement) throws SQLException;

    /**
     * Fetches a {@link SimpleResultSet} from the provided sql and arguments.
     * @param sql the sql query which should be executed
     * @param arguments the arguments which should be bound to the query
     * @return a reference to an instance of {@link SimpleResultSet} which contains the results of the query
     * @throws SQLException thrown when something exceptional happens
     */
    public SimpleResultSet fetch(final String sql, final Object... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        SimpleResultSet resultSet = fetch(statement, arguments);
        DB.closeStatement(statement); // Close this here because a reference to it isn't available outside of this method
        return resultSet;
    }

    /**
     * Bind the provided arguments to the provided statement, execute the statement, and return an instance of
     * {@link SimpleResultSet}
     * @param statement the statement which should be executed
     * @param arguments the arguments which should be bound to the query
     * @return an instance of {@link SimpleResultSet} which contains the results of the query
     * @throws SQLException thrown when something exceptional happens
     */
    public SimpleResultSet fetch(final PreparedStatement statement, final Object... arguments) throws SQLException {
        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        return fetch(statement);
    }

    public <T> T fetch(final ResultSetHandler<T> handler, final String sql, final Object ... arguments) throws SQLException {
        PreparedStatement statement = prepareCall(sql);

        T value = fetch(handler, statement, arguments);

        // Close this here because a reference to it isn't returned
        DB.closeStatement(statement);

        return value;
    }

    public <T> T fetch(final ResultSetHandler<T> handler, final PreparedStatement statement, final Object ... arguments) throws SQLException {
        SimpleResultSet resultSet = fetch(statement, arguments);

        // Return null if there is no data
        if (!resultSet.next()) {
            return null;
        }

        T value = handler.handle(resultSet);

        // Close this here because a reference to it isn't returned
        DB.closeResultSet(resultSet);

        return value;
    }

    public List<Integer> fetchListInteger(final String sql, final Object... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        List<Integer> list = fetchListInteger(statement, arguments);

        // Close this here because a reference to it isn't returned
        DB.closeStatement(statement);

        return list;
    }

    public List<Integer> fetchListInteger(final PreparedStatement statement, final Object... arguments) throws SQLException {
        List<Integer> list = fetch(new ResultSetHandler<List<Integer>>() {
            @Override
            public List<Integer> handle(final SimpleResultSet simpleResultSet) throws SQLException {
                List<Integer> integerList = new ArrayList<>();

                do {
                    integerList.add(simpleResultSet.getInt(1));
                } while (simpleResultSet.next());

                return integerList;
            }
        }, statement, arguments);


        if (list == null) {
            return new ArrayList<>();
        }

        return list;
    }

    public List<Long> fetchListLong(final String sql, final Object... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        List<Long> list = fetchListLong(statement, arguments);

        // Close this here because a reference to it isn't returned
        DB.closeStatement(statement);

        return list;
    }

    public List<Long> fetchListLong(final PreparedStatement statement, final Object... arguments) throws SQLException {
        List<Long> list = fetch(new ResultSetHandler<List<Long>>() {
            @Override
            public List<Long> handle(final SimpleResultSet simpleResultSet) throws SQLException {
                List<Long> integerList = new ArrayList<>();

                do {
                    integerList.add(simpleResultSet.getLong(1));
                } while (simpleResultSet.next());

                return integerList;
            }
        }, statement, arguments);

        if (list == null) {
            return new ArrayList<>();
        }

        return list;
    }

    /**
     * Exceutes the procided statement
     * @param statement the statement which should be executed
     * @return true if the statement contains a resultset and false otherwise
     * @throws SQLException thrown if something exceptional happens
     */
    public boolean execute(final PreparedStatement statement) throws SQLException {
        return statement.execute();
    }

    /**
     * Binds the provided arguments to the provided sql query, executes the query
     * @param sql the sql which should be executed
     * @param arguments the arguments which should be bound to the query
     * @return true if the query returns a resultset and false otherwise. If true is returned and you need access to the
     * resultset, use the <i>execute(PreparedStatement, Object...)</i> or <i>fetch(PreparedStatement, Object...)</i>
     * methods instead
     * @throws SQLException thrown when something exceptional happens
     */
    public boolean execute(final String sql, final Object... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        boolean results = execute(statement, arguments);
        DB.closeStatement(statement); // Close this here because a reference to it isn't returned
        return results;
    }

    /**
     * Binds the provided arguments to the provided statement and executes the statement.
     * @param statement the statement which should be executed
     * @param arguments the arguments which should be bound to the statement
     * @return true if a resultset is available and false otherwise
     * @throws SQLException thrown when something exceptional happens
     */
    public boolean execute(final PreparedStatement statement, final Object... arguments) throws SQLException {
        return execute(bindArguments(statement, arguments));
    }

    /**
     * Returns a list of entities which are instances of the specified class and which have been populated by the
     * provided sql and arguments.
     * @param clazz the class type of the entities which should be returned
     * @param sql the sql which should be used to obtain data from the database
     * @param arguments the arguments which should be bound to the query
     * @param <T> the generic type of the entities which should be returned
     * @return a list of entities which have the results of the query injected into them
     * @throws SQLException thrown when something exceptional happens
     */
    public <T> List<T> fetchAllEntity(final Class<T> clazz, final String sql, final Object... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        List<T> list = fetchAllEntity(clazz, statement, arguments);
        DB.closeStatement(statement); // Close this here because a reference to it isn't returned
        return list;
    }

    public <T> List<T> fetchAllEntity(final Class<T> clazz, final PreparedStatement statement, final Object... arguments) throws SQLException {
        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        SimpleResultSet simpleResultSet = fetch(statement);

        List<T> entities = new ArrayList<>();

        // Iterate over the results
        while (simpleResultSet.next()) {
            entities.add(fetchEntity(clazz, simpleResultSet));
        }

        DB.closeResultSet(simpleResultSet); // Close this here because a reference to it isn't returned

        return entities;
    }

    public <T> Map<String, T> fetchAllEntityMap(final Class<T> clazz, final String columnLabel, final String sql, final Object... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        Map<String, T> map = fetchAllEntityMap(clazz, columnLabel, statement, arguments);
        DB.closeStatement(statement); // Close this here because a reference to it isn't returned
        return map;
    }

    public <T> Map<String, T> fetchAllEntityMap(final Class<T> clazz, final String columnLabel, final PreparedStatement statement, final Object... arguments) throws SQLException {
        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        SimpleResultSet simpleResultSet = fetch(statement);

        Map<String, T> entities = new HashMap<>();

        // Iterate over the results
        while (simpleResultSet.next()) {
            entities.put(simpleResultSet.getStringByName(columnLabel), fetchEntity(clazz, simpleResultSet));
        }

        DB.closeResultSet(simpleResultSet); // Close this here because a reference to it isn't returned

        return entities;
    }

    protected <T> T fetchEntity(final Class<T> clazz, final SimpleResultSet simpleResultSet) throws SQLException {
        try {
            T entity = clazz.newInstance();
            return fetchEntity(entity, simpleResultSet);
        } catch (InstantiationException e) {
            throw new SQLException("Cannot instantiate entity: " + clazz.getCanonicalName(), e);
        } catch (IllegalAccessException e) {
            throw new SQLException("Cannot access constructor of entity: " + clazz.getCanonicalName(), e);
        }
    }

    protected <T> T fetchEntity(final T entity, final SimpleResultSet simpleResultSet) throws SQLException {
        try {
            int columnCount = simpleResultSet.getColumnCount();

            for (int index = 1; index <= columnCount; index++) {
                String columnName = toCamelCase(simpleResultSet.getColumnName(index));
                String columnClassName = simpleResultSet.getColumnClassName(index);
                Object value = simpleResultSet.getValue(index);

                // Attempt to find the appropriate setter method
                SetterMethod setterMethod = findSetter(entity, columnName, columnClassName);

                Class argumentType = setterMethod.getArgumentType();

                // Perform automatic type conversions, where possible
                if (argumentType.equals(Long.class) && value instanceof Integer) {
                    value = new Long((Integer) value);
                }
                if (argumentType.equals(Date.class) && value instanceof java.sql.Timestamp) {
                    value = new Date(((java.sql.Timestamp) value).getTime());
                }

                // Invoke the setter
                setterMethod.invoke(entity, value);
            }

            return entity;
        } catch (NoSuchMethodException e) {
            throw new SQLException("Cannot invoke setter: ", e);
        } catch (IllegalAccessException e) {
            throw new SQLException("Cannot invoke setter: ", e);
        } catch (InvocationTargetException e) {
            throw new SQLException("Cannot invoke setter: ", e);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Cannot invoke setter: ", e);
        }
    }

    public List<Map<String, Object>> fetchAllMap(final String sql, final Object ... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        List<Map<String, Object>> list = fetchAllMap(statement, arguments);
        DB.closeStatement(statement); // Close this here because a reference to it isn't returned
        return list;
    }

    public List<Map<String, Object>> fetchAllMap(final PreparedStatement statement, final Object ... arguments) throws SQLException {
        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        SimpleResultSet simpleResultSet = fetch(statement);

        List<Map<String, Object>> entities = new ArrayList<>();

        // Iterate over the results
        while(simpleResultSet.next()) {
            entities.add(fetchMap(simpleResultSet));
        }

        DB.closeResultSet(simpleResultSet); // Close this here because a reference to it isn't returned

        // Not technically type safe but necessary to create the illusion of it
        return entities;
    }

    public <T> T fetchEntity(final T entity, final String sql, final Object ... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        T e = fetchEntity(entity, statement, arguments);
        DB.closeStatement(statement); // Close this here because a reference to it isn't returned
        return e;
    }

    public <T> T fetchEntity(final T entity, final PreparedStatement statement, final Object ... arguments) throws SQLException {
        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        SimpleResultSet simpleResultSet = fetch(statement);

        int count = 0;

        // Iterate over the results
        while(simpleResultSet.next()) {
            if (count > 0) {
                throw new SQLException("Encountered a second record where a single record was expected");
            }

            // Populate the entity
            fetchEntity(entity, simpleResultSet);
            count++;
        }

        DB.closeResultSet(simpleResultSet); // Close this here because a reference to it isn't returned

        return entity;
    }

    public <T> T fetchEntity(final Class<T> clazz, final String sql, final Object ... arguments) throws SQLException {
        try {
            T entity = clazz.newInstance();
            return fetchEntity(entity, sql, arguments);
        } catch (InstantiationException e) {
            throw new SQLException("Cannot instantiate entity: " + clazz.getCanonicalName(), e);
        } catch (IllegalAccessException e) {
            throw new SQLException("Cannot access constructor of entity: " + clazz.getCanonicalName(), e);
        }
    }

    public Map<String, Object> fetchMap(final String sql, final Object ... arguments) throws SQLException {
        PreparedStatement statement = prepareStatement(sql);
        Map<String, Object> map = fetchMap(statement, arguments);
        DB.closeStatement(statement); // Close this here because a reference to it isn't returned
        return map;
    }

    public Map<String, Object> fetchMap(final PreparedStatement statement, final Object ... arguments) throws SQLException {
        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        SimpleResultSet simpleResultSet = fetch(statement);

        int count = 0;

        Map<String, Object> entity = null;

        // Iterate over the results
        while (simpleResultSet.next()) {
            if (count > 0) {
                throw new SQLException("Encountered a second record where a single record was expected");
            }

            entity = fetchMap(simpleResultSet);
            count++;
        }

        DB.closeResultSet(simpleResultSet); // Close this here because a reference to it isn't returned

        return entity;
    }

    protected Map<String, Object> fetchMap(final SimpleResultSet simpleResultSet) throws SQLException {
        Map<String, Object> entity = new HashMap<>();

        int columnCount = simpleResultSet.getColumnCount();

        for (int index = 1; index <= columnCount; index++) {
            String columnName = simpleResultSet.getColumnName(index).toLowerCase();
            Object value = simpleResultSet.getValue(index);
            entity.put(columnName, value);
        }

        return entity;
    }

    /**
     * Fetches a single string from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested string or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public String fetchString(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<String>() {
            @Override
            public String handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getString(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single integer from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested integer or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Integer fetchInt(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Integer>() {
            @Override
            public Integer handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getInt(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single short from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested short or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Short fetchShort(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Short>() {
            @Override
            public Short handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getShort(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single byte from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested byte or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Byte fetchByte(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Byte>() {
            @Override
            public Byte handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getByte(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single timestamp from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested timestamp or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Timestamp fetchTimestamp(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Timestamp>() {
            @Override
            public Timestamp handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getTimestamp(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single time from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested time or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Time fetchTime(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Time>() {
            @Override
            public Time handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getTime(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single url from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested url or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public URL fetchURL(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<URL>() {
            @Override
            public URL handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getURL(1);
            }
        }, sql, args);
    }

    /**
     * Fetches an array from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested array or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Array fetchArray(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Array>() {
            @Override
            public Array handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getArray(1);
            }
        }, sql, args);
    }

    /**
     * Fetches an ASCII stream from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested ASCII stream or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public InputStream fetchAsciiStream(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<InputStream>() {
            @Override
            public InputStream handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getAsciiStream(1);
            }
        }, sql, args);
    }

    /**
     * Fetches an binary stream from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested binary stream or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public InputStream fetchBinaryStream(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<InputStream>() {
            @Override
            public InputStream handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getBinaryStream(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a blob from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested blob stream or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Blob fetchBlob(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Blob>() {
            @Override
            public Blob handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getBlob(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a character stream from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested character stream or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Reader fetchCharacterStream(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Reader>() {
            @Override
            public Reader handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getCharacterStream(1);
            }
        }, sql, args);
    }

    /**
     * Fetches an NCharacter stream from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested NCharacter stream or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Reader fetchNCharacterStream(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Reader>() {
            @Override
            public Reader handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getNCharacterStream(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a clob from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested clob or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Clob fetchClob(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Clob>() {
            @Override
            public Clob handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getClob(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a NClob from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested NClob or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public NClob fetchNClob(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<NClob>() {
            @Override
            public NClob handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getNClob(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a ref from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested ref or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Ref fetchRef(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Ref>() {
            @Override
            public Ref handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getRef(1);
            }
        }, sql, args);
    }

    /**
     * Fetches an NString from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested NString or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public String fetchNString(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<String>() {
            @Override
            public String handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getNString(1);
            }
        }, sql, args);
    }

    /**
     * Fetches an SQLXML from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested SQLXML or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public SQLXML fetchSQLXML(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<SQLXML>() {
            @Override
            public SQLXML handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getSQLXML(1);
            }
        }, sql, args);
    }

    /**
     * Fetches an array of bytes from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested array of bytes
     * @throws SQLException thrown when something exceptional happens
     */
    public byte[] fetchBytes(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<byte[]>() {
            @Override
            public byte[] handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getBytes(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single long from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested long or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Long fetchLong(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Long>() {
            @Override
            public Long handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getLong(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single float from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested float or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Float fetchFloat(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Float>() {
            @Override
            public Float handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getFloat(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single double from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested double or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Double fetchDouble(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Double>() {
            @Override
            public Double handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getDouble(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single big decimal from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested big decimal or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public BigDecimal fetchBigDecimal(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<BigDecimal>() {
            @Override
            public BigDecimal handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getBigDecimal(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single date from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested date or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Date fetchDate(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Date>() {
            @Override
            public Date handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getDate(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single boolean from the database
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested boolean or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Boolean fetchBoolean(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Boolean>() {
            @Override
            public Boolean handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getBoolean(1);
            }
        }, sql, args);
    }

    /**
     * Fetches a single Object from the database. Reflection must be used to infer the actual
     * data type which was returned.
     * @param sql the sql which should be sent to the database
     * @param args the arguments which should be sent to the database
     * @return the requested object or null if nothing was returned by the database
     * @throws SQLException thrown when something exceptional happens
     */
    public Object fetchObject(final String sql, final Object ... args) throws SQLException {
        return fetch(new ResultSetHandler<Object>() {
            @Override
            public Object handle(final SimpleResultSet simpleResultSet) throws SQLException {
                return simpleResultSet.getObject(1);
            }
        }, sql, args);
    }

    /**
     * Attempts to bind the specified arguments to the specified statement
     * @param statement the statement to which the arguments should be bound
     * @param args the arguments which should be bound to the statement
     * @return the statement after the arguments have been bound to it
     * @throws SQLException thrown when something exceptional happens
     */
    public PreparedStatement bindArguments(final PreparedStatement statement, final Object ... args) throws SQLException {
        int index = 1;

        // Iterate through the arguments
        for (Object argument : args) {
            // Attempt to bind the argument to the query
            bindObject(statement, index, argument);
            index++;
        }

        return statement;
    }

    /**
     * Binds the specified object to the specified statement in the specified position. If a null object is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the object should be bound
     * @param position the position in which the object should be bound
     * @param value the object which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindObject(final PreparedStatement statement, final int position, final Object value) throws SQLException {
        if (value == null) {
            statement.setNull(position, Types.NULL);
        } else if (value instanceof String) {
            bindString(statement, position, (String) value);
        } else if (value instanceof Integer) {
            bindInteger(statement, position, (Integer) value);
        } else if (value instanceof Long) {
            bindLong(statement, position, (Long) value);
        } else if (value instanceof Float) {
            bindFloat(statement, position, (Float) value);
        } else if (value instanceof Double) {
            bindDouble(statement, position, (Double) value);
        } else if (value instanceof Boolean) {
            bindBoolean(statement, position, (Boolean) value);
        } else if (value instanceof Time) {
            bindTime(statement, position, (Time) value);
        } else if (value instanceof Timestamp) {
            bindTimestamp(statement, position, (Timestamp) value);
        } else if (value instanceof Date) {
            bindDate(statement, position, (Date) value);
        } else if (value instanceof BigDecimal) {
            bindBigDecimal(statement, position, (BigDecimal) value);
        } else if (value instanceof Short) {
            bindShort(statement, position, (Short) value);
        } else if (value instanceof Byte) {
            bindByte(statement, position, (Byte) value);
        } else if (value instanceof Array) {
            bindArray(statement, position, (Array) value);
        } else if (value instanceof Blob) {
            bindBlob(statement, position, (Blob) value);
        } else if (value instanceof NClob) {
            bindNClob(statement, position, (NClob) value);
        } else if (value instanceof Clob) {
            bindClob(statement, position, (Clob) value);
        } else if (value instanceof InputStream) {
            bindBinaryStream(statement, position, (InputStream) value);
        } else if (value instanceof SQLXML) {
            bindSQLXML(statement, position, (SQLXML) value);
        } else if (value instanceof Ref) {
            bindRef(statement, position, (Ref) value);
        } else if (value instanceof byte[]) {
            bindBytes(statement, position, (byte[]) value);
        } else {
            // Try this as a last resort, if we don't have an explicit way of
            // handling the type
            statement.setObject(position, value);
        }
    }

    /**
     * Binds the specified bytes to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the bytes should be bound
     * @param position the position in which the bytes should be bound
     * @param value the bytes which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindBytes(final PreparedStatement statement, final int position, final byte[] value) throws SQLException {
        if (value != null) {
            statement.setBytes(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified binary stream to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the binary stream should be bound
     * @param position the position in which the binary stream should be bound
     * @param value the binary stream which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindBinaryStream(final PreparedStatement statement, final int position, final InputStream value) throws SQLException {
        if (value != null) {
            statement.setBinaryStream(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified nclob to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the nclob should be bound
     * @param position the position in which the nclob should be bound
     * @param value the nclob which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindNClob(final PreparedStatement statement, final int position, final NClob value) throws SQLException {
        if (value != null) {
            statement.setNClob(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified clob to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the clob should be bound
     * @param position the position in which the clob should be bound
     * @param value the clob which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindClob(final PreparedStatement statement, final int position, final Clob value) throws SQLException {
        if (value != null) {
            statement.setClob(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified ref to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the ref should be bound
     * @param position the position in which the ref should be bound
     * @param value the ref which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindRef(final PreparedStatement statement, final int position, final Ref value) throws SQLException {
        if (value != null) {
            statement.setRef(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified SQLXML to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the SQLXML should be bound
     * @param position the position in which the SQLXML should be bound
     * @param value the SQLXML which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindSQLXML(final PreparedStatement statement, final int position, final SQLXML value) throws SQLException {
        if (value != null) {
            statement.setSQLXML(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified blob to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the blob should be bound
     * @param position the position in which the blob should be bound
     * @param value the blob which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindBlob(final PreparedStatement statement, final int position, final Blob value) throws SQLException {
        if (value != null) {
            statement.setBlob(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified array to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the array should be bound
     * @param position the position in which the array should be bound
     * @param value the array which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindArray(final PreparedStatement statement, final int position, final Array value) throws SQLException {
        if (value != null) {
            statement.setArray(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified timestamp to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the timestamp should be bound
     * @param position the position in which the timestamp should be bound
     * @param value the timestamp which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindTimestamp(final PreparedStatement statement, final int position, final Timestamp value) throws SQLException {
        if (value != null) {
            statement.setTimestamp(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified time to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the time should be bound
     * @param position the position in which the time should be bound
     * @param value the time which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindTime(final PreparedStatement statement, final int position, final Time value) throws SQLException {
        if (value != null) {
            statement.setTime(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified byte to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the byte should be bound
     * @param position the position in which the byte should be bound
     * @param value the byte which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindByte(final PreparedStatement statement, final int position, final Byte value) throws SQLException {
        if (value != null) {
            statement.setByte(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified short to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the short should be bound
     * @param position the position in which the short should be bound
     * @param value the short which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindShort(final PreparedStatement statement, final int position, final Short value) throws SQLException {
        if (value != null) {
            statement.setShort(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified string to the specified statement in the specified position. If a null string is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the string should be bound
     * @param position the position in which the string should be bound
     * @param value the string which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindString(final PreparedStatement statement, final int position, final String value) throws SQLException {
        if (value != null) {
            statement.setString(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified float to the specified statement in the specified position. If a null float is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the float should be bound
     * @param position the position in which the float should be bound
     * @param value the float which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindFloat(final PreparedStatement statement, final int position, final Float value) throws SQLException {
        if (value != null) {
            statement.setFloat(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified double to the specified statement in the specified position. If a null double is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the double should be bound
     * @param position the position in which the double should be bound
     * @param value the double which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindDouble(final PreparedStatement statement, final int position, final Double value) throws SQLException {
        if (value != null) {
            statement.setDouble(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified big decimal to the specified statement in the specified position. If a null big decimal is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the big decimal should be bound
     * @param position the position in which the big decimal should be bound
     * @param value the big decimal which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindBigDecimal(final PreparedStatement statement, final int position, final BigDecimal value) throws SQLException {
        if (value != null) {
            statement.setBigDecimal(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified integer to the specified statement in the specified position. If a null integer is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the integer should be bound
     * @param position the position in which the integer should be bound
     * @param value the integer which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindInteger(final PreparedStatement statement, final int position, final Integer value) throws SQLException {
        if (value != null) {
            statement.setInt(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified long to the specified statement in the specified position. If a null long is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the long should be bound
     * @param position the position in which the long should be bound
     * @param value the long which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindLong(final PreparedStatement statement, final int position, final Long value) throws SQLException {
        if (value != null) {
            statement.setLong(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified boolean to the specified statement in the specified position. If a null boolean is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the boolean should be bound
     * @param position the position in which the boolean should be bound
     * @param value the boolean which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindBoolean(final PreparedStatement statement, final int position, final Boolean value) throws SQLException {
        if (value != null) {
            statement.setBoolean(position, value);
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * Binds the specified date to the specified statement in the specified position. If a null date is specified,
     * then null will be bound to the statement instead
     * @param statement the statement to which the date should be bound
     * @param position the position in which the date should be bound
     * @param value the date which should be bound to the statement
     * @throws SQLException thrown when something exceptional happens
     */
    protected void bindDate(final PreparedStatement statement, final int position, final Date value) throws SQLException {
        if (value != null) {
            statement.setDate(position, new java.sql.Date(value.getTime()));
        } else {
            statement.setNull(position, Types.NULL);
        }
    }

    /**
     * This method attempts to locate the appropriate setter method within the specified object based
     * on the column name and data type which was returned by the database.
     * @param entity The entity in which we are looking for a setter method
     * @param columnName the column name of the data for which we are trying to find a setter method
     * @param columnTypeName the name of the data type which was returned by the database
     * @return An instance of @{link co.lariat.jdbc.SetterMethod()} or null if a setter cannot be found
     * @throws ClassNotFoundException thrown when something exceptional happens
     * @throws NoSuchMethodException thrown when something exceptional happens
     * @throws SQLException thrown when something exceptional happens
     */
    @SuppressWarnings("unchecked")
    protected SetterMethod findSetter(final Object entity, final String columnName, final String columnTypeName) throws ClassNotFoundException, NoSuchMethodException, SQLException {
        Class columnClass = Class.forName(columnTypeName);
        Class entityClass = entity.getClass();

        // Attempt to find a setter name for the property
        String setterName = "set"
            + new String(new char[]{columnName.charAt(0)}).toUpperCase()
            + columnName.substring(1);

        Method setterMethod;

        try {
            setterMethod = entityClass.getMethod(setterName, columnClass);
        } catch (NoSuchMethodException e) {
            // Attempt some type conversions, where possible
            if (columnTypeName.equals("java.lang.Integer")) {
                return findSetter(entity, columnName, "java.lang.Long");
            } else if (columnTypeName.equals("java.sql.Timestamp")) {
                return findSetter(entity, columnName, "java.util.Date");
            } else {
                throw e;
            }
        }

        Type[] parameterTypes = setterMethod.getParameterTypes();

        if (parameterTypes.length != 1) {
            throw new SQLException("Setter methods should only accept a single parameter");
        }

        Type setterArgumentType = parameterTypes[0];

        // TODO: This probably isn't very portable
        String setterArgumentTypeName = setterArgumentType.toString().replaceAll("class ", "");

        if (!setterArgumentTypeName.equals(columnTypeName)) {
            throw new SQLException("Setter argument type("
                + setterArgumentTypeName
                + ") does not match the column type name("
                + columnTypeName
                + ")"
            );
        }

        // Return the setter
        return new SetterMethod(setterMethod);
    }

    /**
     * Returns true if the specified class is annotated with javax.persistence.Entity
     * and false otherwise.
     * @param entityClass the class type which may or may not be a JPA entity
     * @return true if the specified class is annotated with javax.persistence.Entity
     * and false otherwise
     */
    protected boolean isJpaEntity(final Class entityClass) {
        // Assume that we're not dealing with a JPA entity if the entity annotation does not
        // exist on the classpath
        try {
            Class.forName("javax.persistence.Entity");
        } catch( ClassNotFoundException e ) {
            return false;
        }

        // Otherwise, check to see if the entity is annotated with it
        return entityClass.isAnnotationPresent(Entity.class);
    }

    /**
     * Returns true if the class of the specified instance is annotated with
     * javax.persistence.Entity and false otherwise.
     * @param entityClass the instance which may or may not be a JPA entity
     * @return true if the specified class is annotated with javax.persistence.Entity
     * and false otherwise
     */
    protected boolean isJpaEntity(final Object entityClass) {
        return isJpaEntity(entityClass.getClass());
    }

    /**
     * This method converts underscore delimited column names to camel case so that they can
     * be used to locate the appropriate setter method in the target entity. For example, the
     * column name my_column_name would become myColumnName.
     * @param columnName underscore separated column name
     * @return camel case equivalent of the underscore separated input value
     * @throws SQLException thrown then something exceptional happens
     */
    protected String toCamelCase(final String columnName) throws SQLException {
        String c = columnName.toLowerCase();

        // Leading underscores are not supported
        if (c.getBytes()[0] == '_') {
            throw new SQLException("Column names cannot begin with underscores");
        }

        // Return the unmodified column name, if the column name does not contain any underscores
        if (c.indexOf('_') == -1) {
            return c;
        }

        StringTokenizer st = new StringTokenizer(c.toLowerCase(), "_");
        StringBuilder result = new StringBuilder();
        boolean first = true;

        // Otherwise, iterate through the tokens doing our thing
        while (st.hasMoreTokens()) {
            String tmp = st.nextToken();

            if (first) {
                // Don't capitalize the first token
                result.append(tmp.toLowerCase());
                first = false;
            } else {
                result.append(new String(new char[]{tmp.charAt(0)}).toUpperCase())
                    .append(tmp.substring(1));
            }
        }

        // Return the camel case property name
        return result.toString();
    }

    /**
     * {@inheritDoc}
     */
    public void commit() throws SQLException {
        this.connection.commit();
    }

    /**
     * {@inheritDoc}
     */
    public void rollback() throws SQLException {
        this.connection.rollback();
    }

    /**
     * {@inheritDoc}
     */
    public void close() throws SQLException {
        this.connection.close();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isClosed() throws SQLException {
        return this.connection.isClosed();
    }

    /**
     * {@inheritDoc}
     */
    public DatabaseMetaData getMetaData() throws SQLException {
        return this.connection.getMetaData();
    }

    /**
     * {@inheritDoc}
     */
    public void setReadOnly(boolean readOnly) throws SQLException {
        this.connection.setReadOnly(readOnly);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isReadOnly() throws SQLException {
        return this.connection.isReadOnly();
    }

    /**
     * {@inheritDoc}
     */
    public void setCatalog(String catalog) throws SQLException {
        this.connection.setCatalog(catalog);
    }

    /**
     * {@inheritDoc}
     */
    public String getCatalog() throws SQLException {
        return this.connection.getCatalog();
    }

    /**
     * {@inheritDoc}
     */
    public void setTransactionIsolation(int level) throws SQLException {
        this.connection.setTransactionIsolation(level);
    }

    /**
     * {@inheritDoc}
     */
    public int getTransactionIsolation() throws SQLException {
        return this.connection.getTransactionIsolation();
    }

    /**
     * {@inheritDoc}
     */
    public SQLWarning getWarnings() throws SQLException {
        return this.connection.getWarnings();
    }

    /**
     * {@inheritDoc}
     */
    public void clearWarnings() throws SQLException {
        this.connection.clearWarnings();
    }

    /**
     * {@inheritDoc}
     */
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        return this.connection.createStatement();
    }

    /**
     * {@inheritDoc}
     */
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return this.connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    /**
     * {@inheritDoc}
     */
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        return this.connection.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return this.connection.getTypeMap();
    }

    /**
     * {@inheritDoc}
     */
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        this.connection.setTypeMap(map);
    }

    /**
     * {@inheritDoc}
     */
    public void setHoldability(int holdability) throws SQLException {
        this.connection.setHoldability(holdability);
    }

    /**
     * {@inheritDoc}
     */
    public int getHoldability() throws SQLException {
        return this.connection.getHoldability();
    }

    /**
     * {@inheritDoc}
     */
    public Savepoint setSavepoint() throws SQLException {
        return this.connection.setSavepoint();
    }

    /**
     * {@inheritDoc}
     */
    public Savepoint setSavepoint(String name) throws SQLException {
        return this.connection.setSavepoint(name);
    }

    /**
     * {@inheritDoc}
     */
    public void rollback(final Savepoint savepoint) throws SQLException {
        this.connection.rollback(savepoint);
    }

    /**
     * {@inheritDoc}
     */
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        this.connection.releaseSavepoint(savepoint);
    }

    /**
     * {@inheritDoc}
     */
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return this.connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    /**
     * {@inheritDoc}
     */
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return this.connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    /**
     * {@inheritDoc}
     */
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        return this.connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    /**
     * {@inheritDoc}
     */
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        return this.connection.prepareStatement(sql, autoGeneratedKeys);
    }

    /**
     * {@inheritDoc}
     */
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        return this.connection.prepareStatement(sql, columnIndexes);
    }

    /**
     * {@inheritDoc}
     */
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        return this.connection.prepareStatement(sql, columnNames);
    }

    /**
     * {@inheritDoc}
     */
    public Clob createClob() throws SQLException {
        return this.connection.createClob();
    }

    /**
     * {@inheritDoc}
     */
    public Blob createBlob() throws SQLException {
        return this.connection.createBlob();
    }

    /**
     * {@inheritDoc}
     */
    public NClob createNClob() throws SQLException {
        return this.connection.createNClob();
    }

    /**
     * {@inheritDoc}
     */
    public SQLXML createSQLXML() throws SQLException {
        return this.connection.createSQLXML();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isValid(int timeout) throws SQLException {
        return this.connection.isValid(timeout);
    }

    /**
     * {@inheritDoc}
     */
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        this.connection.setClientInfo(name, value);
    }

    /**
     * {@inheritDoc}
     */
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
       this.connection.setClientInfo(properties);
    }

    /**
     * {@inheritDoc}
     */
    public String getClientInfo(String name) throws SQLException {
        return this.connection.getClientInfo(name);
    }

    /**
     * {@inheritDoc}
     */
    public Properties getClientInfo() throws SQLException {
        return this.connection.getClientInfo();
    }

    /**
     * {@inheritDoc}
     */
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        return this.connection.createArrayOf(typeName, elements);
    }

    /**
     * {@inheritDoc}
     */
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        return this.connection.createStruct(typeName, attributes);
    }

    /**
     * {@inheritDoc}
     */
    public void setSchema(String schema) throws SQLException {
        this.connection.setSchema(schema);
    }

    /**
     * {@inheritDoc}
     */
    public String getSchema() throws SQLException {
        return this.connection.getSchema();
    }

    /**
     * {@inheritDoc}
     */
    public void abort(Executor executor) throws SQLException {
        this.connection.abort(executor);
    }

    /**
     * {@inheritDoc}
     */
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        this.connection.setNetworkTimeout(executor, milliseconds);
    }

    /**
     * {@inheritDoc}
     */
    public int getNetworkTimeout() throws SQLException {
        return this.connection.getNetworkTimeout();
    }

    /**
     * {@inheritDoc}
     */
    public boolean getAutoCommit() throws SQLException {
        return this.connection.getAutoCommit();
    }

    /**
     * {@inheritDoc}
     */
    public Statement createStatement() throws SQLException {
        return this.connection.createStatement();
    }

    /**
     * {@inheritDoc}
     */
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return this.connection.prepareStatement(sql);
    }

    /**
     * {@inheritDoc}
     */
    public CallableStatement prepareCall(String sql) throws SQLException {
        return this.connection.prepareCall(sql);
    }

    /**
     * {@inheritDoc}
     */
    public String nativeSQL(String sql) throws SQLException {
        return this.connection.nativeSQL(sql);
    }

    /**
     * {@inheritDoc}
     */
    public void  setAutoCommit(final boolean autoCommit) throws SQLException {
        this.connection.setAutoCommit(autoCommit);
    }

    /**
     * {@inheritDoc}
     */
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return this.connection.unwrap(iface);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return this.connection.isWrapperFor(iface);
    }
}
