package org.openjdk.btrace.core.jfr;

@SuppressWarnings("UnusedReturnValue")
public abstract class JfrEvent {
  public static final class Template {
    public static final class Field {
      private final String name;
      private final String type;
      private final String label;
      private final String description;
      private final String specificationName;
      private final String specificationValue;

      public Field(
          String name,
          String type,
          String label,
          String description,
          String specificationName,
          String specificationValue) {
        this.name = name;
        this.type = type;
        this.label = label;
        this.description = description;
        this.specificationName = specificationName;
        this.specificationValue = specificationValue;
      }

      public String getName() {
        return name;
      }

      public String getType() {
        return type;
      }

      public String getLabel() {
        return label;
      }

      public String getDescription() {
        return description;
      }

      public String getSpecificationName() {
        return specificationName;
      }

      public String getSpecificationValue() {
        return specificationValue;
      }
    }

    private final String owner;
    private final String name;
    private final String label;
    private final String description;
    private final String[] category;
    private final Field[] fields;
    private final boolean stacktrace;
    private final String period;
    private final String periodicHandler;

    public Template(
        String owner,
        String name,
        String label,
        String description,
        String[] category,
        Field[] fields,
        boolean stacktrace,
        String period,
        String periodicHandler) {
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

    public Field[] getFields() {
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

  public interface Factory {
    JfrEvent newEvent();
  }

  public static final JfrEvent EMPTY =
      new JfrEvent() {
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
        public void commit() {}

        @Override
        public boolean shouldCommit() {
          return false;
        }

        @Override
        public void begin() {}

        @Override
        public void end() {}
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
