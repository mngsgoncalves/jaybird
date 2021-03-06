/*
 * Firebird Open Source JavaEE Connector - JDBC Driver
 *
 * Distributable under LGPL license.
 * You may obtain a copy of the License at http://www.gnu.org/copyleft/lgpl.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * LGPL License for more details.
 *
 * This file was created by members of the firebird development team.
 * All individual contributions remain the Copyright (C) of those
 * individuals.  Contributors to this file are either listed here or
 * can be obtained from a source control history command.
 *
 * All rights reserved.
 */
package org.firebirdsql.gds.ng;

import org.firebirdsql.common.FBTestProperties;
import org.firebirdsql.common.rules.UsesDatabase;
import org.firebirdsql.gds.ISCConstants;
import org.firebirdsql.gds.TransactionParameterBuffer;
import org.firebirdsql.gds.impl.TransactionParameterBufferImpl;
import org.firebirdsql.gds.ng.fields.RowValue;
import org.firebirdsql.gds.ng.wire.SimpleStatementListener;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.firebirdsql.common.FBTestProperties.*;
import static org.firebirdsql.common.matchers.SQLExceptionMatchers.errorCodeEquals;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Shared test for statement timeout (Firebird 4).
 *
 * @author <a href="mailto:mrotteveel@users.sourceforge.net">Mark Rotteveel</a>
 */
public abstract class AbstractStatementTimeoutTest {

    protected final SimpleStatementListener listener = new SimpleStatementListener();
    protected FbDatabase db;
    private FbTransaction transaction;
    protected FbStatement statement;
    protected final FbConnectionProperties connectionInfo;
    {
        connectionInfo = new FbConnectionProperties();
        connectionInfo.setServerName(FBTestProperties.DB_SERVER_URL);
        connectionInfo.setPortNumber(FBTestProperties.DB_SERVER_PORT);
        connectionInfo.setUser(DB_USER);
        connectionInfo.setPassword(DB_PASSWORD);
        connectionInfo.setDatabaseName(FBTestProperties.getDatabasePath());
        connectionInfo.setEncoding("NONE");
    }

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    @ClassRule
    public static final UsesDatabase usesDatabase = UsesDatabase.usesDatabase();

    protected abstract Class<? extends FbDatabase> getExpectedDatabaseType();

    @Before
    public final void setUp() throws Exception {
        db = createDatabase();
        assertEquals("Unexpected FbDatabase implementation", getExpectedDatabaseType(), db.getClass());

        db.attach();
    }

    @BeforeClass
    public static void requireTimeoutSupport() {
        assumeTrue("Requires statement timeout support", getDefaultSupportInfo().supportsStatementTimeouts());
    }

    protected abstract FbDatabase createDatabase() throws SQLException;

    @Test
    public void testStatementTimeout_sufficientForExecute() throws Exception {
        allocateStatement();
        statement.setTimeout(TimeUnit.MINUTES.toMillis(1));

        // use 'for update' to force individual fetch
        statement.prepare("SELECT * FROM RDB$RELATIONS FOR UPDATE");
        final SimpleStatementListener statementListener = new SimpleStatementListener();
        statement.addStatementListener(statementListener);
        statement.execute(RowValue.EMPTY_ROW_VALUE);
        statement.fetchRows(1);

        Thread.sleep(100);

        statement.fetchRows(1);

        final List<RowValue> rows = statementListener.getRows();
        assertEquals("Expected no row", 2, rows.size());
    }

    @Test
    public void testStatementTimeout_timeoutBetweenExecuteAndFetch() throws Exception {
        allocateStatement();
        statement.setTimeout(TimeUnit.MILLISECONDS.toMillis(75));

        // use 'for update' to force individual fetch
        statement.prepare("SELECT * FROM RDB$RELATIONS FOR UPDATE");
        final SimpleStatementListener statementListener = new SimpleStatementListener();
        statement.addStatementListener(statementListener);
        statement.execute(RowValue.EMPTY_ROW_VALUE);
        // fbclient will delay execute until fetch for remote connections
        statement.fetchRows(1);

        expectedException.expect(SQLTimeoutException.class);
        expectedException.expect(errorCodeEquals(ISCConstants.isc_req_stmt_timeout));

        Thread.sleep(100);

        statement.fetchRows(1);
    }

