package org.openjdk.btrace.runtime;

import static org.openjdk.btrace.core.annotations.Event.FieldKind.BOOLEANFLAG;
import static org.openjdk.btrace.core.annotations.Event.FieldKind.DATAAMOUNT;
import static org.openjdk.btrace.core.annotations.Event.FieldKind.FREQUENCY;
import static org.openjdk.btrace.core.annotations.Event.FieldKind.MEMORYADDRESS;
import static org.openjdk.btrace.core.annotations.Event.FieldKind.PERCENTAGE;
import static org.openjdk.btrace.core.annotations.Event.FieldKind.TIMESPAN;
import static org.openjdk.btrace.core.annotations.Event.FieldKind.TIMESTAMP;
import static org.openjdk.btrace.core.annotations.Event.FieldKind.UNSIGNED;
import static org.openjdk.btrace.core.annotations.Event.FieldType.BOOLEAN;
import static org.openjdk.btrace.core.annotations.Event.FieldType.BYTE;
import static org.openjdk.btrace.core.annotations.Event.FieldType.CHAR;
import static org.openjdk.btrace.core.annotations.Event.FieldType.DOUBLE;
import static org.openjdk.btrace.core.annotations.Event.FieldType.FLOAT;
import static org.openjdk.btrace.core.annotations.Event.FieldType.INT;
import static org.openjdk.btrace.core.annotations.Event.FieldType.LONG;
import static org.openjdk.btrace.core.annotations.Event.FieldType.SHORT;
import static org.openjdk.btrace.core.annotations.Event.FieldType.STRING;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.jfr.AnnotationElement;
import jdk.jfr.BooleanFlag;
import jdk.jfr.Category;
import jdk.jfr.DataAmount;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.EventFactory;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Frequency;
import jdk.jfr.Label;
import jdk.jfr.MemoryAddress;
import jdk.jfr.Name;
import jdk.jfr.Percentage;
import jdk.jfr.Period;
import jdk.jfr.Registered;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;
import jdk.jfr.Timestamp;
import jdk.jfr.Unsigned;
import jdk.jfr.ValueDescriptor;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.jfr.JfrEvent;

final class JfrEventFactoryImpl implements JfrEvent.Factory {
  private static final Map<String, Class<?>> VALUE_TYPES;
  private static final Map<String, Class<? extends Annotation>> SPECIFICATION_ANNOTATION_TYPES;

  static {
    VALUE_TYPES = new HashMap<>();
    SPECIFICATION_ANNOTATION_TYPES = new HashMap<>();
    VALUE_TYPES.put(BYTE.name(), byte.class);
    VALUE_TYPES.put(BOOLEAN.name(), boolean.class);
    VALUE_TYPES.put(CHAR.name(), char.class);
    VALUE_TYPES.put(INT.name(), int.class);
    VALUE_TYPES.put(SHORT.name(), short.class);
    VALUE_TYPES.put(FLOAT.name(), float.class);
    VALUE_TYPES.put(LONG.name(), long.class);
    VALUE_TYPES.put(DOUBLE.name(), double.class);
    VALUE_TYPES.put(STRING.name(), String.class);

    SPECIFICATION_ANNOTATION_TYPES.put(TIMESTAMP.name(), Timestamp.class);
    SPECIFICATION_ANNOTATION_TYPES.put(TIMESPAN.name(), Timespan.class);
    SPECIFICATION_ANNOTATION_TYPES.put(DATAAMOUNT.name(), DataAmount.class);
    SPECIFICATION_ANNOTATION_TYPES.put(FREQUENCY.name(), Frequency.class);
    SPECIFICATION_ANNOTATION_TYPES.put(MEMORYADDRESS.name(), MemoryAddress.class);
    SPECIFICATION_ANNOTATION_TYPES.put(PERCENTAGE.name(), Percentage.class);
    SPECIFICATION_ANNOTATION_TYPES.put(BOOLEANFLAG.name(), BooleanFlag.class);
    SPECIFICATION_ANNOTATION_TYPES.put(UNSIGNED.name(), Unsigned.class);
  }

  private final EventFactory eventFactory;
  private final Map<String, Integer> fieldIndex = new HashMap<>();

