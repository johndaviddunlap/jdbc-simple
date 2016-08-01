# jdbc-simple

I've always liked how JPA can bind query results to Java objects but dislike many other things about it. For example, I dislike that it wraps generated classes around my objects, I dislike that it adds a lot of dependencies to my project, I dislike that it is complicated, and I dislike that it is difficult to use with existing schemas. For many projects, JPA is overkill for what you're trying to do while the low level JDBC API is too painful to be useful. Further, neither option can be implemented quickly when you're developing a project within strict time constraints. Strangely, there seems to be very few libraries available which fall in between those two extremes and which also don't require significant setup time and or a steep learning curve. If you've ever found yourself in a similar situation or thinking similar things, then you might like the minimalist approach of jdbc-simple.

Generally speaking, the vast majority of SQL database interactions fall into one of the following categories:
* Query returns nothing
* Query returns a single row with a single column
* Query returns a single row with multiple columns
* Query returns multiple rows with one or more columns

These are the core use cases which are handled by jdbc-simple.

# Features
* Small and very lightweight
* No transitive dependencies will be added to your project
* Query methods which eliminate JDBC boiler plate without getting in your way
* Query results are injected into java objects
* Compatible with existing JDBC applications

# Documentation
* [Javadoc](https://johndunlap.github.io/jdbc-simple/)

# Examples
For more working examples, look at the unit tests [here](https://github.com/johndunlap/jdbc-simple/tree/master/src/test/java/co/lariat/jdbc/test).

_You will need to have the JDBC HSQLDB driver(only necessary for demonstration purposes) on your classpath for the following examples to run. You can add this driver to your project by adding the following XML snippet to your Maven POM:_
```xml
<dependency>
    <groupId>hsqldb</groupId>
    <artifactId>hsqldb</artifactId>
    <version>1.8.0.10</version>
</dependency>
```
_Dependency coordinates for other build systems can be found [here](http://search.maven.org/#artifactdetails%7Chsqldb%7Chsqldb%7C1.8.0.10%7Cjar)_

```java
import co.lariat.jdbc.DB;
import co.lariat.jdbc.SimpleConnection;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class Main {
    public static void main(String[] args) throws SQLException {
        SimpleConnection connection = DB.getConnection("jdbc:hsqldb:mem:test", "sa", "");
        connection.execute("create table users(\n" +
            "    id INTEGER not null,\n" +
            "    username char(25),\n" +
            "    password char(25),\n" +
            "    active BOOLEAN,\n" +
            "    last_active TIMESTAMP,\n" +
            "    PRIMARY KEY (id)\n" +
            ");"
        );

        // Add some data
        connection.execute(
            "insert into users(id, username, password, active, last_active) values(?,?,?,?,?)",
            1,
            "admin",
            "password",
            true,
            "1970-01-01 00:00:00"
        );
        connection.execute(
            "insert into users(id, username, password, active, last_active) values(?,?,?,?,?)",
            2,
            "bob.wiley",
            "password2",
            true,
            "1973-02-02 00:00:00"
        );

        String username = "bob.wiley";

        System.out.println("==== FETCH A SINGLE ROW WITH A SINGLE COLUMN ====");

        Long userId = connection.fetchLong(
            "select id from users where username = ?",
            username
        );

        System.out.println("User id for username " + username + " is " + userId);

        System.out.println("\n==== FETCH A SINGLE ROW WITH MULTIPLE COLUMNS ====");

        User user = connection.fetchEntity(
            User.class,
            "select * from users where id = ?",
            2
        );

        System.out.println("User before update: " + user);

        // ==== RUN A QUERY WHICH RETURNS NOTHING ====

        connection.execute(
            "update users set password = ? where id = ?",
            "password3",
            2
        );

        System.out.println("\n==== FETCH A SINGLE ROW INTO AN EXISTING ENTITY ====");

        connection.fetchEntity(
            user,
            "select password from users where id = ?",
            2
        );

        System.out.println("User after update: " + user);

        System.out.println("\n==== FETCH MULTIPLE ROWS WITH MULTIPLE COLUMNS ====");

        List<User> users = connection.fetchAllEntity(
            User.class,
            "select id, username, password, active, last_active from users"
        );

        for (User u : users) {
            System.out.println(u);
        }
    }

    public static class User {
        private Long id;
        private String username;
        private String password;
        private Boolean active;
        private Date lastActive;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public Date getLastActive() {
            return lastActive;
        }

        public void setLastActive(Date lastActive) {
            this.lastActive = lastActive;
        }

        @Override
        public String toString() {
            return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", active=" + active +
                ", lastActive=" + lastActive +
                '}';
        }
    }
}
```
