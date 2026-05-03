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

import io.btrace.core.comm.EventCommand;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.opensolaris.os.dtrace.Consumer;
import org.opensolaris.os.dtrace.ConsumerEvent;

/**
 * Abstract command to represent consumer event from DTrace.
 *
 * @author A. Sundararajan
 */
public abstract class DTraceConsumerCommand extends EventCommand implements DTraceCommand {
  private ConsumerEvent ce;

  public DTraceConsumerCommand(String type, ConsumerEvent ce) {
    super(type);
    this.ce = ce;
  }

  /** Returns the underlying DTrace ConsumerEvent. */
  public ConsumerEvent getConsumerEvent() {
    return ce;
  }

  /** Returns the Consumer object. */
  public Consumer getConsumer() {
    return ce.getSource();
  }

  public void write(ObjectOutput out) throws IOException {
    out.writeObject(ce);
  }

  public void read(ObjectInput in) throws ClassNotFoundException, IOException {
    ce = (ConsumerEvent) in.readObject();
  }
}
