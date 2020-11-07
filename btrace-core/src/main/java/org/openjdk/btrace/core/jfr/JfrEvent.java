package org.openjdk.btrace.core.jfr;

public abstract class JfrEvent {
    public static final class Template {
        private final String owner;
        private final String name;
        private final String label;
        private final String description;
        private final String[] category;
        private final String fields;
        private final boolean stacktrace;
        private final String period;
        private final String periodicHandler;

        public Template(String owner, String name, String label, String description, String[] category, String fields, boolean stacktrace, String period, String periodicHandler) {
            this.owner = owner;
            this.name = name;
            this.label = label;
            this.description = description;
            this.category = category;
            this.fields = fields;
            this.stacktrace = stacktrace;
            this.period = period;
            this.periodicHandler = periodicHandler;
        }

        public String getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }

        public String[] getCategory() {
            return category;
        }

        public String getFields() {
            return fields;
        }

        public boolean isStacktrace() {
            return stacktrace;
        }

        public String getPeriod() {
            return period;
        }

        public String getPeriodicHandler() {
            return periodicHandler;
        }
    }
    public interface  Factory {
        JfrEvent newEvent();
    }

    public static final JfrEvent EMPTY = new JfrEvent() {
        @Override
        public JfrEvent withValue(String fieldName, byte value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, boolean value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, char value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, short value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, int value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, float value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, long value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, double value) {
            return this;
        }

        @Override
        public JfrEvent withValue(String fieldName, String value) {
            return this;
        }

        @Override
        public void commit() {

        }

        @Override
        public boolean shouldCommit() {
            return false;
        }

        @Override
        public void begin() {

        }

        @Override
        public void end() {

        }
    };

    public abstract JfrEvent withValue(String fieldName, byte value);
    public abstract JfrEvent withValue(String fieldName, boolean value);
    public abstract JfrEvent withValue(String fieldName, char value);
    public abstract JfrEvent withValue(String fieldName, short value);
    public abstract JfrEvent withValue(String fieldName, int value);
    public abstract JfrEvent withValue(String fieldName, float value);
    public abstract JfrEvent withValue(String fieldName, long value);
    public abstract JfrEvent withValue(String fieldName, double value);
    public abstract JfrEvent withValue(String fieldName, String value);

    public abstract void commit();
    public abstract boolean shouldCommit();
    public abstract void begin();
    public abstract void end();
}
