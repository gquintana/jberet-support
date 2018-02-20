/*
 * Copyright (c) 2018 Red Hat, Inc. and/or its affiliates.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Cheng Fang - Initial API and implementation
 */

package org.jberet.support.io;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import javax.batch.runtime.BatchStatus;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;
import org.jberet.runtime.JobExecutionImpl;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("Need to run Cassandra cluster first")
public class CassandraReaderWriterTest {
    static final JobOperator jobOperator = BatchRuntime.getJobOperator();
    static final String writerTestJobName = "org.jberet.support.io.CassandraWriterTest";
    static final String readerTestJobName = "org.jberet.support.io.CassandraReaderTest";

    static final String contactPoints = "localhost";
    static final String keyspace = "test";
    static Cluster cluster;
    static Session session;

    static final String dropKeyspace = "drop keyspace if exists " + keyspace;
    static final String dropTable = "drop table stock_trade";
    static final String deleteAllRows = "truncate stock_trade";

    static final String createKeyspace = "create keyspace if not exists " + keyspace +
            " with replication = {'class': 'SimpleStrategy', 'replication_factor' : 1}";

    static final String useKeyspace = "use " + keyspace;

    static final String createTable =
            "create table if not exists stock_trade (" +
                    "tradedate timestamp, " +
                    "tradetime text, " +
                    "open double, " +
                    "high double, " +
                    "low double, " +
                    "close double, " +
                    "volume double, " +
                    "primary key(tradedate, tradetime))";

    static final String nameMapping = "Date,Time,Open,High,Low,Close,Volume";

    static final String writerInsertCql =
    "insert into stock_trade (tradedate, tradetime, open, high, low, close, volume) values(?, ?, ?, ?, ?, ?, ?)";

    static final String writerInsertCql2 =
    "insert into stock_trade (tradedate, tradetime, open, high, low, close, volume) " +
    "values(:date, :time, :open, :high, :low, :close, :volume)";

    // tradedate is date type (instead of timestamp), which maps to java type
    // com.datastax.driver.core.LocalDate
    // and table name is different (with suffix _date)
    static final String createTableDateColumn =
            "create table if not exists stock_trade_date (" +
                    "tradedate date, " +
                    "tradetime text, " +
                    "open double, " +
                    "high double, " +
                    "low double, " +
                    "close double, " +
                    "volume double, " +
                    "primary key(tradedate, tradetime))";

    static final String deleteAllRowsDate = "truncate stock_trade_date";

    static final String writerInsertCqlDate =
            "insert into stock_trade_date (tradedate, tradetime, open, high, low, close, volume) values(?, ?, ?, ?, ?, ?, ?)";

    static final String writerInsertCql2Date =
            "insert into stock_trade_date (tradedate, tradetime, open, high, low, close, volume) " +
                    "values(:date, :time, :open, :high, :low, :close, :volume)";

    static final String readerSelectCql  = "select tradedate, tradetime, open, high, low, close, volume from stock_trade";
    static final String readerSelectCqlDate = "select tradedate, tradetime, open, high, low, close, volume from stock_trade_date";
    static final String columnMapping = "date,time,open,high,low,close,volume";

    @BeforeClass
    public static void beforeClass() {
        initKeyspaceAndTable();
    }

    @AfterClass
    public static void afterClass() {
        if (session != null) {
            session.close();
        }
    }

    @Before
    public void before() {
        deleteAllRows();
    }

