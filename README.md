# jdbc-simple

For many projects, JPA is, overkill for what you're trying to do and the low level JDBC API is too painful to use. Strangely, there seems to be very few libraries available which fall in between those two extremes.

I've always liked how JPA can bind query results to Java objects but dislike many other things about it. For example, I dislike the it wraps generated classes around my objects and makes it more difficult to work with native SQL. If you've ever found yourself thinking this or similar things, then you might like the minimalist approach of jdbc-simple. 

