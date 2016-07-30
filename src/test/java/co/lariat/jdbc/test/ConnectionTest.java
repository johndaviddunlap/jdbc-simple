package co.lariat.jdbc.test;

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

import co.lariat.jdbc.Connection;
import co.lariat.jdbc.DB;
import co.lariat.jdbc.test.entity.User;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:john@lariat.co">John D. Dunlap</a>
 * @since 7/26/16 2:56 PM - Created with IntelliJ IDEA.
 */
public class ConnectionTest {
    private static Connection connection;

    @BeforeClass
    public static void beforeClass() {
        connection = DB.getConnection("jdbc:hsqldb:mem:test", "sa", "");

        // Create a table
        connection.execute("create table users(\n" +
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
        connection.execute(
            "insert into users(id, username, password, active, last_active, balance) values(?,?,?,?,?,?)",
            1,
            "admin",
            "password",
            true,
            "1970-01-01 00:00:00",
            1345.23
        );
        connection.execute(
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
    public void testExecuteMethod() {
        String oldPassword = "password";
        String newPassword = "new34password";

        // Change the password
        connection.execute("update users set password = ? where username = ?", newPassword, "admin");
        assertEquals(newPassword, connection.fetchString("select password from users where username = ?", "admin"));

        // Change the password back
        connection.execute("update users set password = ? where username = ?", oldPassword, "admin");
        assertEquals(oldPassword, connection.fetchString("select password from users where username = ?", "admin"));
    }

    @Test
    public void testFetchStringMethod() {
        String username = connection.fetchString("select username from users where id = ?", 1);
        assertEquals(username, "admin");
    }

    @Test
    public void testFetchIntegerMethod() {
        Integer id = connection.fetchInteger("select id from users where username = ?", "admin");
        assertEquals(id, new Integer(1));
    }

    @Test
    public void testFetchLongMethod() {
        Long id = connection.fetchLong("select id from users where username = ?", "admin");
        assertEquals(id, new Long(1));
    }

    @Test
    public void testFetchBooleanMethod() {
        Boolean active = connection.fetchBoolean("select active from users where username = ?", "admin");
        assertEquals(active, true);
    }

    @Test
    public void testFetchFloatMethod() {
        Float balance = connection.fetchFloat("select balance from users where username = ?", "admin");
        assertEquals(balance, new Float(1345.23));
    }

    @Test
    public void testFetchDoubleMethod() {
        Double balance = connection.fetchDouble("select balance from users where username = ?", "admin");
        assertEquals(balance, new Double(1345.23));
    }

    @Test
    public void testFetchBigDecimalMethod() {
        BigDecimal balance = connection.fetchBigDecimal("select balance from users where username = ?", "admin");
        assertEquals(new Double(balance.doubleValue()), new Double(new BigDecimal(1345.23).doubleValue()));
    }

    @Test
    public void testFetchDateMethod() throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date lastActive = connection.fetchDate("select last_active from users where username = ?", "admin");
        assertEquals(lastActive, formatter.parse("1970-01-01 00:00:00"));
    }

    @Test
    public void testFetchEntityMethod() throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        User user = connection.fetchEntity(
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
        connection.fetchEntity(
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
    public void testFetchMapMethod() throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Map<String, Object> user = connection.fetchMap(
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
    public void testFetchAllMapMethod() throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<Map<String, Object>> users = connection.fetchAllMap("select id, username, password, active, last_active, balance from users order by id asc");

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
    public void testFetchAllEntityMethod() throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<User> users = connection.fetchAllEntity(
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
    public void testFetchAllEntityMap() throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Map<String, User> users = connection.fetchAllEntityMap(
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
}