    /**
     * Writes data items into table stock_trade, and then reads from Cassandra.
     * Each data item is of java type java.util.List, which is used to
     * fill cql parameters, one list element for one parameter in order.
     * The java type of each list element must be compatible with the
     * cql type in each column.
     * <p>
     * Note tradedate column has cql type timestamp, which maps to java type java.util.Date.
     * So it contains both date info and time info.
     * <p>
     * Sample output:
     * <pre>
     *       tradedate                       | tradetime | close  | high   | low    | open   | volume
     *      ---------------------------------+-----------+--------+--------+--------+--------+--------
     *       1998-01-02 05:00:00.000000+0000 |     09:30 | 104.44 | 104.44 | 104.44 | 104.44 |  67040
     *       1998-01-02 05:00:00.000000+0000 |     09:31 | 104.31 | 104.44 | 104.31 | 104.31 |  10810
     *       1998-01-02 05:00:00.000000+0000 |     09:32 | 104.44 | 104.44 | 104.31 | 104.37 |  13310
     * </pre>
     * @throws Exception
     */
    @Test
    public void readIBMStockTradeCsvWriteCassandraList() throws Exception {
        Properties jobParams = new Properties();
        Properties jobParams2 = new Properties();
        jobParams.setProperty("beanType", java.util.List.class.getName());
        jobParams.setProperty("contactPoints", contactPoints);
        jobParams.setProperty("keyspace", keyspace);
        jobParams2.putAll(jobParams);
        jobParams.setProperty("cql", writerInsertCql);
        jobParams.setProperty("end", String .valueOf(5));  // read the first 5 lines

        runTest(writerTestJobName, jobParams);

        jobParams = null;
        jobParams2.setProperty("cql", readerSelectCql);
        jobParams2.setProperty("start", String .valueOf(2));
        jobParams2.setProperty("end", String .valueOf(4));
        runTest(readerTestJobName, jobParams2);
    }

    @Test
    public void readIBMStockTradeCsvWriteCassandraMap() throws Exception {
        Properties jobParams = new Properties();
        Properties jobParams2 = new Properties();
        jobParams.setProperty("beanType", java.util.Map.class.getName());
        jobParams.setProperty("contactPoints", contactPoints);
        jobParams.setProperty("keyspace", keyspace);

        //use nameMapping since the bean type is Map, and the input csv has no header
        jobParams.setProperty("nameMapping", nameMapping);
        jobParams.setProperty("parameterNames", nameMapping);
        jobParams2.putAll(jobParams);

        jobParams.setProperty("cql", writerInsertCql2);  // use cql with named parameters
        jobParams.setProperty("end", String .valueOf(8));  // read the first 8 lines

        runTest(writerTestJobName, jobParams);

        jobParams = null;
        jobParams2.setProperty("cql", readerSelectCql);
        jobParams2.setProperty("start", String .valueOf(2));
        jobParams2.setProperty("end", String .valueOf(4));
        runTest(readerTestJobName, jobParams2);
    }

    /**
     * Same as {@link #readIBMStockTradeCsvWriteCassandraMap()}, except that
     * this test uses table stock_trade_date, whose tradedate column only contains
     * date info without time info. The cql data type date maps to java type
     * com.datastax.driver.core.LocalDate
     * <p>
     * Sample output:
     * <pre>
     *      tradedate  | tradetime | close  | high   | low    | open   | volume
     *     ------------+-----------+--------+--------+--------+--------+--------
     *      1998-01-05 |     09:30 | 105.66 | 105.66 | 105.66 | 105.66 |  82630
     *      1998-01-05 |     09:31 | 105.66 | 105.66 | 105.66 | 105.66 |  13690
     *      1998-01-05 |     09:32 | 105.66 | 105.66 | 105.66 | 105.66 |   8790
     *      1998-01-05 |     09:33 | 105.59 | 105.66 | 105.41 | 105.59 |  26880
     * </pre>
     *
     * @throws Exception
     */
    @Test
    public void readIBMStockTradeCsvWriteCassandraMapDate() throws Exception {
        Properties jobParams = new Properties();
        Properties jobParams2 = new Properties();
        jobParams.setProperty("beanType", java.util.Map.class.getName());
        jobParams.setProperty("contactPoints", contactPoints);
        jobParams.setProperty("keyspace", keyspace);

        //use nameMapping since the bean type is Map, and the input csv has no header
        jobParams.setProperty("nameMapping", nameMapping);
        jobParams.setProperty("parameterNames", nameMapping);
        jobParams2.putAll(jobParams);

        jobParams.setProperty("cql", writerInsertCql2Date);  // use cql with named parameters
        jobParams.setProperty("start", String .valueOf(365));
        jobParams.setProperty("end", String .valueOf(400));

        runTest(writerTestJobName, jobParams);
        jobParams = null;

        jobParams2.setProperty("cql", readerSelectCqlDate);
        jobParams2.setProperty("start", String .valueOf(2));
        jobParams2.setProperty("end", String .valueOf(4));
        runTest(readerTestJobName, jobParams2);
    }

