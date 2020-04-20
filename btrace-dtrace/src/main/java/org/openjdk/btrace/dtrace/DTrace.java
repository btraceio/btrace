/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.dtrace;

import java.io.File;
import java.io.IOException;
import org.openjdk.btrace.core.comm.Command;
import org.openjdk.btrace.core.comm.CommandListener;
import org.openjdk.btrace.core.comm.ErrorCommand;
import org.openjdk.btrace.core.comm.MessageCommand;
import org.opensolaris.os.dtrace.*;

/**
 * Simple wrapper class around DTrace/Java API. This wrapper accepts a BTrace command listener,
 * DTrace file or string and DTrace macro arguments. The events from DTrace are wrapped as BTrace
 * command instances and given to the listener.
 *
 * @author A. Sundararajan
 */
public class DTrace {

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

  private static void start(Consumer cons, final CommandListener listener) throws DTraceException {
    cons.enable();
    cons.go(
        new ExceptionHandler() {
          @Override
          public void handleException(Throwable th) {
            try {
              listener.onCommand(new ErrorCommand(th));
            } catch (IOException ioexp) {
              ioexp.printStackTrace();
            }
          }
        });
  }

  private static Consumer newConsumer(String[] args, final CommandListener listener)
      throws DTraceException {
    Consumer cons = new LocalConsumer();
    cons.addConsumerListener(
        new ConsumerAdapter() {
          private void notify(Command cmd) {
            try {
              listener.onCommand(cmd);
            } catch (IOException ioexp) {
              ioexp.printStackTrace();
            }
          }

          @Override
          public void consumerStarted(ConsumerEvent ce) {
            notify(new DTraceStartCommand(ce));
          }

          @Override
          public void consumerStopped(ConsumerEvent ce) {
            Consumer cons = (Consumer) ce.getSource();
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
