package org.openjdk.btrace.core.annotations;

import jdk.jfr.Event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JfrBlock {
    Class<? extends Event>[] value();
}
