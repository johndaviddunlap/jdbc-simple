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

import co.lariat.jdbc.exception.UnsupportedColumnNameException;

import javax.persistence.Entity;
import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author <a href="mailto:john@lariat.co">John D. Dunlap</a>
 * @since 9/14/15 3:26 PM - Created with IntelliJ IDEA.
 */
public abstract class Connection {
    private DataSource dataSource;
    private java.sql.Connection connection;
    private Map<String, PreparedStatement> preparedStatementCache = new HashMap<String, PreparedStatement>();
    private Map<Class<?>, Map<String, SetterMethod>> setterCache = new HashMap<Class<?>, Map<String, SetterMethod>>();

    public Connection(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Connection(final java.sql.Connection connection) {
        this.connection = connection;
    }

    protected java.sql.Connection getConnection() {
        try {
            if (dataSource != null) {
                return dataSource.getConnection();
            } else {
                return connection;
            }
        }

        // Re-throw as a runtime exception
        catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected PreparedStatement getPreparedStatement(final String sql) {
        try {
            // Return the cached prepared statement, if available
            if (preparedStatementCache.containsKey(sql)) {
                return preparedStatementCache.get(sql);
            }

            // Create a prepared statement
            preparedStatementCache.put(sql, getConnection().prepareStatement(sql));

            // Return the prepared statement
            return preparedStatementCache.get(sql);
        }

        // Re-throw as a runtime exception
        catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected abstract ResultSet fetch(final PreparedStatement statement);

    public ResultSet fetch(final String sql, final Object... arguments) {
        // Attempt to construct a prepared statement
        PreparedStatement statement = getPreparedStatement(sql);

        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        return fetch(statement);
    }

    public boolean execute(final PreparedStatement statement) {
        try {
            return statement.execute();
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public boolean execute(final String sql, final Object... arguments) {
        // Attempt to construct a prepared statement
        PreparedStatement statement = getPreparedStatement(sql);

        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        return execute(statement);
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> fetchAllEntity(final Class<T> clazz, final String sql, final Object... arguments) {
        // Attempt to construct a prepared statement
        PreparedStatement statement = getPreparedStatement(sql);

        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        ResultSet resultSet = fetch(statement);

        List<T> entities = new ArrayList<T>();

        // Iterate over the results
        for (Record record : resultSet) {
            entities.add(fetchEntity(clazz, record));
        }

        // Not technically type safe but necessary to create the illusion of it
        return entities;
    }

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> fetchAllEntityMap(final Class<T> clazz, final String columnLabel, final String sql, final Object... arguments) {
        try {
            // Attempt to construct a prepared statement
            PreparedStatement statement = getPreparedStatement(sql);

            // Attempt to bind the arguments to the query
            bindArguments(statement, arguments);

            // Run the query
            ResultSet resultSet = fetch(statement);

            Map<String, T> entities = new HashMap<String, T>();

            // Iterate over the results
            for (Record record : resultSet) {
                entities.put(record.getStringByName(columnLabel), fetchEntity(clazz, record));
            }

            // Not technically type safe but necessary to create the illusion of it
            return entities;
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }

    }

    protected <T> T fetchEntity(final Class<T> clazz, final Record record) {
        try {
            T entity = clazz.newInstance();
            return fetchEntity(entity, record);
        } catch (InstantiationException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        } catch (IllegalAccessException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected <T> T fetchEntity(final T entity, final Record record) {
        try {
            int columnCount = record.getColumnCount();

            for (int index = 1; index <= columnCount; index++) {
                String columnName = toCamelCase(record.getColumnName(index));
                String columnClassName = record.getColumnClassName(index);
                Object value = record.getValue(index);

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

            return (T) entity;
        } catch (ClassNotFoundException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        } catch (NoSuchMethodException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        } catch (IllegalAccessException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        } catch (InvocationTargetException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchAllMap(final String sql, final Object ... arguments) {
        // Attempt to construct a prepared statement
        PreparedStatement statement = getPreparedStatement(sql);

        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        ResultSet resultSet = fetch(statement);

        List<Map<String, Object>> entities = new ArrayList<Map<String, Object>>();

        // Iterate over the results
        for (Record record : resultSet) {
            entities.add(fetchMap(record));
        }

        // Not technically type safe but necessary to create the illusion of it
        return entities;
    }

    public <T> T fetchEntity(final T entity, final String sql, final Object ... arguments) {
        // Attempt to construct a prepared statement
        PreparedStatement statement = getPreparedStatement(sql);

        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        ResultSet resultSet = fetch(statement);

        int count = 0;

        // Iterate over the results
        for (Record record : resultSet) {
            if (count > 0) {
                throw new co.lariat.jdbc.exception.SQLException("Encountered a second record where a single record was expected");
            }

            // Populate the entity
            fetchEntity(entity, record);
            count++;
        }

        // Not technically type safe but necessary to create the illusion of it
        return entity;
    }

    public <T> T fetchEntity(final Class<T> clazz, final String sql, final Object ... arguments) {
        try {
            T entity = clazz.newInstance();
            return fetchEntity(entity, sql, arguments);
        } catch (InstantiationException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        } catch (IllegalAccessException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchMap(final String sql, final Object ... arguments) {
        // Attempt to construct a prepared statement
        PreparedStatement statement = getPreparedStatement(sql);

        // Attempt to bind the arguments to the query
        bindArguments(statement, arguments);

        // Run the query
        ResultSet resultSet = fetch(statement);

        int count = 0;

        Map<String, Object> entity = null;

        // Iterate over the results
        for (Record record : resultSet) {
            if (count > 0) {
                throw new co.lariat.jdbc.exception.SQLException("Encountered a second record where a single record was expected");
            }

            entity = fetchMap(record);
            count++;
        }

        // Not technically type safe but necessary to create the illusion of it
        return entity;
    }

    protected Map<String, Object> fetchMap(final Record record) {
        try {
            Map<String, Object> entity = new HashMap<String, Object>();

            int columnCount = record.getColumnCount();

            for (int index = 1; index <= columnCount; index++) {
                String columnName = record.getColumnName(index).toLowerCase();
                Object value = record.getValue(index);
                entity.put(columnName, value);
            }

            return entity;
        } catch(SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected java.sql.ResultSet fetchField(final String sql, final Object ... args) {
        try {
            // Construct a prepared statement
            PreparedStatement statement = getConnection().prepareStatement(sql);

            // Bind the arguments to the query
            bindArguments(statement, args);

            // Run the query
            java.sql.ResultSet resultSet = statement.executeQuery();
            resultSet.next();

            // Return the first column of the first row
            return resultSet;
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public String fetchString(final String sql, final Object ... args) {
        try {
            return fetchField(sql, args).getString(1);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public Integer fetchInteger(final String sql, final Object ... args) {
        try {
            return fetchField(sql, args).getInt(1);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public Long fetchLong(final String sql, final Object ... args) {
        try {
            return fetchField(sql, args).getLong(1);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public Float fetchFloat(final String sql, final Object ... args) {
        try {
            return fetchField(sql, args).getFloat(1);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public Double fetchDouble(final String sql, final Object ... args) {
        try {
            return fetchField(sql, args).getDouble(1);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public BigDecimal fetchBigDecimal(final String sql, final Object ... args) {
        try {
            return fetchField(sql, args).getBigDecimal(1);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public Date fetchDate(final String sql, final Object ... args) {
        try {
            return new Date(fetchField(sql, args).getTimestamp(1).getTime());
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    public Boolean fetchBoolean(final String sql, final Object ... args) {
        try {
            return fetchField(sql, args).getBoolean(1);
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindArguments(final PreparedStatement statement, final Object ... args) {
        int index = 1;

        // Iterate through the arguments
        for (Object argument : args) {
            // Attempt to bind the argument to the query
            bindArgument(statement, argument, index);
            index++;
        }
    }

    protected void bindArgument(final PreparedStatement statement, final Object argument, final int index) {
        try {
            if (argument == null) {
                statement.setNull(index, Types.NULL);
            } else if (argument instanceof String) {
                bindString(statement, index, (String) argument);
            } else if (argument instanceof Integer) {
                bindInteger(statement, index, (Integer) argument);
            } else if (argument instanceof Long) {
                bindLong(statement, index, (Long) argument);
            } else if (argument instanceof Float) {
                bindFloat(statement, index, (Float) argument);
            } else if (argument instanceof Double) {
                bindDouble(statement, index, (Double) argument);
            } else if (argument instanceof Boolean) {
                bindBoolean(statement, index, (Boolean) argument);
            } else if (argument instanceof Date) {
                bindDate(statement, index, (Date) argument);
            } else if (argument instanceof BigDecimal) {
                bindBigDecimal(statement, index, (BigDecimal) argument);
            } else {
                throw new IllegalArgumentException("Query arguments of type " + argument.getClass().toString() + " are not supported");
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindString(final PreparedStatement statement, final int position, final String value) {
        try {
            if (value != null) {
                statement.setString(position, value);
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindFloat(final PreparedStatement statement, final int position, final Float value) {
        try {
            if (value != null) {
                statement.setFloat(position, value);
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindDouble(final PreparedStatement statement, final int position, final Double value) {
        try {
            if (value != null) {
                statement.setDouble(position, value);
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindBigDecimal(final PreparedStatement statement, final int position, final BigDecimal value) {
        try {
            if (value != null) {
                statement.setBigDecimal(position, value);
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindInteger(final PreparedStatement statement, final int position, final Integer value) {
        try {
            if (value != null) {
                statement.setInt(position, value);
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindLong(final PreparedStatement statement, final int position, final Long value) {
        try {
            if (value != null) {
                statement.setLong(position, value);
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindBoolean(final PreparedStatement statement, final int position, final Boolean value) {
        try {
            if (value != null) {
                statement.setBoolean(position, value);
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    protected void bindDate(final PreparedStatement statement, final int position, final Date value) {
        try {
            if (value != null) {
                statement.setDate(position, new java.sql.Date(value.getTime()));
            } else {
                statement.setNull(position, Types.NULL);
            }
        } catch (SQLException e) {
            throw new co.lariat.jdbc.exception.SQLException(e);
        }
    }

    @SuppressWarnings("unchecked")
    protected SetterMethod findSetter(final Object entity, final String columnName, final String columnTypeName) throws ClassNotFoundException, NoSuchMethodException {
        Class columnClass = Class.forName(columnTypeName);
        Class entityClass = entity.getClass();

        // Check the cache before scanning the entity
        if (setterCache.containsKey(entityClass.getClass())) {
            if (setterCache.get(entityClass.getClass()).containsKey(columnName)) {
                // Return the cached setter, if one was found
                return setterCache.get(entityClass.getClass()).get(columnName);
            }
        } else {
            setterCache.put(entityClass.getClass(), new HashMap<String, SetterMethod>());
        }

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

        Type[] parameterTypes = setterMethod.getGenericParameterTypes();

        if (parameterTypes.length != 1) {
            throw new NoSuchMethodError("Setter methods should only accept a single parameter");
        }

        Type setterArgumentType = parameterTypes[0];

        if (!setterArgumentType.getTypeName().equals(columnTypeName)) {
            throw new NoSuchMethodException("Setter argument type does not match the column type");
        }

        SetterMethod foundSetter = new SetterMethod(setterMethod);

        // Cache the setter
        setterCache.get(entityClass.getClass()).put(columnName, foundSetter);

        // Return the setter
        return foundSetter;
    }

    protected boolean isJpaEntity(final Class entityClass) {
        // Assume that we're not dealing with a JPA entity if the entity annotation does not
        // exist on the classpath
        try {
            Class.forName("javax.persistence.Entity");
        } catch( ClassNotFoundException e ) {
            return false;
        }

        // Otherwise, check to see if the entity is annotated with it
        return entityClass.getDeclaredAnnotation(Entity.class) != null;
    }

    protected String toCamelCase(final String c) {
        String columnName = c.toLowerCase();

        // Leading underscores are not supported
        if (columnName.getBytes()[0] == '_') {
            throw new UnsupportedColumnNameException("Column names may not begin with underscores");
        }

        // Return the unmodified column name, if the column name does not contain any underscores
        if (columnName.indexOf('_') == -1) {
            return columnName;
        }

        StringTokenizer st = new StringTokenizer(columnName.toLowerCase(), "_");
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
}
