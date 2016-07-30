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

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author <a href="mailto:john@lariat.co">John D. Dunlap</a>
 * @since 9/26/15 4:07 PM - Created with IntelliJ IDEA.
 */
public abstract class Record {
    private ResultSet resultSet;

    public Record(final ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    public int getColumnCount() throws SQLException {
        return resultSet.getMetaData().getColumnCount();
    }

    public String getColumnName(final int position) throws SQLException {
        return resultSet.getMetaData().getColumnName(position);
    }

    public String getColumnClassName(final int position) throws SQLException {
        return resultSet.getMetaData().getColumnClassName(position);
    }

    public Object getValue(final int position) throws SQLException {
        return resultSet.getObject(position);
    }

    public String getStringByName(final String columnLabel) throws SQLException {
        return resultSet.getString(columnLabel);
    }

    public ResultSet getResultSet() {
        return resultSet;
    }
}