    @Test
    public void readIBMStockTradeCsvWriteCassandraPOJO() throws Exception {
        Properties jobParams = new Properties();
        Properties jobParams2 = new Properties();
        jobParams.setProperty("beanType", StockTrade.class.getName());
        jobParams.setProperty("contactPoints", contactPoints);
        jobParams.setProperty("keyspace", keyspace);
        jobParams2.putAll(jobParams);

        //use nameMapping since the bean type is Map, and the input csv has no header
        jobParams.setProperty("nameMapping", nameMapping);
        jobParams.setProperty("parameterNames", nameMapping);

        jobParams.setProperty("cql", writerInsertCql2);  // use cql with named parameters
        jobParams.setProperty("end", String .valueOf(10));  // read the first 10 lines

        runTest(writerTestJobName, jobParams);

        jobParams = null;
        jobParams2.setProperty("cql", readerSelectCql);
        jobParams2.setProperty("columnMapping", columnMapping);
        jobParams2.setProperty("start", String .valueOf(2));
        jobParams2.setProperty("end", String .valueOf(4));
        runTest(readerTestJobName, jobParams2);
    }

    @Test
    public void readIBMStockTradeCsvWriteCassandraPOJODate() throws Exception {
        Properties jobParams = new Properties();
        Properties jobParams2 = new Properties();
        jobParams.setProperty("beanType", StockTrade.class.getName());
        jobParams.setProperty("contactPoints", contactPoints);
        jobParams.setProperty("keyspace", keyspace);
        jobParams2.putAll(jobParams);

        //use nameMapping since the bean type is Map, and the input csv has no header
        jobParams.setProperty("nameMapping", nameMapping);
        jobParams.setProperty("parameterNames", nameMapping);

        jobParams.setProperty("cql", writerInsertCql2Date);  // use cql with named parameters
        jobParams.setProperty("start", String .valueOf(755));
        jobParams.setProperty("end", String .valueOf(785));

        runTest(writerTestJobName, jobParams);

        jobParams = null;
        jobParams2.setProperty("cql", readerSelectCqlDate);
        jobParams2.setProperty("columnMapping", columnMapping);
        jobParams2.setProperty("start", String .valueOf(2));
        jobParams2.setProperty("end", String .valueOf(4));
        runTest(readerTestJobName, jobParams2);
    }

    private void runTest(final String jobName, final Properties jobParams) throws Exception {
        final long jobExecutionId = jobOperator.start(jobName, jobParams);
        final JobExecutionImpl jobExecution = (JobExecutionImpl) jobOperator.getJobExecution(jobExecutionId);
        jobExecution.awaitTermination(1, TimeUnit.MINUTES);
        Assert.assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
    }

    static void initKeyspaceAndTable() {
        final ResultSet resultSet = getSession().execute(createKeyspace);
        if (resultSet.wasApplied()) {
            System.out.printf("Created keyspace %s%n", keyspace);
        }
        getSession().execute(useKeyspace);
        ResultSet resultSet1 = getSession().execute(createTable);
        if (resultSet1.wasApplied()) {
            System.out.printf("Created table STOCK_TRADE%n");
        }
        resultSet1 = getSession().execute(createTableDateColumn);
        if (resultSet1.wasApplied()) {
            System.out.printf("Created table STOCK_TRADE_DATE%n");
        }
    }

    static void deleteAllRows() {
        getSession().execute(deleteAllRows);
        getSession().execute(deleteAllRowsDate);
    }

    static Session getSession() {
        if (session != null) {
            return session;
        }
        if(cluster == null) {
            cluster = new Cluster.Builder().addContactPoint(contactPoints).build();
        }

        return session = cluster.newSession();
    }
}