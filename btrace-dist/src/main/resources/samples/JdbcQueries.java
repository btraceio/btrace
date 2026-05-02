/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import org.openjdk.btrace.core.types.AnyType;
import org.openjdk.btrace.core.aggregation.Aggregation;
import org.openjdk.btrace.core.aggregation.AggregationFunction;
import org.openjdk.btrace.core.aggregation.AggregationKey;
import org.openjdk.btrace.core.annotations.BTrace;
import org.openjdk.btrace.core.annotations.Duration;
import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Location;
import org.openjdk.btrace.core.annotations.OnEvent;
import org.openjdk.btrace.core.annotations.OnMethod;
import org.openjdk.btrace.core.annotations.Return;
import org.openjdk.btrace.core.annotations.Self;
import org.openjdk.btrace.core.annotations.TLS;

import java.sql.Statement;
import java.util.Map;

import static org.openjdk.btrace.core.BTraceUtils.*;

/**
 * BTrace script to print timings for all executed JDBC statements on an event. Demonstrates
 * different types of aggregation function.
 * <p>
 *
 * @author Christian Glencross
 */
@BTrace
public class JdbcQueries {

    private static Map<Statement, String> preparedStatementDescriptions = Collections.newWeakMap();

    private static Aggregation histogram = Aggregations.newAggregation(AggregationFunction.QUANTIZE);

    private static Aggregation average = Aggregations.newAggregation(AggregationFunction.AVERAGE);

    private static Aggregation max = Aggregations.newAggregation(AggregationFunction.MAXIMUM);

    private static Aggregation min = Aggregations.newAggregation(AggregationFunction.MINIMUM);

    private static Aggregation sum = Aggregations.newAggregation(AggregationFunction.SUM);

    private static Aggregation count = Aggregations.newAggregation(AggregationFunction.COUNT);

    private static Aggregation globalCount = Aggregations.newAggregation(AggregationFunction.COUNT);

    @TLS
    private static String preparingStatement;

    @TLS
    private static String executingStatement;

    /**
     * If "--stack" is passed on command line, print the Java stack trace of the JDBC statement.
     * <p>
     * Otherwise we print the SQL.
     */
    private static boolean useStackTrace = Sys.$(2) != null && Strings.strcmp("--stack", Sys.$(2)) == 0;

    // The first couple of probes capture whenever prepared statement and callable statements are
    // instantiated, in order to let us track what SQL they contain.

    /**
     * Capture SQL used to create prepared statements.
     *
     * @param args the list of method parameters. args[1] is the SQL.
     */
    @OnMethod(clazz = "+java.sql.Connection", method = "/prepare(Call|Statement)/")
    public static void onPrepare(AnyType[] args) {
        preparingStatement = useStackTrace ? Threads.jstackStr() : str(args[0]);
    }

    /**
     * Cache SQL associated with a prepared statement.
     *
     * @param arg the return value from the prepareXxx() method.
     */
    @OnMethod(clazz = "+java.sql.Connection", method = "/prepare(Call|Statement)/", location = @Location(Kind.RETURN))
    public static void onPrepareReturn(@Return Statement preparedStatement) {
        if (preparingStatement != null) {
//             print("P"); // Debug Prepared
            Collections.put(preparedStatementDescriptions, preparedStatement, preparingStatement);
            preparingStatement = null;
        }
    }

    // The next couple of probes intercept the execution of a statement. If it execute with no-args,
    // then it must be a prepared statement or callable statement. Get the SQL from the probes up above.
    // Otherwise the SQL is in the first argument.

    @OnMethod(clazz = "+java.sql.Statement", method = "/execute($|Update|Query|Batch)/")
    public static void onExecute(@Self Statement currentStatement, AnyType[] args) {
        if (args.length == 0) {
            // No SQL argument; lookup the SQL from the prepared statement
            executingStatement = Collections.get(preparedStatementDescriptions, (Statement) currentStatement);
        } else {
            // Direct SQL in the first argument
            executingStatement = useStackTrace ? Threads.jstackStr() : str(args[0]);
        }
    }

    @OnMethod(clazz = "+java.sql.Statement", method = "/execute($|Update|Query|Batch)/", location = @Location(Kind.RETURN))
    public static void onExecuteReturn(@Duration long durationL) {

        if (executingStatement == null) {
            return;
        }

//        print("X"); // Debug Executed

        AggregationKey key = Aggregations.newAggregationKey(executingStatement);
        int duration = (int) durationL / 1000;

        Aggregations.addToAggregation(histogram, key, duration);
        Aggregations.addToAggregation(average, key, duration);
        Aggregations.addToAggregation(max, key, duration);
        Aggregations.addToAggregation(min, key, duration);
        Aggregations.addToAggregation(sum, key, duration);
        Aggregations.addToAggregation(count, key, duration);
        Aggregations.addToAggregation(globalCount, duration);

        executingStatement = null;
    }

    @OnEvent
    public static void onEvent() {

        // Top 10 queries only
        Aggregations.truncateAggregation(histogram, 10);

        println("---------------------------------------------");
        Aggregations.printAggregation("Count", count);
        Aggregations.printAggregation("Min", min);
        Aggregations.printAggregation("Max", max);
        Aggregations.printAggregation("Average", average);
        Aggregations.printAggregation("Sum", sum);
        Aggregations.printAggregation("Histogram", histogram);
        Aggregations.printAggregation("Global Count", globalCount);
        println("---------------------------------------------");
    }

}
