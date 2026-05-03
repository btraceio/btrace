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
package io.btrace.dtrace;

import io.btrace.core.comm.Command;
import io.btrace.core.comm.CommandListener;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.comm.MessageCommand;
import java.io.File;
import java.io.IOException;
import org.opensolaris.os.dtrace.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple wrapper class around DTrace/Java API. This wrapper accepts a BTrace command listener,
 * DTrace file or string and DTrace macro arguments. The events from DTrace are wrapped as BTrace
 * command instances and given to the listener.
 *
 * @author A. Sundararajan
 */
@SuppressWarnings("RedundantThrows")
public class DTrace {
  private static final Logger log = LoggerFactory.getLogger(DTrace.class);

  /**
   * Submits a D-script from given file and passes given argument array as DTrace macro arguments.
   * The events from DTrace are wrapped as BTrace commands and listener given is notified.
   *
   * @param file D-script file to submit
   * @param args DTrace macro arguments
   * @param listener BTrace command listener that is notified
   */
  public static void submit(File file, String[] args, CommandListener listener)
      throws DTraceException, IOException {
    Consumer cons = newConsumer(args, listener);
    cons.compile(file, args);
    start(cons, listener);
  }

  /**
   * Submits a D-script string and passes the given argument array as DTrace macro arguments. The
   * events from DTrace are wrapped as BTrace commands and listener given is notified.
   *
   * @param program D-script as a string
   * @param args DTrace macro arguments
   * @param listener BTrace command listener that is notified
   */
  public static void submit(String program, String[] args, CommandListener listener)
      throws DTraceException {
    Consumer cons = newConsumer(args, listener);
    cons.compile(program, args);
    start(cons, listener);
  }

  private static void start(Consumer cons, CommandListener listener) throws DTraceException {
    cons.enable();
    cons.go(
        th -> {
          try {
            listener.onCommand(new ErrorCommand(th));
          } catch (IOException ioexp) {
            log.error("Failed to send error command to listener", ioexp);
          }
        });
  }

  private static Consumer newConsumer(String[] args, CommandListener listener)
      throws DTraceException {
    Consumer cons = new LocalConsumer();
    cons.addConsumerListener(
        new ConsumerAdapter() {
          private void notify(Command cmd) {
            try {
              listener.onCommand(cmd);
            } catch (IOException ioexp) {
              log.error("Failed to send command to listener", ioexp);
            }
          }

          @Override
          public void consumerStarted(ConsumerEvent ce) {
            notify(new DTraceStartCommand(ce));
          }

          @Override
          public void consumerStopped(ConsumerEvent ce) {
            Consumer cons = ce.getSource();
            Aggregate ag = null;
            try {
              ag = cons.getAggregate();
            } catch (DTraceException dexp) {
              notify(new ErrorCommand(dexp));
            }
            StringBuilder buf = new StringBuilder();
            if (ag != null) {
              for (Aggregation agg : ag.asMap().values()) {
                String name = agg.getName();
                if (name != null && name.length() > 0) {
                  buf.append(name);
                  buf.append('\n');
                }
                for (AggregationRecord rec : agg.asMap().values()) {
                  buf.append('\t');
                  buf.append(rec.getTuple());
                  buf.append(" ");
                  buf.append(rec.getValue());
                  buf.append('\n');
                }
              }
            }
            String msg = buf.toString();
            if (msg.length() > 0) {
              notify(new MessageCommand(msg));
            }
            notify(new DTraceStopCommand(ce));
            cons.close();
          }

          @Override
          public void dataReceived(DataEvent de) {
            notify(new DTraceDataCommand(de));
          }

          @Override
          public void dataDropped(DropEvent de) {
            notify(new DTraceDropCommand(de));
          }

          @Override
          public void errorEncountered(ErrorEvent ee) throws ConsumerException {
            try {
              super.errorEncountered(ee);
            } catch (ConsumerException ce) {
              notify(new DTraceErrorCommand(ce, ee));
              throw ce;
            }
          }
        });

    // open DTrace Consumer
    cons.open();

    // unused macro arguments are fine
    cons.setOption(Option.argref, "");
    // if no macro arg passed use "" or NULL
    cons.setOption(Option.defaultargs, "");
    // allow empty D-scripts
    cons.setOption(Option.empty, "");
    // be quiet! equivalent to DTrace's -q
    cons.setOption(Option.quiet, "");
    // undefined user land symbols are fine
    cons.setOption(Option.unodefs, "");
    // allow zero matching of probes (needed for late loading)
    cons.setOption(Option.zdefs, "");
    try {
      int pid = Integer.parseInt(args[0]);
      cons.grabProcess(pid);
    } catch (Exception ignored) {
    }
    return cons;
  }
}
