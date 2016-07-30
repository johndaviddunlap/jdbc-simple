# jdbc-simple

For many projects, JPA is overkill for what you're trying to do, the low level JDBC API is painful too painful to use, and neither can be implemented quickly. Strangely, there seems to be very few libraries available which fall in between those two extremes. I've always liked how JPA can bind query results to Java objects but dislike many other things about it. For example, I dislike the it wraps generated classes around my objects and makes it more difficult to work with native SQL. If you've ever found yourself thinking this or similar things, then you might like the minimalist approach of jdbc-simple. 

Generally speaking, the vast majority of SQL database interactions fall into one of the following categories:
* Query does not return anything
* Query returns a single row with a single column
* Query returns a single row with multiple columns
* Query returns multiple rows with one or more columns

# Pros and cons

# Features

# Examples
