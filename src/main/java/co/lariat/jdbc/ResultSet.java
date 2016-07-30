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

import java.sql.SQLException;
import java.util.Iterator;

/**
 * @author <a href="mailto:john@lariat.co">John D. Dunlap</a>
 * @since 9/14/15 3:27 PM - Created with IntelliJ IDEA.
 */
public abstract class ResultSet implements Iterable<Record> {
    private java.sql.ResultSet resultSet;

    public ResultSet(final java.sql.ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    protected java.sql.ResultSet getResultSet() {
        return resultSet;
    }

    protected abstract Record createRecord(final java.sql.ResultSet resultSet);

    public Iterator<Record> iterator() {
        return new ResultSetIterator(resultSet);
    }

    public class ResultSetIterator implements Iterator <Record>{
        private java.sql.ResultSet resultSet;

        public ResultSetIterator(final java.sql.ResultSet resultSet) {
            this.resultSet = resultSet;
        }

        public boolean hasNext() {
            try {
                return resultSet.next();
            } catch (SQLException e) {
                throw new RuntimeException("Caught SQLException: " + e.toString());
            }
        }

        public Record next() {
            return createRecord(resultSet);
        }
    }

    public void close() throws SQLException {
        resultSet.close();
    }
}
