package org.openjdk.btrace.runtime;

import java.util.Map;
import jdk.jfr.Event;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.jfr.JfrEvent;

class JfrEventImpl extends JfrEvent {
  private final Event event;
  private final Map<String, Integer> fieldIndex;
  private final DebugSupport debug;

  JfrEventImpl(Event event, Map<String, Integer> fieldIndex, DebugSupport debug) {
    this.event = event;
    this.fieldIndex = fieldIndex;
    this.debug = debug;
  }

  @Override
  public JfrEvent withValue(String fieldName, byte value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, boolean value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, char value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, short value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, int value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, float value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, long value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, double value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public JfrEvent withValue(String fieldName, String value) {
    if (checkField(fieldName)) {
      event.set(fieldIndex.get(fieldName), value);
    }
    return this;
  }

  @Override
  public void commit() {
    event.commit();
  }

  @Override
  public boolean shouldCommit() {
    return event.shouldCommit();
  }

  @Override
  public void begin() {
    event.begin();
  }

  @Override
  public void end() {
    event.end();
  }

  private boolean checkField(String fieldName) {
    if (!fieldIndex.containsKey(fieldName)) {
      debug.warning("Invalid event field: " + fieldName);
      return false;
    }
    return true;
  }
}
