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

import co.lariat.jdbc.entity.User;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:john@lariat.co">John D. Dunlap</a>
 * @since 7/26/16 2:56 PM - Created with IntelliJ IDEA.
 */
public class SimpleConnectionTest {
    private static SimpleConnection simpleConnection;

    @BeforeClass
    public static void beforeClass() throws SQLException {
        simpleConnection = DB.getConnection("jdbc:hsqldb:mem:test", "sa", "");

        // Create a table
        simpleConnection.execute("create table users(\n" +
            "            id INTEGER not null,\n" +
            "            username char(25),\n" +
            "            password char(25),\n" +
            "            active BOOLEAN,\n" +
            "            last_active TIMESTAMP,\n" +
            "            balance NUMERIC(10, 2),\n" +
            "            PRIMARY KEY (id)\n" +
            "        );"
        );

        // Add some data
        simpleConnection.execute(
            "insert into users(id, username, password, active, last_active, balance) values(?,?,?,?,?,?)",
            1,
            "admin",
            "password",
            true,
            "1970-01-01 00:00:00",
            1345.23
        );
        simpleConnection.execute(
            "insert into users(id, username, password, active, last_active, balance) values(?,?,?,?,?,?)",
            2,
            "bob.wiley",
            "password2",
            true,
            "1973-02-02 00:00:00",
            564.77
        );
    }

    @Test
    public void testExecuteMethod() throws SQLException {
        String oldPassword = "password";
        String newPassword = "new34password";

        // Change the password
        simpleConnection.execute("update users set password = ? where username = ?", newPassword, "admin");
        assertEquals(newPassword, simpleConnection.fetchString("select password from users where username = ?", "admin"));

        // Change the password back
        simpleConnection.execute("update users set password = ? where username = ?", oldPassword, "admin");
        assertEquals(oldPassword, simpleConnection.fetchString("select password from users where username = ?", "admin"));
    }

    @Test
    public void testFetchStringMethod() throws SQLException {
        String username = simpleConnection.fetchString("select username from users where id = ?", 1);
        assertEquals(username, "admin");
    }

    @Test
    public void testFetchObjectMethod() throws SQLException {
        Object object = simpleConnection.fetchObject("select username from users where id = ?", 1);
        assertEquals(String.class, object.getClass());

        object = simpleConnection.fetchObject("select id from users where username = ?", "admin");
        assertEquals(Integer.class, object.getClass());
    }

    @Test
    public void testFetchIntegerMethod() throws SQLException {
        Integer id = simpleConnection.fetchInt("select id from users where username = ?", "admin");
        assertEquals(id, new Integer(1));
    }

    @Test
    public void testFetchLongMethod() throws SQLException {
        Long id = simpleConnection.fetchLong("select id from users where username = ?", "admin");
        assertEquals(id, new Long(1));
    }

    @Test
    public void testFetchBooleanMethod() throws SQLException {
        Boolean active = simpleConnection.fetchBoolean("select active from users where username = ?", "admin");
        assertEquals(active, true);
    }

    @Test
    public void testFetchFloatMethod() throws SQLException {
        Float balance = simpleConnection.fetchFloat("select balance from users where username = ?", "admin");
        assertEquals(balance, new Float(1345.23));
    }

    @Test
    public void testFetchDoubleMethod() throws SQLException {
        Double balance = simpleConnection.fetchDouble("select balance from users where username = ?", "admin");
        assertEquals(balance, new Double(1345.23));
    }

    @Test
    public void testFetchBigDecimalMethod() throws SQLException {
        BigDecimal balance = simpleConnection.fetchBigDecimal("select balance from users where username = ?", "admin");
        assertEquals(new Double(balance.doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));
    }