    @Test
    public void testStatementTimeout_reuseAfterTimeout() throws Exception {
        allocateStatement();

        // use 'for update' to force individual fetch
        statement.prepare("SELECT * FROM RDB$RELATIONS FOR UPDATE");
        statement.setTimeout(TimeUnit.MILLISECONDS.toMillis(75));
        final SimpleStatementListener statementListener = new SimpleStatementListener();
        statement.addStatementListener(statementListener);
        statement.execute(RowValue.EMPTY_ROW_VALUE);
        // fbclient will delay execute until fetch for remote connections
        statement.fetchRows(1);

        Thread.sleep(100);

        try {
            statement.fetchRows(1);
            fail("expected timeout to occur");
        } catch (SQLTimeoutException e) {
            // ignore
        }

        statement.setTimeout(0);
        statementListener.clear();
        statement.addStatementListener(statementListener);
        statement.execute(RowValue.EMPTY_ROW_VALUE);

        statement.fetchRows(1);
        final List<RowValue> rows = statementListener.getRows();
        assertThat("Expected rows", rows, not(empty()));
    }

    @Test
    public void testStatementTimeout_interleaveOperationWithDifferentStatement() throws Exception {
        // Checks if interleaving operations on another statement will not signal the timeout on that other statement
        allocateStatement();

        statement.setTimeout(TimeUnit.MILLISECONDS.toMillis(75));

        // use 'for update' to force individual fetch
        statement.prepare("SELECT * FROM RDB$RELATIONS FOR UPDATE");
        final SimpleStatementListener statementListener = new SimpleStatementListener();
        statement.addStatementListener(statementListener);
        statement.execute(RowValue.EMPTY_ROW_VALUE);
        // fbclient will delay execute until fetch for remote connections
        statement.fetchRows(1);

        Thread.sleep(100);

        FbStatement statement2 = db.createStatement(getOrCreateTransaction());
        // use 'for update' to force individual fetch
        statement2.prepare("SELECT * FROM RDB$RELATIONS FOR UPDATE");
        final SimpleStatementListener statementListener2 = new SimpleStatementListener();
        statement2.addStatementListener(statementListener2);
        statement2.execute(RowValue.EMPTY_ROW_VALUE);
        statement2.fetchRows(1);
        assertEquals("Expected no row", 1, statementListener2.getRows().size());
        statement2.close();

        try {
            statement.fetchRows(1);
            fail("expected timeout to occur");
        } catch (SQLTimeoutException e) {
            // ignore
        }

        statement.setTimeout(0);
        statementListener.clear();
        statement.addStatementListener(statementListener);
        statement.execute(RowValue.EMPTY_ROW_VALUE);

        statement.fetchRows(1);
        final List<RowValue> rows = statementListener.getRows();
        assertEquals("Expected a row", 1, rows.size());
    }

    private FbTransaction getTransaction() throws SQLException {
        TransactionParameterBuffer tpb = new TransactionParameterBufferImpl();
        tpb.addArgument(ISCConstants.isc_tpb_read_committed);
        tpb.addArgument(ISCConstants.isc_tpb_rec_version);
        tpb.addArgument(ISCConstants.isc_tpb_write);
        tpb.addArgument(ISCConstants.isc_tpb_wait);
        return db.startTransaction(tpb);
    }

    protected FbTransaction getOrCreateTransaction() throws SQLException {
        if (transaction == null || transaction.getState() != TransactionState.ACTIVE) {
            transaction = getTransaction();
        }
        return transaction;
    }

    protected void allocateStatement() throws SQLException {
        if (transaction == null || transaction.getState() != TransactionState.ACTIVE) {
            transaction = getTransaction();
        }
        statement = db.createStatement(transaction);
    }

    @After
    public final void tearDown() {
        if (statement != null) {
            try {
                statement.close();
            } catch (SQLException ex) {
                System.out.println("Exception on statement close");
                ex.printStackTrace();
            }
        }
        if (transaction != null) {
            try {
                transaction.commit();
            } catch (SQLException ex) {
                System.out.println("Exception on transaction commit");
                ex.printStackTrace();
            }
        }
        if (db != null) {
            try {
                db.close();
            } catch (SQLException ex) {
                System.out.println("Exception on detach");
                ex.printStackTrace();
            }
        }
    }
}