  private Runnable periodicHook = null;
  private final DebugSupport debug;

  JfrEventFactoryImpl(JfrEvent.Template template, DebugSupport debug) {
    this.debug = debug;
    List<AnnotationElement> defAnnotations = new ArrayList<>();
    List<ValueDescriptor> defFields = new ArrayList<>();
    defAnnotations.add(new AnnotationElement(Name.class, template.getName()));
    defAnnotations.add(new AnnotationElement(Registered.class, true));
    defAnnotations.add(new AnnotationElement(StackTrace.class, template.isStacktrace()));
    if (template.getLabel() != null) {
      defAnnotations.add(new AnnotationElement(Label.class, template.getLabel()));
    }
    if (template.getDescription() != null) {
      defAnnotations.add(new AnnotationElement(Description.class, template.getDescription()));
    }
    if (template.getCategory() != null) {
      defAnnotations.add(new AnnotationElement(Category.class, template.getCategory()));
    }
    if (template.getPeriod() != null) {
      defAnnotations.add(new AnnotationElement(Period.class, template.getPeriod()));
    }

    JfrEvent.Template.Field[] fields = template.getFields();
    if (fields != null) {
      for (int i = 0; i < fields.length; i++) {
        JfrEvent.Template.Field field = fields[i];
        List<AnnotationElement> fieldAnnotations = new ArrayList<>();
        if (field.getDescription() != null) {
          fieldAnnotations.add(new AnnotationElement(Description.class, field.getDescription()));
        }
        if (field.getLabel() != null) {
          fieldAnnotations.add(new AnnotationElement(Label.class, field.getLabel()));
        }
        if (field.getSpecificationName() != null) {
          Class<? extends Annotation> annotationType =
              SPECIFICATION_ANNOTATION_TYPES.get(field.getSpecificationName());
          if (annotationType != null) {
            fieldAnnotations.add(
                new AnnotationElement(annotationType, field.getSpecificationValue()));
          }
        }
        ValueDescriptor vd =
            new ValueDescriptor(
                VALUE_TYPES.get(field.getType()), field.getName(), fieldAnnotations);

        defFields.add(vd);
        fieldIndex.put(field.getName(), i);
      }
    }
    debug.debug("Creating event factory: " + template.getName());
    eventFactory = EventFactory.create(defAnnotations, defFields);
    debug.debug("Registering event factory: " + template.getName());
    eventFactory.register();
    if (template.getPeriod() != null && template.getPeriodicHandler() != null) {
      addJfrPeriodicEvent(template);
    }
  }

  @Override
  public JfrEvent newEvent() {
    return new JfrEventImpl(eventFactory.newEvent(), fieldIndex, debug);
  }

  private void addJfrPeriodicEvent(JfrEvent.Template template) {
    try {
      Class<?> handlerClass = Class.forName(template.getOwner());
      Method handlerMethod = handlerClass.getMethod(template.getPeriodicHandler(), JfrEvent.class);
      Runnable hook =
          (Runnable)
              Proxy.newProxyInstance(
                  handlerClass.getClassLoader(),
                  new Class[] {Runnable.class},
                  new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args)
                        throws Throwable {
                      if (method.getName().equals("run")) {
                        try {
                          JfrEvent event = newEvent();
                          return handlerMethod.invoke(null, event);
                        } catch (Throwable t) {
                          t.printStackTrace(System.out);
                          throw t;
                        }
                      } else {
                        return method.invoke(this, args);
                      }
                    }
                  });
      Class<? extends Event> eClz = eventFactory.newEvent().getClass();
      FlightRecorder.addPeriodicEvent(eClz, hook);
      periodicHook = hook;
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      StringBuilder msg = new StringBuilder("Unable to register periodic JFR event of type '");
      String eMsg = e.getMessage();
      msg.append(eMsg.replace('/', '.'));
      msg.append("'");
      DebugSupport.info(msg.toString());
    } catch (Throwable ignored) {
    }
  }

  void unregister() {
    if (periodicHook != null) {
      FlightRecorder.removePeriodicEvent(periodicHook);
    }
    eventFactory.unregister();
  }
}