    @Test
    public void testFetchDateMethod() throws ParseException, SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date lastActive = simpleConnection.fetchDate("select last_active from users where username = ?", "admin");
        assertEquals(lastActive, formatter.parse("1970-01-01 00:00:00"));
    }

    @Test
    public void testFetchEntityMethod() throws ParseException, SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        User user = simpleConnection.fetchEntity(
            User.class,
            "select id, username, password, active, last_active, balance from users where id = ?",
            1
        );

        assertEquals(user.getId(), new Long(1));
        assertEquals(user.getUsername(), "admin");
        assertEquals(user.getPassword(), "password");
        assertEquals(user.getActive(), true);
        assertEquals(user.getLastActive(), formatter.parse("1970-01-01 00:00:00"));
        assertEquals(new Double(user.getBalance().doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));

        // Now attempt to override a value in the entity
        simpleConnection.fetchEntity(
            user,
            "select password from users where id = ?",
            2
        );

        // Verify that the password attribute has been overridden and that all other attributes have
        // remained the same
        assertEquals(user.getId(), new Long(1));
        assertEquals(user.getUsername(), "admin");
        assertEquals(user.getPassword(), "password2");
        assertEquals(user.getActive(), true);
        assertEquals(user.getLastActive(), formatter.parse("1970-01-01 00:00:00"));
        assertEquals(new Double(user.getBalance().doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));
    }

    @Test
    public void testFetchMapMethod() throws ParseException, SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Map<String, Object> user = simpleConnection.fetchMap(
            "select id, username, password, active, last_active, balance from users where id = ?",
            1
        );

        assertEquals(new Long((Integer) user.get("id")), new Long(1));
        assertEquals((String) user.get("username"), "admin");
        assertEquals((String) user.get("password"), "password");
        assertEquals((Boolean) user.get("active"), true);
        assertEquals(new Date(((java.sql.Timestamp) user.get("last_active")).getTime()), formatter.parse("1970-01-01 00:00:00"));
        assertEquals(new Double(((BigDecimal) user.get("balance")).doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));
    }

    @Test
    public void testFetchAllMapMethod() throws ParseException, SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<Map<String, Object>> users = simpleConnection.fetchAllMap(
            "select id, username, password, active, last_active, balance from users order by id asc"
        );

        assertEquals(new Integer(2), (Integer) users.size());

        Map<String, Object> user = users.get(0);
        assertEquals(new Long((Integer) user.get("id")), new Long(1));
        assertEquals((String) user.get("username"), "admin");
        assertEquals((String) user.get("password"), "password");
        assertEquals((Boolean) user.get("active"), true);
        assertEquals(new Date(((java.sql.Timestamp) user.get("last_active")).getTime()), formatter.parse("1970-01-01 00:00:00"));
        assertEquals(new Double(((BigDecimal) user.get("balance")).doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));

        user = users.get(1);
        assertEquals(new Long((Integer) user.get("id")), new Long(2));
        assertEquals((String) user.get("username"), "bob.wiley");
        assertEquals((String) user.get("password"), "password2");
        assertEquals((Boolean) user.get("active"), true);
        assertEquals(new Date(((java.sql.Timestamp) user.get("last_active")).getTime()), formatter.parse("1973-02-02 00:00:00"));
        assertEquals(new Double(((BigDecimal) user.get("balance")).doubleValue()), new Double(new BigDecimal(564.77).doubleValue()));
    }

    @Test
    public void testFetchAllEntityMethod() throws ParseException, SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<User> users = simpleConnection.fetchAllEntity(
            User.class,
            "select id, username, password, active, last_active, balance from users order by id asc"
        );

        assertEquals(new Integer(2), (Integer) users.size());

        User user = users.get(0);
        assertEquals(user.getId(), new Long(1));
        assertEquals(user.getUsername(), "admin");
        assertEquals(user.getPassword(), "password");
        assertEquals(user.getActive(), true);
        assertEquals(user.getLastActive(), formatter.parse("1970-01-01 00:00:00"));
        assertEquals(new Double(user.getBalance().doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));

        user = users.get(1);
        assertEquals(user.getId(), new Long(2));
        assertEquals(user.getUsername(), "bob.wiley");
        assertEquals(user.getPassword(), "password2");
        assertEquals(user.getActive(), true);
        assertEquals(user.getLastActive(), formatter.parse("1973-02-02 00:00:00"));
        assertEquals(new Double(user.getBalance().doubleValue()), new Double(new BigDecimal(564.77).doubleValue()));
    }

    @Test
    public void testFetchAllEntityMap() throws ParseException, SQLException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Map<String, User> users = simpleConnection.fetchAllEntityMap(
            User.class,
            "username",
            "select id, username, password, active, last_active, balance from users order by id asc"
        );

        assertEquals(new Integer(2), (Integer) users.size());

        User user = users.get("admin");
        assertEquals(user.getId(), new Long(1));
        assertEquals(user.getUsername(), "admin");
        assertEquals(user.getPassword(), "password");
        assertEquals(user.getActive(), true);
        assertEquals(user.getLastActive(), formatter.parse("1970-01-01 00:00:00"));
        assertEquals(new Double(user.getBalance().doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));

        user = users.get("bob.wiley");
        assertEquals(user.getId(), new Long(2));
        assertEquals(user.getUsername(), "bob.wiley");
        assertEquals(user.getPassword(), "password2");
        assertEquals(user.getActive(), true);
        assertEquals(user.getLastActive(), formatter.parse("1973-02-02 00:00:00"));
        assertEquals(new Double(user.getBalance().doubleValue()), new Double(new BigDecimal(564.77).doubleValue()));
    }

    @Test
    public void testToCamelCaseMethod() throws SQLException {
        assertEquals("myColumnName", simpleConnection.toCamelCase("my_column_name"));
        assertEquals("thisIsATest", simpleConnection.toCamelCase("this_is_a_test"));
        assertEquals("test", simpleConnection.toCamelCase("test"));
        assertEquals("test", simpleConnection.toCamelCase("tEst"));

        // Some databases are case insensitive so we can't rely on the case
        // of the column name
        assertEquals("myColumnName", simpleConnection.toCamelCase("MY_COLUMN_NAME"));
        assertEquals("myColumnName", simpleConnection.toCamelCase("My_CoLuMn_NaMe"));
        assertEquals("thisIsATest", simpleConnection.toCamelCase("THIS_IS_A_TEST"));
    }

    @Test
    public void testIsJpaEntityMethod() {
        assertTrue(simpleConnection.isJpaEntity(User.class));
        assertFalse(simpleConnection.isJpaEntity(getClass()));

        User user = new User();
        assertTrue(simpleConnection.isJpaEntity(user));
        assertFalse(simpleConnection.isJpaEntity(this));
    }
}
