# jdbc-simple

I've always liked how JPA can bind query results to Java objects but dislike many other things about it. For example, I dislike the it wraps generated classes around my objects, I dislike that it adds a lot of dependencies to my project, I dislike that it is complicated and difficult to configure, and I dislike that it is difficult/time consuming to adapt to existing schemas. For many projects, JPA is overkill for what you're trying to do while the low level JDBC API is too painful to be useful. Further, neither option can be implemented quickly when you're developing a project within strict time constraints. Strangely, there seems to be very few libraries available which fall in between those two extremes and which also don't require significant setup time or a steep learning curve. If you've ever found yourself in a similar situation or thinking similar things, then you might like the minimalist approach of jdbc-simple.

Generally speaking, the vast majority of SQL database interactions fall into one of the following categories:
* Query returns nothing
* * ```java public boolean execute(final String sql, final Object... arguments) throws SQLException```
* Query returns a single row with a single column
* Query returns a single row with multiple columns
* Query returns multiple rows with one or more columns

These are the core use cases which are handled by jdbc-simple.

# Features
* Very light weight; JDBC simple will not add any transitive dependencies to your project.
* Simple intuitive query API, which is easy to learn
* Binds query results to Java objects

# Examples
