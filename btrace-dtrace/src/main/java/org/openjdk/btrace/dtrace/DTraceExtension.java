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

import java.io.File;
import java.io.IOException;
import io.btrace.core.comm.Command;
import io.btrace.core.comm.ErrorCommand;
import io.btrace.core.comm.MessageCommand;
import io.btrace.core.extensions.Extension;
import io.btrace.core.extensions.ExtensionContext;
import org.opensolaris.os.dtrace.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DTrace extension for BTrace.
 *
 * <p>Provides integration with Solaris DTrace for executing D-scripts from BTrace.
 *
 * <p>Use the following code to obtain an instance:
 *
 * <p>Example usage:
 *
 * <pre><code>
 * {@literal @}Injected
 * private static DTraceExtension dtrace;
 * </code></pre>
 */
public final class DTraceExtension extends Extension {
  private static final Logger log = LoggerFactory.getLogger(DTraceExtension.class);

  private volatile ExtensionContext ctx;
  private volatile Consumer consumer;

  @Override
  public void initialize(ExtensionContext ctx) {
    super.initialize(ctx);
    this.ctx = ctx;
  }

  @Override
  public void close() {
    Consumer cons = this.consumer;
    if (cons != null) {
      try {
        cons.close();
      } catch (Exception e) {
        log.debug("Error closing DTrace consumer", e);
      }
      this.consumer = null;
    }
  }

  /**
   * Submits a D-script from the given file.
   *
   * @param file D-script file to submit
   * @param args DTrace macro arguments
   * @throws DTraceException if compilation or execution fails
   * @throws IOException if file cannot be read
   */
  public void submit(File file, String[] args) throws DTraceException, IOException {
    Consumer cons = newConsumer(args);
    cons.compile(file, args);
    start(cons);
  }

  /**
   * Submits a D-script string.
   *
   * @param program D-script as a string
   * @param args DTrace macro arguments
   * @throws DTraceException if compilation or execution fails
   */
  public void submit(String program, String[] args) throws DTraceException {
    Consumer cons = newConsumer(args);
    cons.compile(program, args);
    start(cons);
  }

  private void start(Consumer cons) throws DTraceException {
    cons.enable();
    cons.go(
        th -> {
          send(new ErrorCommand(th));
        });
  }

  private Consumer newConsumer(String[] args) throws DTraceException {
    Consumer cons = new LocalConsumer();
    this.consumer = cons;

    cons.addConsumerListener(
        new ConsumerAdapter() {
          @Override
          public void consumerStarted(ConsumerEvent ce) {
            send(new DTraceStartCommand(ce));
          }

          @Override
          public void consumerStopped(ConsumerEvent ce) {
            Consumer c = ce.getSource();
            Aggregate ag = null;
            try {
              ag = c.getAggregate();
            } catch (DTraceException dexp) {
              send(new ErrorCommand(dexp));
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
              send(new MessageCommand(msg));
            }
            send(new DTraceStopCommand(ce));
            c.close();
          }

          @Override
          public void dataReceived(DataEvent de) {
            send(new DTraceDataCommand(de));
          }

          @Override
          public void dataDropped(DropEvent de) {
            send(new DTraceDropCommand(de));
          }

          @Override
          public void errorEncountered(ErrorEvent ee) throws ConsumerException {
            try {
              super.errorEncountered(ee);
            } catch (ConsumerException ce) {
              send(new DTraceErrorCommand(ce, ee));
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
    if (args != null && args.length > 0) {
      try {
        int pid = Integer.parseInt(args[0]);
        cons.grabProcess(pid);
      } catch (NumberFormatException e) {
        log.debug("First argument '{}' is not a valid PID, skipping process grab", args[0]);
      } catch (Exception e) {
        log.warn("Failed to grab process with PID '{}': {}", args[0], e.getMessage());
      }
    }
    return cons;
  }

  private void send(Command cmd) {
    ExtensionContext c = this.ctx;
    if (c != null) {
      c.send(cmd);
    }
  }
}
