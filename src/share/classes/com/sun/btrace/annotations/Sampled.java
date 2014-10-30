/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.btrace.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 *
 * @author jbachorik
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface Sampled {
    public static final int MEAN_DEFAULT = 10;

    public static enum Sampler {
        None, Avg, Adaptive
    }

    Sampler kind() default Sampler.Avg;
    int mean() default MEAN_DEFAULT;
}
