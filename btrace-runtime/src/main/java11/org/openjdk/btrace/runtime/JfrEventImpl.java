package org.openjdk.btrace.runtime;

import jdk.jfr.Event;
import org.openjdk.btrace.core.jfr.JfrEvent;

import java.util.Map;

class JfrEventImpl extends JfrEvent {
    private final Event event;
    private final Map<String, Integer> fieldIndex;

    JfrEventImpl(Event event, Map<String, Integer> fieldIndex) {
        this.event = event;
        this.fieldIndex = fieldIndex;
    }

    @Override
    public JfrEvent withValue(String fieldName, byte value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, boolean value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, char value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, short value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, int value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, float value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, long value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, double value) {
        event.set(fieldIndex.get(fieldName), value);
        return this;
    }

    @Override
    public JfrEvent withValue(String fieldName, String value) {
        event.set(fieldIndex.get(fieldName), value);
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
    @SuppressWarnings("unchecked")
    public <T extends Class<?>> T getJfrClass() {
        return (T)event.getClass();
    }
}
