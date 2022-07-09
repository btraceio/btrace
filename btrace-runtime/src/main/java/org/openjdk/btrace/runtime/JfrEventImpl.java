package org.openjdk.btrace.runtime;

import java.util.Map;
import jdk.jfr.Event;
import org.openjdk.btrace.core.jfr.JfrEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JfrEventImpl extends JfrEvent {
  private static final Logger log = LoggerFactory.getLogger(JfrEventImpl.class);

  private final Event event;
  private final Map<String, Integer> fieldIndex;

  JfrEventImpl(Event event, Map<String, Integer> fieldIndex) {
    this.event = event;
    this.fieldIndex = fieldIndex;
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
      if (log.isDebugEnabled()) {
        log.debug("Invalid event field: {}", fieldName);
      }
      return false;
    }
    return true;
  }
}
