package org.openjdk.btrace.runtime;

import jdk.jfr.AnnotationElement;
import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.EventFactory;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.FlightRecorderListener;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.Registered;
import jdk.jfr.StackTrace;
import jdk.jfr.ValueDescriptor;
import org.openjdk.btrace.core.BTraceRuntime;
import org.openjdk.btrace.core.DebugSupport;
import org.openjdk.btrace.core.jfr.JfrEvent;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

final class JfrEventFactoryImpl implements JfrEvent.Factory {
    private static final Pattern TYPE_NAME_SPLIT = Pattern.compile("\\s+");
    private static final Map<String, Class<?>> VALUE_TYPES;

    static {
        VALUE_TYPES = new HashMap<>();
        VALUE_TYPES.put("byte", byte.class);
        VALUE_TYPES.put("boolean", boolean.class);
        VALUE_TYPES.put("char", char.class);
        VALUE_TYPES.put("int", int.class);
        VALUE_TYPES.put("short", short.class);
        VALUE_TYPES.put("float", float.class);
        VALUE_TYPES.put("long", long.class);
        VALUE_TYPES.put("double", double.class);
        VALUE_TYPES.put("string", String.class);
    }

    private final EventFactory eventFactory;
    private final Map<String, Integer> fieldIndex = new HashMap<>();

    JfrEventFactoryImpl(JfrEvent.Template template) {
        // init JFR
//        FlightRecorder.getFlightRecorder();

        System.out.println("===> new event factory");
        List<AnnotationElement> defAnnotations = new ArrayList<>();
        List<ValueDescriptor> defFields = new ArrayList<>();
        defAnnotations.add(new AnnotationElement(Name.class, template.getName()));
        defAnnotations.add(new AnnotationElement(Registered.class, true));
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
            defAnnotations.add(new AnnotationElement(StackTrace.class, false));
        }
        int counter = 0;
        StringTokenizer tokenizer = new StringTokenizer(template.getFields(), ",");
        while (tokenizer.hasMoreTokens()) {
            String nextToken = tokenizer.nextToken().trim();
            String[] typeName = TYPE_NAME_SPLIT.split(nextToken);
            defFields.add(new ValueDescriptor(VALUE_TYPES.get(typeName[0].toLowerCase()), typeName[1]));
            fieldIndex.put(typeName[1], counter++);
        }
        eventFactory = EventFactory.create(defAnnotations, defFields);
        eventFactory.register();
        System.out.println("===> " + template.getPeriod() + ", " + template.getPeriodicHandler());
        if (template.getPeriod() != null && template.getPeriodicHandler() != null) {
            addJfrPeriodicEvent(eventFactory.getEventType(), template);
        }
    }

    @Override
    public JfrEvent newEvent() {
        System.out.println("===> new event");
        return new JfrEventImpl(eventFactory.newEvent(), fieldIndex);
    }

    private void addJfrPeriodicEvent(EventType type, JfrEvent.Template template) {
        try {
            Class<?> handlerClass = Class.forName(template.getOwner());
            final Method handlerMethod = handlerClass.getMethod(template.getPeriodicHandler(), JfrEvent.class);
            Runnable hook = (Runnable) Proxy.newProxyInstance(handlerClass.getClassLoader(), new Class[]{Runnable.class}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    System.out.println("===> ***** ");
                    if (method.getName().equals("run")) {
                        JfrEvent event = newEvent();
                        return handlerMethod.invoke(null, event);
                    } else {
                        return method.invoke(this, args);
                    }
                }
            });
            Method m = EventType.class.getDeclaredMethod("getPlatformEventType");
            m.setAccessible(true);
            Object pet = m.invoke(type);
            m = pet.getClass().getMethod("getPeriod");
            long per = (long)m.invoke(pet);
            System.out.println("===> period: " + per);
            Field f = EventFactory.class.getDeclaredField("eventClass");
            f.setAccessible(true);
            Class<? extends Event> eClz = (Class<? extends Event>)f.get(eventFactory);
            System.out.println("===> adding periodic event: " + type);
            System.out.println("===> " + type.getAnnotation(Period.class));
            FlightRecorder.addPeriodicEvent(eClz, hook);
            // TODO periodic event cleanup
//            periodicJfrEvents.add(hook);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            StringBuilder msg = new StringBuilder("Unable to register periodic JFR event of type '");
            String eMsg = e.getMessage();
            msg.append(eMsg.replace('/', '.'));
            msg.append("'");
            DebugSupport.info(msg.toString());
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }
    }
}
