/*
 * Copyright 2008-2010 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.btrace;

import com.sun.btrace.annotations.ProbeClassName;
import com.sun.btrace.annotations.ProbeMethodName;
import com.sun.btrace.annotations.Self;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.PatternSyntaxException;
import sun.reflect.Reflection;
import com.sun.btrace.aggregation.Aggregation;
import com.sun.btrace.aggregation.AggregationFunction;
import com.sun.btrace.aggregation.AggregationKey;
import java.io.Serializable;
import java.lang.management.MemoryUsage;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * This class is an all-in-one wrapper for methods from {@linkplain Utils}
 * @author Jaroslav Bachorik
 */
public class BTraceUtils {
    // standard stack depth decrement for Reflection.getCallerClass() calls
    private static final int STACK_DEC = 2;

    // do not allow object creation!
    private BTraceUtils() {}

    // Thread and stack access

    /**
     * Tests whether this thread has been interrupted.  The <i>interrupted
     * status</i> of the thread is unaffected by this method.
     *
     * <p>A thread interruption ignored because a thread was not alive
     * at the time of the interrupt will be reflected by this method
     * returning false.
     *
     * @return <code>true</code> if this thread has been interrupted;
     *         <code>false</code> otherwise.
     */
    public static boolean isInteruppted() {
        return Threads.isInteruppted();
    }

    /**
     * Prints the java stack trace of the current thread.
     */
    public static void jstack() {
        Threads.jstack();
    }

    /**
     * Prints the java stack trace of the current thread. But,
     * atmost given number of frames.
     *
     * @param numFrames number of frames to be printed. When this is
     *        negative all frames are printed.
     */
    public static void jstack(int numFrames) {
        Threads.jstack(numFrames);
    }

    /**
     * Prints Java stack traces of all the Java threads.
     */
    public static void jstackAll() {
        Threads.jstackAll();
    }

    /**
     * Prints Java stack traces of all the Java threads. But,
     * atmost given number of frames.
     *
     * @param numFrames number of frames to be printed. When this is
     *        negative all frames are printed.
     */
    public static void jstackAll(int numFrames) {
        Threads.jstackAll(numFrames);
    }

    /**
     * Returns the stack trace of current thread as a String.
     *
     * @return the stack trace as a String.
     */
    public static String jstackStr() {
        return Threads.jstackStr();
    }

    /**
     * Returns the stack trace of the current thread as a String
     * but includes atmost the given number of frames.
     *
     * @param  numFrames number of frames to be included. When this is
     *         negative all frames are included.
     * @return the stack trace as a String.
     */
    public static String jstackStr(int numFrames) {
        return Threads.jstackStr(numFrames);
    }

    /**
     * Returns the stack traces of all Java threads as a String.
     *
     * @return the stack traces as a String.
     */
    public static String jstackAllStr() {
        return Threads.jstackAllStr();
    }

    /**
     * Returns atmost given number of frames in stack traces
     * of all threads as a String.
     *
     * @param numFrames number of frames to be included. When this is
     *        negative all frames are included.
     * @return the stack traces as a String.
     */
    public static String jstackAllStr(int numFrames) {
        return Threads.jstackAllStr(numFrames);
    }

    /**
     * Prints the stack trace of the given exception object.
     *
     * @param exception throwable for which stack trace is printed.
     */
    public static void jstack(Throwable exception) {
        Threads.jstack(exception);
    }

    /**
     * Prints the stack trace of the given exception object. But,
     * prints atmost given number of frames.
     *
     * @param exception throwable for which stack trace is printed.
     * @param numFrames maximum number of frames to be printed.
     */
    public static void jstack(Throwable exception, int numFrames) {
        Threads.jstack(exception, numFrames);
    }

    /**
     * Returns the stack trace of given exception object as a String.
     *
     * @param exception the throwable for which stack trace is returned.
     */
    public static String jstackStr(Throwable exception) {
        return Threads.jstackStr(exception);
    }

    /**
     * Returns stack trace of given exception object as a String.
     *
     * @param exception throwable for which stack trace is returned.
     * @param numFrames maximum number of frames to be returned.
     */
    public static String jstackStr(Throwable exception, int numFrames) {
        return Threads.jstackStr(exception, numFrames);
    }

    /**
     * Returns a reference to the currently executing thread object.
     *
     * @return  the currently executing thread.
     */
    public static Thread currentThread() {
        return Threads.currentThread();
    }

    /**
     * Returns the identifier of the given Thread.  The thread ID is a positive
     * <tt>long</tt> number generated when the given thread was created.
     * The thread ID is unique and remains unchanged during its lifetime.
     * When a thread is terminated, the thread ID may be reused.
     */
    public static long threadId(Thread thread) {
        return Threads.threadId(thread);
    }

    /**
     * Returns the state of the given thread.
     * This method is designed for use in monitoring of the system state,
     * not for synchronization control.
     */
    public static Thread.State threadState(Thread thread) {
        return Threads.threadState(thread);
    }

    /**
     * Returns <tt>true</tt> if and only if the current thread holds the
     * monitor lock on the specified object.
     *
     * <p>This method is designed to allow a program to assert that
     * the current thread already holds a specified lock:
     * <pre>
     *     assert Thread.holdsLock(obj);
     * </pre>
     *
     * @param  obj the object on which to test lock ownership
     * @throws NullPointerException if obj is <tt>null</tt>
     * @return <tt>true</tt> if the current thread holds the monitor lock on
     *         the specified object.
     */
    public static boolean holdsLock(Object obj) {
        return Threads.holdsLock(obj);
    }

    /**
     * Prints the Java level deadlocks detected (if any).
     */
    public static void deadlocks() {
        Threads.deadlocks();
    }

    /**
     * Prints deadlocks detected (if any). Optionally prints
     * stack trace of the deadlocked threads.
     *
     * @param stackTrace boolean flag to specify whether to
     *        print stack traces of deadlocked threads or not.
     */
    public static void deadlocks(boolean stackTrace) {
        Threads.deadlocks(stackTrace);
    }

    /**
     * Returns the name of the given thread.
     *
     * @param thread thread whose name is returned
     */
    public static String name(Thread thread) {
        return Threads.name(thread);
    }

    // class loader access
    /**
     * Returns the class loader for the given class. Some implementations may use
     * null to represent the bootstrap class loader. This method will return
     * null in such implementations if this class was loaded by the bootstrap
     * class loader.
     *
     * @param clazz the Class for which the class loader is returned
     */
    public static ClassLoader loader(Class clazz) {
        return clazz.getClassLoader();
    }

    /**
     * Returns the parent class loader of the given loader. Some implementations may
     * use <tt>null</tt> to represent the bootstrap class loader. This method
     * will return <tt>null</tt> in such implementations if this class loader's
     * parent is the bootstrap class loader.
     *
     * @param  loader the loader for which the parent loader is returned
     * @return The parent <tt>ClassLoader</tt>
     */
    public static ClassLoader parentLoader(ClassLoader loader) {
        return loader.getParent();
    }

    // java.lang.Object methods

    /**
     * Returns a string representation of the object. In general, the
     * <code>toString</code> method returns a string that
     * "textually represents" this object. The result should
     * be a concise but informative representation that is easy for a
     * person to read. For bootstrap classes, returns the result of
     * calling Object.toString() override. For non-bootstrap classes,
     * default toString() value [className@hashCode] is returned.
     *
     * @param  obj the object whose string representation is returned
     * @return a string representation of the given object.
     */
    public static String str(Object obj) {
        return Strings.str(obj);
    }

    /**
     * Returns identity string of the form class-name@identity-hash
     *
     * @param obj object for which identity string is returned
     * @return identity string
     */
    public static String identityStr(Object obj) {
        int hashCode = java.lang.System.identityHashCode(obj);
        return obj.getClass().getName() + "@" + Integer.toHexString(hashCode);
    }

    /**
     * Returns a hash code value for the object. This method is supported
     * for the benefit of hashtables such as those provided by
     * <code>java.util.Hashtable</code>. For bootstrap classes, returns the
     * result of calling Object.hashCode() override. For non-bootstrap classes,
     * the identity hash code is returned.
     *
     * @param obj the Object whose hash code is returned.
     * @return  a hash code value for the given object.
     */
    public static int hash(Object obj) {
        if (obj.getClass().getClassLoader() == null) {
            return obj.hashCode();
        } else {
            return java.lang.System.identityHashCode(obj);
        }
    }

    /**
     * Returns the same hash code for the given object as
     * would be returned by the default method hashCode(),
     * whether or not the given object's class overrides
     * hashCode(). The hash code for the null reference is zero.
     *
     * @param  obj object for which the hashCode is to be calculated
     * @return the hashCode
     */
    public static int identityHashCode(Object obj) {
        return java.lang.System.identityHashCode(obj);
    }

    /**
     * Indicates whether two given objects are "equal to" one another.
     * For bootstrap classes, returns the result of calling Object.equals()
     * override. For non-bootstrap classes, the reference identity comparison
     * is done.
     *
     * @param  obj1 first object to compare equality
     * @param  obj2 second object to compare equality
     * @return <code>true</code> if the given objects are equal;
     *         <code>false</code> otherwise.
     */
    public static boolean compare(Object obj1, Object obj2) {
        return BTraceRuntime.compare(obj1, obj2);
    }

    // reflection
    /**
     * Returns the runtime class of the given Object.
     *
     * @param  obj the Object whose Class is returned
     * @return the Class object of given object
     */
    public static Class classOf(Object obj) {
        return Reflective.classOf(obj);
    }

    /**
     * Returns the Class object representing the class or interface
     * that declares the field represented by the given Field object.

     * @param field whose declaring Class is returned
     */
    public static Class declaringClass(Field field) {
        return Reflective.declaringClass(field);
    }

    /**
     * Returns the name of the given Class object.
     */
    public static String name(Class clazz) {
        return Reflective.name(clazz);
    }

    /**
     * Returns the name of the Field object.
     *
     * @param  field Field for which name is returned
     * @return name of the given field
     */
    public static String name(Field field) {
        return Reflective.name(field);
    }

    /**
     * Returns the type of the Field object.
     *
     * @param  field Field for which type is returned
     * @return type of the given field
     */
    public static Class type(Field field) {
        return Reflective.type(field);
    }

    /**
     * Returns the access flags of the given Class.
     */
    public static int accessFlags(Class clazz) {
        return Reflective.accessFlags(clazz);
    }

    /**
     * Returns the access flags of the given Field.
     */
    public static int accessFlags(Field field) {
        return Reflective.accessFlags(field);
    }

    /**
     * Returns the current context class loader
     */
    public static ClassLoader contextClassLoader() {
        return Reflective.contextClassLoader();
    }

    // get Class of the given name

    /**
     * Returns Class object for given class name.
     */
    public static Class classForName(String name) {
        return Reflective.classForName(name);
    }

    /**
     * Returns the Class for the given class name
     * using the given class loader.
     */
    public static Class classForName(String name, ClassLoader cl) {
        return Reflective.classForName(name, cl);
    }

    /**
     * Determines if the class or interface represented by the first
     * <code>Class</code> object is either the same as, or is a superclass or
     * superinterface of, the class or interface represented by the second
     * <code>Class</code> parameter. It returns <code>true</code> if so;
     * otherwise it returns <code>false</code>.
     */
    public static boolean isAssignableFrom(Class<?> a, Class<?> b) {
        return Reflective.isAssignableFrom(a, b);
    }


    /**
     * Determines if the specified <code>Object</code> is assignment-compatible
     * with the object represented by the specified <code>Class</code>. This method is
     * the dynamic equivalent of the Java language <code>instanceof</code>
     * operator. The method returns <code>true</code> if the specified
     * <code>Object</code> argument is non-null and can be cast to the
     * reference type represented by this <code>Class</code> object without
     * raising a <code>ClassCastException.</code> It returns <code>false</code>
     * otherwise.
     *
     * @param  clazz the class that is checked.
     * @param  obj the object to check.
     * @return  true if <code>obj</code> is an instance of the given class.
     */
    public static boolean isInstance(Class clazz, Object obj) {
        return Reflective.isInstance(clazz, obj);
    }

    /**
     * Returns the <code>Class</code> representing the superclass of the entity
     * (class, interface, primitive type or void) represented by the given
     * <code>Class</code>.  If the given <code>Class</code> represents either the
     * <code>Object</code> class, an interface, a primitive type, or void, then
     * null is returned.  If the given object represents an array class then the
     * <code>Class</code> object representing the <code>Object</code> class is
     * returned.
     *
     * @param clazz the Class whose super class is returned.
     * @return the superclass of the class represented by the given object.
     */
    public static Class getSuperclass(Class clazz) {
        return Reflective.getSuperclass(clazz);
    }

    /**
     * Determines if the specified <code>Class</code> object represents an
     * interface type.
     *
     * @param clazz the Class object to check.
     * @return  <code>true</code> if the Class represents an interface;
     *          <code>false</code> otherwise.
     */
    public static boolean isInterface(Class clazz) {
        return Reflective.isInterface(clazz);
    }

    /**
     * Determines if the given <code>Class</code> object represents an array class.
     *
     * @param clazz Class object to check.
     * @return  <code>true</code> if the given object represents an array class;
     *          <code>false</code> otherwise.
     */
    public static boolean isArray(Class clazz) {
        return Reflective.isArray(clazz);
    }

    /**
     * Returns whether the given Class represent primitive type or not.
     */
    public static boolean isPrimitive(Class clazz) {
        return Reflective.isPrimitive(clazz);
    }

    /**
     * returns component type of an array Class.
     */
    public static Class getComponentType(Class clazz) {
        return Reflective.getComponentType(clazz);
    }

    // Accessing fields by reflection

    /**
     * Returns a <code>Field</code> object that reflects the specified declared
     * field of the class or interface represented by the given <code>Class</code>
     * object. The <code>name</code> parameter is a <code>String</code> that
     * specifies the simple name of the desired field. Returns <code>null</code> on not finding
     * field if throwException parameter is <code>false</code>. Else throws a <code>RuntimeException</code>
     * when field is not found.
     *
     * @param clazz Class whose field is returned
     * @param name the name of the field
     * @param throwException whether to throw exception on failing to find field or not
     * @return the <code>Field</code> object for the specified field in this
     * class
     */
    public static Field field(Class clazz, String name, boolean throwException) {
        return Reflective.field(clazz, name, throwException);
    }

    /**
     * Returns a <code>Field</code> object that reflects the specified declared
     * field of the class or interface represented by the given <code>Class</code>
     * object. The <code>name</code> parameter is a <code>String</code> that
     * specifies the simple name of the desired field. Throws a <code>RuntimeException</code>
     * when field is not found.
     *
     * @param clazz Class whose field is returned
     * @param name the name of the field
     * @return the <code>Field</code> object for the specified field in this
     * class
     */
    public static Field field(Class clazz, String name) {
        return Reflective.field(clazz, name);
    }

    /**
     * Returns a <code>Field</code> object that reflects the specified declared
     * field of the class or interface represented by the given <code>Class</code>
     * object. The <code>name</code> parameter is a <code>String</code> that
     * specifies the simple name of the desired field. Returns <code>null</code> on not finding
     * field if throwException parameter is <code>false</code>. Else throws a <code>RuntimeException</code>
     * when field is not found.
     *
     * @param clazz Class whose field is returned
     * @param name the name of the field
     * @param throwException whether to throw exception on failing to find field or not
     * @return the <code>Field</code> object for the specified field in this
     * class
     */
    public static Field field(String clazz, String name, boolean throwException) {
        return Reflective.field(clazz, name, throwException);
    }

    /**
     * Returns a <code>Field</code> object that reflects the specified declared
     * field of the class or interface represented by the given <code>Class</code>
     * object. The <code>name</code> parameter is a <code>String</code> that
     * specifies the simple name of the desired field. Throws a <code>RuntimeException</code>
     * when field is not found.
     *
     * @param clazz Class whose field is returned
     * @param name the name of the field
     * @return the <code>Field</code> object for the specified field in this
     * class
     */
    public static Field field(String clazz, String name) {
        return Reflective.field(clazz, name);
    }

    // field value get methods

    /**
     * Gets the value of a static <code>byte</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>byte</code> field
     */
    public static byte getByte(Field field) {
        return Reflective.getByte(field);
    }

    /**
     * Gets the value of an instance <code>byte</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>byte</code> value
     * from
     * @return the value of the <code>byte</code> field
     */
    public static byte getByte(Field field, Object obj) {
        return Reflective.getByte(field, obj);
    }

    /**
     * Gets the value of a static <code>short</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>short</code> field
     */
    public static short getShort(Field field) {
        return Reflective.getShort(field);
    }

    /**
     * Gets the value of an instance <code>short</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>short</code> value
     * from
     * @return the value of the <code>short</code> field
     */
    public static short getShort(Field field, Object obj) {
        return Reflective.getShort(field, obj);
    }

    /**
     * Gets the value of a static <code>int</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>int</code> field
     */
    public static int getInt(Field field) {
        return Reflective.getInt(field);
    }

    /**
     * Gets the value of an instance <code>int</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>int</code> value
     * from
     * @return the value of the <code>int</code> field
     */
    public static int getInt(Field field, Object obj) {
        return Reflective.getInt(field, obj);
    }

    /**
     * Gets the value of a static <code>long</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>long</code> field
     */
    public static long getLong(Field field) {
        return Reflective.getLong(field);
    }

    /**
     * Gets the value of an instance <code>long</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>long</code> value
     * from
     * @return the value of the <code>long</code> field
     */
    public static long getLong(Field field, Object obj) {
        return Reflective.getLong(field, obj);
    }

    /**
     * Gets the value of a static <code>float</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>float</code> field
     */
    public static float getFloat(Field field) {
        return Reflective.getFloat(field);
    }

    /**
     * Gets the value of an instance <code>float</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>float</code> value
     * from
     * @return the value of the <code>float</code> field
     */
    public static float getFloat(Field field, Object obj) {
        return Reflective.getFloat(field, obj);
    }

    /**
     * Gets the value of a static <code>double</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>double</code> field
     */
    public static double getDouble(Field field) {
        return Reflective.getDouble(field);
    }

    /**
     * Gets the value of an instance <code>double</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>double</code> value
     * from
     * @return the value of the <code>double</code> field
     */
    public static double getDouble(Field field, Object obj) {
        return Reflective.getDouble(field, obj);
    }

    /**
     * Gets the value of a static <code>boolean</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>boolean</code> field
     */
    public static boolean getBoolean(Field field) {
        return Reflective.getBoolean(field);
    }

    /**
     * Gets the value of an instance <code>boolean</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>boolean</code> value
     * from
     * @return the value of the <code>boolean</code> field
     */
    public static boolean getBoolean(Field field, Object obj) {
        return Reflective.getBoolean(field, obj);
    }

    /**
     * Gets the value of a static <code>char</code> field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the <code>char</code> field
     */
    public static char getChar(Field field) {
        return Reflective.getChar(field);
    }

    /**
     * Gets the value of an instance <code>char</code> field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the <code>char</code> value
     * from
     * @return the value of the <code>char</code> field
     */
    public static char getChar(Field field, Object obj) {
        return Reflective.getChar(field, obj);
    }

    /**
     * Gets the value of a static reference field.
     *
     * @param field Field object whose value is returned.
     * @return the value of the reference field
     */
    public static Object get(Field field) {
        return Reflective.get(field);
    }

    /**
     * Gets the value of an instance reference field.
     *
     * @param field Field object whose value is returned.
     * @param obj the object to extract the reference value
     * from
     * @return the value of the reference field
     */
    public static Object get(Field field, Object obj) {
        return Reflective.get(field, obj);
    }

    // weak, soft references

    /**
     * Creates and returns a weak reference to the given object.
     *
     * @param obj object for which a weak reference is created.
     * @return a weak reference to the given object.
     */
    public static WeakReference weakRef(Object obj) {
        return References.weakRef(obj);
    }

    /**
     * Creates and returns a soft reference to the given object.
     *
     * @param obj object for which a soft reference is created.
     * @return a soft reference to the given object.
     */
    public static SoftReference softRef(Object obj) {
        return References.softRef(obj);
    }

    /**
     * Returns the given reference object's referent.  If the reference object has
     * been cleared, either by the program or by the garbage collector, then
     * this method returns <code>null</code>.
     *
     * @param ref reference object whose referent is returned.
     * @return	 The object to which the reference refers, or
     *		 <code>null</code> if the reference object has been cleared.
     */
    public static Object deref(Reference ref) {
        return References.deref(ref);
    }

    // probe point access

    /**
     * Returns the Class object of the currently
     * probed (or traced) class.
     * @deprecated Since 1.1. Use {@linkplain ProbeClassName} and {@linkplain Self} annotations instead
     */
    public static Class probeClass() {
        return Reflection.getCallerClass(STACK_DEC);
    }

    /**
     * Returns the currently probed method's name.
     * @deprecated Since 1.1. Use {@linkplain ProbeMethodName} annotation instead
     */
    @Deprecated
    public static String probeMethod() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length >= 4) {
            return stack[3].getMethodName();
        } else {
            return null;
        }
    }

    /**
     * Returns the currently probed source line number (if available).
     */
    public static int probeLine() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        if (stack.length >= 4) {
            return stack[3].getLineNumber();
        } else {
            return -1;
        }
    }

    // printing values

    /**
     * Prints the given Map.
     *
     * @param map Map that is printed.
     */
    public static void printMap(Map map) {
        BTraceRuntime.printMap(map);
    }

    /**
     * Prints the given Map.
     *
     * @param name - the name of the map
     * @param data - the map data
     */
    public static void printStringMap(String name, Map<String,String> data) {
        BTraceRuntime.printStringMap(name, data);
    }

    /**
     * Prints the given Map.
     *
     * @param name - the name of the map
     * @param data - the map data
     */
    public static void printNumberMap(String name, Map<String, ? extends Number> data) {
        BTraceRuntime.printNumberMap(name, data);
    }

    /**
     * Prints a number.
     *
     * @param name - name of the number data
     * @param value - value of the numerical data
     */
    public static void printNumber(String name, Number value) {
        BTraceRuntime.printNumber(name, value);
    }

    /**
     * Prints the elements of the given array as comma
     * separated line bounded by '[' and ']'.
     */
    public static void printArray(Object[] array) {
        StringBuilder buf = new StringBuilder();
        buf.append('[');
        for (Object obj : array) {
            buf.append(Strings.str(obj));
            buf.append(", ");
        }
        buf.append(']');
        println(buf.toString());
    }

    /**
     * Print all instance fields of an object as name-value
     * pairs. Includes the inherited fields as well.
     *
     * @param obj Object whose fields are printed.
     */
    public static void printFields(Object obj) {
        Reflective.printFields(obj, false);
    }

    /**
     * Print all instance fields of an object as name-value
     * pairs. Includes the inherited fields as well. Optionally,
     * prints name of the declaring class before each field - so that
     * if same named field in super class chain may be disambiguated.
     *
     * @param obj Object whose fields are printed.
     * @param classNamePrefix flag to tell whether to prefix field names
     *        names by class name or not.
     */
    public static void printFields(Object obj, boolean classNamePrefix) {
        Reflective.printFields(obj, classNamePrefix);
    }

    /**
     * Print all static fields of the class as name-value
     * pairs. Includes the inherited fields as well.
     *
     * @param clazz Class whose static fields are printed.
     */
    public static void printStaticFields(Class clazz) {
        Reflective.printStaticFields(clazz, false);
    }

    /**
     * Print all static fields of the class as name-value
     * pairs. Includes the inherited fields as well. Optionally,
     * prints name of the declaring class before each field - so that
     * if same named field in super class chain may be disambiguated.
     *
     * @param clazz Class whose static fields are printed.
     * @param classNamePrefix flag to tell whether to prefix field names
     *        names by class name or not.
     */
    public static void printStaticFields(Class clazz, boolean classNamePrefix) {
        Reflective.printStaticFields(clazz, classNamePrefix);
    }

    // various print methods
    public static void print(Object obj) {
        BTraceRuntime.print(Strings.str(obj));
    }

    /**
     * Prints a boolean value.  The string produced by <code>{@link
     * java.lang.String#valueOf(boolean)}</code> is sent to BTrace client
     * for "printing".
     *
     * @param      b   The <code>boolean</code> to be printed
     */

    public static void print(boolean b) {
        print(Boolean.valueOf(b));
    }

    /**
     * Prints a character.  The string produced by <code>{@link
     * java.lang.Character#valueOf(char)}</code> is sent to BTrace client
     * for "printing".
     *
     *
     * @param      c   The <code>char</code> to be printed
     */
    public static void print(char c) {
        print(Character.valueOf(c));
    }

    /**
     * Prints an integer.  The string produced by <code>{@link
     * java.lang.String#valueOf(int)}</code> is sent to BTrace client for "printing".
     *
     * @param      i   The <code>int</code> to be printed
     * @see        java.lang.Integer#toString(int)
     */

    public static void print(int i) {
        print(Integer.valueOf(i));
    }


    /**
     * Prints a long integer.  The string produced by <code>{@link
     * java.lang.String#valueOf(long)}</code> is sent to BTrace client for "printing".
     *
     * @param      l   The <code>long</code> to be printed
     * @see        java.lang.Long#toString(long)
     */
    public static void print(long l) {
        print(Long.valueOf(l));
    }

    /**
     * Prints a floating-point number.  The string produced by <code>{@link
     * java.lang.String#valueOf(float)}</code> is sent to BTrace client for "printing".
     *
     * @param      f   The <code>float</code> to be printed
     * @see        java.lang.Float#toString(float)
     */
    public static void print(float f) {
        print(Float.valueOf(f));
    }


    /**
     * Prints a double-precision floating-point number.  The string produced by
     * <code>{@link java.lang.String#valueOf(double)}</code> is sent to BTrace client
     * for "printing".
     *
     * @param      d   The <code>double</code> to be printed
     * @see        java.lang.Double#toString(double)
     */
    public static void print(double d) {
        print(Double.valueOf(d));
    }

    /**
     * Prints the given object and then prints a newline
     */
    public static void println(Object obj) {
        BTraceRuntime.println(Strings.str(obj));
    }

    /**
     * Prints a boolean and then terminate the line.  This method behaves as
     * though it invokes <code>{@link #print(boolean)}</code> and then
     * <code>{@link #println()}</code>.
     *
     * @param b  The <code>boolean</code> to be printed
     */

    public static void println(boolean b) {
        println(Boolean.valueOf(b));
    }

    /**
     * Prints a character and then terminate the line.  This method behaves as
     * though it invokes <code>{@link #print(char)}</code> and then
     * <code>{@link #println()}</code>.
     *
     * @param c  The <code>char</code> to be printed.
     */
    public static void println(char c) {
        println(Character.valueOf(c));
    }

    /**
     * Prints an integer and then terminate the line.  This method behaves as
     * though it invokes <code>{@link #print(int)}</code> and then
     * <code>{@link #println()}</code>.
     *
     * @param i  The <code>int</code> to be printed.
     */
    public static void println(int i) {
        println(Integer.valueOf(i));
    }

    /**
     * Prints a long and then terminate the line.  This method behaves as
     * though it invokes <code>{@link #print(long)}</code> and then
     * <code>{@link #println()}</code>.
     *
     * @param l  a The <code>long</code> to be printed.
     */
    public static void println(long l) {
        println(Long.valueOf(l));
    }


    /**
     * Prints a float and then terminate the line.  This method behaves as
     * though it invokes <code>{@link #print(float)}</code> and then
     * <code>{@link #println()}</code>.
     *
     * @param f  The <code>float</code> to be printed.
     */
    public static void println(float f) {
        println(Float.valueOf(f));
    }


    /**
     * Prints a double and then terminate the line.  This method behaves as
     * though it invokes <code>{@link #print(double)}</code> and then
     * <code>{@link #println()}</code>.
     *
     * @param d  The <code>double</code> to be printed.
     */
    public static void println(double d) {
        println(Double.valueOf(d));
    }

    /**
     * Terminates the current line by writing the line separator string.  The
     * line separator string is defined by the system property
     * <code>line.separator</code>, and is not necessarily a single newline
     * character (<code>'\n'</code>).
     */
    public static void println() {
        BTraceRuntime.println();
    }

    /**
     * Returns the start time of the Java virtual machine in milliseconds.
     * This method returns the approximate time when the Java virtual
     * machine started.
     *
     * @return start time of the Java virtual machine in milliseconds.
     */
    public static long vmStartTime() {
        return Sys.VM.vmStartTime();
    }

    /**
     * Returns the uptime of the Java virtual machine in milliseconds.
     *
     * @return uptime of the Java virtual machine in milliseconds.
     */
    public static long vmUptime() {
        return Sys.VM.vmUptime();
    }

    /**
     * Returns the current time in milliseconds.  Note that
     * while the unit of time of the return value is a millisecond,
     * the granularity of the value depends on the underlying
     * operating system and may be larger.  For example, many
     * operating systems measure time in units of tens of
     * milliseconds.
     *
     * @return  the difference, measured in milliseconds, between
     *          the current time and midnight, January 1, 1970 UTC.
     */
    public static long timeMillis() {
        return Time.millis();
    }

    /**
     * Returns the current value of the most precise available system
     * timer, in nanoseconds.
     *
     * <p>This method can only be used to measure elapsed time and is
     * not related to any other notion of system or wall-clock time.
     * The value returned represents nanoseconds since some fixed but
     * arbitrary time (perhaps in the future, so values may be
     * negative).  This method provides nanosecond precision, but not
     * necessarily nanosecond accuracy. No guarantees are made about
     * how frequently values change. Differences in successive calls
     * that span greater than approximately 292 years (2<sup>63</sup>
     * nanoseconds) will not accurately compute elapsed time due to
     * numerical overflow.
     *
     * @return The current value of the system timer, in nanoseconds.
     */
    public static long timeNanos() {
        return Time.nanos();
    }

    /**
     * <p>Generates a string timestamp (current date&time)
     * @param format The format to be used - see {@linkplain SimpleDateFormat}
     * @return Returns a string representing current date&time
     * @since 1.1
     */
    public static String timestamp(String format) {
        return Time.timestamp(format);
    }

    /**
     * <p>Generates a string timestamp (current date&time) in the default system format
     * @return Returns a string representing current date&time
     * @since 1.1
     */
    public static String timestamp() {
        return Time.timestamp();
    }

    // String utilities
    public static boolean startsWith(String s, String start) {
        return Strings.startsWith(s, start);
    }

    public static boolean endsWith(String s, String end) {
        return Strings.endsWith(s, end);
    }

    /**
     * This is synonym to "concat".
     *
     * @see #concat(String, String)
     */
    public static String strcat(String str1, String str2) {
        return Strings.strcat(str1, str2);
    }

    /**
     * Concatenates the specified strings together.
     */
    public static String concat(String str1, String str2) {
        return Strings.concat(str1, str2);
    }

    /**
     * Compares two strings lexicographically.
     * The comparison is based on the Unicode value of each character in
     * the strings. The character sequence represented by the first
     * <code>String</code> object is compared lexicographically to the
     * character sequence represented by the second string. The result is
     * a negative integer if the first <code>String</code> object
     * lexicographically precedes the second string. The result is a
     * positive integer if the first <code>String</code> object lexicographically
     * follows the second string. The result is zero if the strings
     * are equal; <code>compareTo</code> returns <code>0</code> exactly when
     * the {@link String#equals(Object)} method would return <code>true</code>.
     */
    public static int compareTo(String str1, String str2) {
        return Strings.compareTo(str1, str2);
    }

    /**
     * This is synonym to "compareTo" method.
     *
     * @see #compareTo
     */
    public static int strcmp(String str1, String str2) {
        return Strings.strcmp(str1, str2);
    }

    /**
     * Compares two strings lexicographically, ignoring case
     * differences. This method returns an integer whose sign is that of
     * calling <code>compareTo</code> with normalized versions of the strings
     * where case differences have been eliminated by calling
     * <code>Character.toLowerCase(Character.toUpperCase(character))</code> on
     * each character.
     */
    public static int compareToIgnoreCase(String str1, String str2) {
        return Strings.compareToIgnoreCase(str1, str2);
    }

    /**
     * This is synonym to "compareToIgnoreCase".
     *
     * @see #compareToIgnoreCase
     */
    public static int stricmp(String str1, String str2) {
        return Strings.stricmp(str1, str2);
    }

    /**
     * Find String within String
     */
    public static int strstr(String str1, String str2) {
        return Strings.strstr(str1, str2);
    }

    public static int indexOf(String str1, String str2) {
        return Strings.indexOf(str1, str2);
    }

    public static int lastIndexOf(String str1, String str2) {
        return Strings.lastIndexOf(str1, str2);
    }

    /**
     * Substring
     */
    public static String substr(String str,  int start, int length) {
        return Strings.substr(str, start, length);
    }
    public static String substr(String str, int start) {
        return Strings.substr(str, start);
    }

    /**
     * Returns the length of the given string.
     * The length is equal to the number of <a href="Character.html#unicode">Unicode
     * code units</a> in the string.
     *
     * @param str String whose length is calculated.
     * @return  the length of the sequence of characters represented by this
     *          object.
     */
    public static int length(String str) {
        return Strings.length(str);
    }

    /**
     * This is synonym for "length".
     *
     * @see #length(String)
     */
    public static int strlen(String str) {
        return Strings.strlen(str);
    }

    // regular expression matching

    /**
     * Compiles the given regular expression into a pattern.  </p>
     *
     * @param  regex
     *         The expression to be compiled
     *
     * @throws  PatternSyntaxException
     *          If the expression's syntax is invalid
     */
    public static Pattern regexp(String regex) {
        return Strings.regexp(regex);
    }

    /**
     * This is synonym for "regexp".
     *
     * @see #regexp(String)
     */
    public static Pattern pattern(String regex) {
        return Strings.pattern(regex);
    }

    /**
     * Compiles the given regular expression into a pattern with the given
     * flags.  </p>
     *
     * @param  regex
     *         The expression to be compiled
     *
     * @param  flags
     *         Match flags, a bit mask that may include
     *         {@link Pattern#CASE_INSENSITIVE}, {@link Pattern#MULTILINE}, {@link Pattern#DOTALL},
     *         {@link Pattern#UNICODE_CASE}, {@link Pattern#CANON_EQ}, {@link Pattern#UNIX_LINES},
     *         {@link Pattern#LITERAL} and {@link Pattern#COMMENTS}
     *
     * @throws  IllegalArgumentException
     *          If bit values other than those corresponding to the defined
     *          match flags are set in <tt>flags</tt>
     *
     * @throws  PatternSyntaxException
     *          If the expression's syntax is invalid
     */
    public static Pattern regexp(String regex, int flags) {
        return Strings.regexp(regex, flags);
    }

    /**
     * This is synonym for "regexp".
     *
     * @see #regexp(String, int)
     */
    public static Pattern pattern(String regex, int flags) {
        return Strings.pattern(regex, flags);
    }

    /**
     * Matches the given (precompiled) regular expression and attempts
     * to match the given input against it.
     */
    public static boolean matches(Pattern regex, String input) {
        return Strings.matches(regex, input);
    }

    /**
     * Compiles the given regular expression and attempts to match the given
     * input against it.
     *
     * <p> An invocation of this convenience method of the form
     *
     * <blockquote><pre>
     * Pattern.matches(regex, input);</pre></blockquote>
     *
     * behaves in exactly the same way as the expression
     *
     * <blockquote><pre>
     * Pattern.compile(regex).matcher(input).matches()</pre></blockquote>
     *
     * <p> If a pattern is to be used multiple times, compiling it once and reusing
     * it will be more efficient than invoking this method each time.  </p>
     *
     * @param  regex
     *         The expression to be compiled
     *
     * @param  input
     *         The character sequence to be matched
     *
     * @throws  PatternSyntaxException
     *          If the expression's syntax is invalid
     */
    public static boolean matches(String regex, String input) {
        return Strings.matches(regex, input);
    }

    // Numbers utilities

    /**
     * Returns a <code>double</code> value with a positive sign, greater
     * than or equal to <code>0.0</code> and less than <code>1.0</code>.
     * Returned values are chosen pseudorandomly with (approximately)
     * uniform distribution from that range.
     */
    public static double random() {
        return Numbers.random();
    }

    /**
     * Returns the natural logarithm (base <i>e</i>) of a <code>double</code>
     * value.  Special cases:
     * <ul><li>If the argument is NaN or less than zero, then the result
     * is NaN.
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     * <li>If the argument is positive zero or negative zero, then the
     * result is negative infinity.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   a value
     * @return  the value ln&nbsp;<code>a</code>, the natural logarithm of
     *          <code>a</code>.
     */
    public strictfp static double log(double a) {
        return Numbers.log(a);
    }

    /**
     * Returns the base 10 logarithm of a <code>double</code> value.
     * Special cases:
     *
     * <ul><li>If the argument is NaN or less than zero, then the result
     * is NaN.
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     * <li>If the argument is positive zero or negative zero, then the
     * result is negative infinity.
     * <li> If the argument is equal to 10<sup><i>n</i></sup> for
     * integer <i>n</i>, then the result is <i>n</i>.
     * </ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   a value
     * @return  the base 10 logarithm of  <code>a</code>.
     */
    public strictfp static double log10(double a) {
        return Numbers.log10(a);
    }

    /**
     * Returns Euler's number <i>e</i> raised to the power of a
     * <code>double</code> value.  Special cases:
     * <ul><li>If the argument is NaN, the result is NaN.
     * <li>If the argument is positive infinity, then the result is
     * positive infinity.
     * <li>If the argument is negative infinity, then the result is
     * positive zero.</ul>
     *
     * <p>The computed result must be within 1 ulp of the exact result.
     * Results must be semi-monotonic.
     *
     * @param   a   the exponent to raise <i>e</i> to.
     * @return  the value <i>e</i><sup><code>a</code></sup>,
     *          where <i>e</i> is the base of the natural logarithms.
     */
    public strictfp static double exp(double a) {
        return Numbers.exp(a);
    }

    /**
     * Returns <code>true</code> if the specified number is a
     * Not-a-Number (NaN) value, <code>false</code> otherwise.
     *
     * @param   d  the value to be tested.
     * @return  <code>true</code> if the value of the argument is NaN;
     *          <code>false</code> otherwise.
     */
    public static boolean isNaN(double d) {
        return Numbers.isNaN(d);
    }

    /**
     * Returns <code>true</code> if the specified number is a
     * Not-a-Number (NaN) value, <code>false</code> otherwise.
     *
     * @param   f the value to be tested.
     * @return  <code>true</code> if the value of the argument is NaN;
     *          <code>false</code> otherwise.
     */
    public static boolean isNaN(float f) {
        return Numbers.isNaN(f);
    }

    /**
     * Returns <code>true</code> if the specified number is infinitely
     * large in magnitude, <code>false</code> otherwise.
     *
     * @param   d the value to be tested.
     * @return  <code>true</code> if the value of the argument is positive
     *          infinity or negative infinity; <code>false</code> otherwise.
     */
    public static boolean isInfinite(double d) {
        return Numbers.isInfinite(d);
    }

    /**
     * Returns <code>true</code> if the specified number is infinitely
     * large in magnitude, <code>false</code> otherwise.
     *
     * @param   f the value to be tested.
     * @return  <code>true</code> if the value of the argument is positive
     *          infinity or negative infinity; <code>false</code> otherwise.
     */
    public static boolean isInfinite(float f) {
        return Numbers.isInfinite(f);
    }

    // string parsing methods

    /**
     * Parses the string argument as a boolean.  The <code>boolean</code>
     * returned represents the value <code>true</code> if the string argument
     * is not <code>null</code> and is equal, ignoring case, to the string
     * {@code "true"}. <p>
     * Example: {@code Boolean.parseBoolean("True")} returns <tt>true</tt>.<br>
     * Example: {@code Boolean.parseBoolean("yes")} returns <tt>false</tt>.
     *
     * @param      s   the <code>String</code> containing the boolean
     *                 representation to be parsed
     * @return     the boolean represented by the string argument
     */
    public static boolean parseBoolean(String s) {
        return Numbers.parseBoolean(s);
    }

    /**
     * Parses the string argument as a signed decimal
     * <code>byte</code>. The characters in the string must all be
     * decimal digits, except that the first character may be an ASCII
     * minus sign <code>'-'</code> (<code>'&#92;u002D'</code>) to
     * indicate a negative value. The resulting <code>byte</code> value is
     * returned.
     *
     * @param s		a <code>String</code> containing the
     *                  <code>byte</code> representation to be parsed
     * @return 		the <code>byte</code> value represented by the
     *                  argument in decimal
     */
    public static byte parseByte(String s) {
        return Numbers.parseByte(s);
    }

    /**
     * Parses the string argument as a signed decimal
     * <code>short</code>. The characters in the string must all be
     * decimal digits, except that the first character may be an ASCII
     * minus sign <code>'-'</code> (<code>'&#92;u002D'</code>) to
     * indicate a negative value. The resulting <code>short</code> value is
     * returned.
     *
     * @param s		a <code>String</code> containing the <code>short</code>
     *                  representation to be parsed
     * @return          the <code>short</code> value represented by the
     *                  argument in decimal.
     */
    public static short parseShort(String s) {
        return Numbers.parseShort(s);
    }

    /**
     * Parses the string argument as a signed decimal integer. The
     * characters in the string must all be decimal digits, except that
     * the first character may be an ASCII minus sign <code>'-'</code>
     * (<code>'&#92;u002D'</code>) to indicate a negative value. The resulting
     * integer value is returned.
     *
     * @param s	   a <code>String</code> containing the <code>int</code>
     *             representation to be parsed
     * @return     the integer value represented by the argument in decimal.
     */
    public static int parseInt(String s) {
        return Numbers.parseInt(s);
    }

    /**
     * Parses the string argument as a signed decimal
     * <code>long</code>.  The characters in the string must all be
     * decimal digits, except that the first character may be an ASCII
     * minus sign <code>'-'</code> (<code>&#92;u002D'</code>) to
     * indicate a negative value. The resulting <code>long</code>
     * value is returned.
     * <p>
     * Note that neither the character <code>L</code>
     * (<code>'&#92;u004C'</code>) nor <code>l</code>
     * (<code>'&#92;u006C'</code>) is permitted to appear at the end
     * of the string as a type indicator, as would be permitted in
     * Java programming language source code.
     *
     * @param      s   a <code>String</code> containing the <code>long</code>
     *             representation to be parsed
     * @return     the <code>long</code> represented by the argument in
     *		   decimal.
     */
    public static long parseLong(String s) {
        return Numbers.parseLong(s);
    }

    /**
     * Returns a new <code>float</code> initialized to the value
     * represented by the specified <code>String</code>, as performed
     * by the <code>valueOf</code> method of class <code>Float</code>.
     *
     * @param      s   the string to be parsed.
     * @return the <code>float</code> value represented by the string
     *         argument.
     */
    public static float parseFloat(String s) {
        return Numbers.parseFloat(s);
    }

    /**
     * Returns a new <code>double</code> initialized to the value
     * represented by the specified <code>String</code>, as performed
     * by the <code>valueOf</code> methcod of class
     * <code>Double</code>.
     *
     * @param      s   the string to be parsed.
     * @return the <code>double</code> value represented by the string
     *         argument.
     */
    public static double parseDouble(String s) {
	return Numbers.parseDouble(s);
    }

    // boxing methods

    /**
     * Returns a <tt>Boolean</tt> instance representing the specified
     * <tt>boolean</tt> value.  If the specified <tt>boolean</tt> value
     * is <tt>true</tt>, this method returns <tt>Boolean.TRUE</tt>;
     * if it is <tt>false</tt>, this method returns <tt>Boolean.FALSE</tt>.
     *
     * @param  b a boolean value.
     * @return a <tt>Boolean</tt> instance representing <tt>b</tt>.
     */
    public static Boolean box(boolean b) {
        return Numbers.box(b);
    }

    /**
     * Returns a <tt>Character</tt> instance representing the specified
     * <tt>char</tt> value.
     *
     * @param  c a char value.
     * @return a <tt>Character</tt> instance representing <tt>c</tt>.
     */
    public static Character box(char c) {
        return Numbers.box(c);
    }

    /**
     * Returns a <tt>Byte</tt> instance representing the specified
     * <tt>byte</tt> value.
     *
     * @param  b a byte value.
     * @return a <tt>Byte</tt> instance representing <tt>b</tt>.
     */
    public static Byte box(byte b) {
        return Numbers.box(b);
    }

    /**
     * Returns a <tt>Short</tt> instance representing the specified
     * <tt>short</tt> value.
     *
     * @param  s a short value.
     * @return a <tt>Short</tt> instance representing <tt>s</tt>.
     */
    public static Short box(short s) {
        return Numbers.box(s);
    }

    /**
     * Returns a <tt>Integer</tt> instance representing the specified
     * <tt>int</tt> value.
     *
     * @param  i an <code>int</code> value.
     * @return a <tt>Integer</tt> instance representing <tt>i</tt>.
     */
    public static Integer box(int i) {
        return Numbers.box(i);
    }

    /**
     * Returns a <tt>Long</tt> instance representing the specified
     * <tt>long</tt> value.
     *
     * @param  l a long value.
     * @return a <tt>Long</tt> instance representing <tt>l</tt>.
     */
    public static Long box(long l) {
        return Numbers.box(l);
    }

    /**
     * Returns a <tt>Float</tt> instance representing the specified
     * <tt>float</tt> value.
     *
     * @param  f a float value.
     * @return a <tt>Float</tt> instance representing <tt>f</tt>.
     */
    public static Float box(float f) {
        return Numbers.box(f);
    }

    /**
     * Returns a <tt>Double</tt> instance representing the specified
     * <tt>double</tt> value.
     *
     * @param  d a double value.
     * @return a <tt>Double</tt> instance representing <tt>d</tt>.
     */
    public static Double box(double d) {
        return Numbers.box(d);
    }

    // unboxing methods

    /**
     * Returns the value of the given <tt>Boolean</tt> object as a boolean
     * primitive.
     *
     * @param b the Boolean object whose value is returned.
     * @return  the primitive <code>boolean</code> value of the object.
     */
    public static boolean unbox(Boolean b) {
        return Numbers.unbox(b);
    }

    /**
     * Returns the value of the given <tt>Character</tt> object as a char
     * primitive.
     *
     * @param ch the Character object whose value is returned.
     * @return  the primitive <code>char</code> value of the object.
     */
    public static char unbox(Character ch) {
        return Numbers.unbox(ch);
    }

    /**
     * Returns the value of the specified Byte as a <code>byte</code>.
     *
     * @param b Byte that is unboxed
     * @return  the byte value represented by the <code>Byte</code>.
     */
    public static byte unbox(Byte b) {
        return Numbers.unbox(b);
    }

    /**
     * Returns the short value represented by <code>Short</code>.
     *
     * @param s Short that is unboxed.
     * @return  the short value represented by the <code>Short</code>.
     */
    public static short unbox(Short s) {
        return Numbers.unbox(s);
    }

    /**
     * Returns the value of represented by <code>Integer</code>.
     *
     * @param i Integer that is unboxed.
     * @return  the int value represented by the <code>Integer</code>.
     */
    public static int unbox(Integer i) {
        return Numbers.unbox(i);
    }

    /**
     * Returns the long value represented by the specified <code>Long</code>.
     *
     * @param l Long to be unboxed.
     * @return  the long value represented by the <code>Long</code>.
     */
    public static long unbox(Long l) {
        return Numbers.unbox(l);
    }

    /**
     * Returns the float value represented by the specified <code>Float</code>.
     *
     * @param f Float to be unboxed.
     * @return  the float value represented by the <code>Float</code>.
     */
    public static float unbox(Float f) {
        return Numbers.unbox(f);
    }

    /**
     * Returns the double value represented by the specified <code>Double</code>.
     *
     * @param d Double to be unboxed.
     */
    public static double unbox(Double d) {
        return Numbers.unbox(d);
    }

    // primitive types to String conversion

    /**
     * Returns a <tt>String</tt> object representing the specified
     * boolean.  If the specified boolean is <code>true</code>, then
     * the string {@code "true"} will be returned, otherwise the
     * string {@code "false"} will be returned.
     *
     * @param b	the boolean to be converted
     * @return the string representation of the specified <code>boolean</code>
     */
    public static String str(boolean b) {
        return Strings.str(b);
    }

    /**
     * Returns a <code>String</code> object representing the
     * specified <code>char</code>.  The result is a string of length
     * 1 consisting solely of the specified <code>char</code>.
     *
     * @param c the <code>char</code> to be converted
     * @return the string representation of the specified <code>char</code>
     */
    public static String str(char c) {
        return Strings.str(c);
    }

    /**
     * Returns a <code>String</code> object representing the
     * specified integer. The argument is converted to signed decimal
     * representation and returned as a string.
     *
     * @param   i   an integer to be converted.
     * @return  a string representation of the argument in base&nbsp;10.
     */
    public static String str(int i) {
        return Strings.str(i);
    }

    /**
     * Returns a string representation of the integer argument as an
     * unsigned integer in base&nbsp;16.
     * <p>
     * The unsigned integer value is the argument plus 2<sup>32</sup>
     * if the argument is negative; otherwise, it is equal to the
     * argument.  This value is converted to a string of ASCII digits
     * in hexadecimal (base&nbsp;16) with no extra leading
     * <code>0</code>s. If the unsigned magnitude is zero, it is
     * represented by a single zero character <code>'0'</code>
     * (<code>'&#92;u0030'</code>); otherwise, the first character of
     * the representation of the unsigned magnitude will not be the
     * zero character. The following characters are used as
     * hexadecimal digits:
     * <blockquote><pre>
     * 0123456789abcdef
     * </pre></blockquote>
     * These are the characters <code>'&#92;u0030'</code> through
     * <code>'&#92;u0039'</code> and <code>'&#92;u0061'</code> through
     * <code>'&#92;u0066'</code>.
     *
     * @param   i   an integer to be converted to a string.
     * @return  the string representation of the unsigned integer value
     *          represented by the argument in hexadecimal (base&nbsp;16).
     */
    public static String toHexString(int i) {
        return Strings.toHexString(i);
    }

    /**
     * Returns a <code>String</code> object representing the specified
     * <code>long</code>.  The argument is converted to signed decimal
     * representation and returned as a string.
     *
     * @param   l a <code>long</code> to be converted.
     * @return  a string representation of the argument in base&nbsp;10.
     */
    public static String str(long l) {
        return Strings.str(l);
    }

    /**
     * Returns a string representation of the <code>long</code>
     * argument as an unsigned integer in base&nbsp;16.
     * <p>
     * The unsigned <code>long</code> value is the argument plus
     * 2<sup>64</sup> if the argument is negative; otherwise, it is
     * equal to the argument.  This value is converted to a string of
     * ASCII digits in hexadecimal (base&nbsp;16) with no extra
     * leading <code>0</code>s.  If the unsigned magnitude is zero, it
     * is represented by a single zero character <code>'0'</code>
     * (<code>'&#92;u0030'</code>); otherwise, the first character of
     * the representation of the unsigned magnitude will not be the
     * zero character. The following characters are used as
     * hexadecimal digits:
     * <blockquote><pre>
     * 0123456789abcdef
     * </pre></blockquote>
     * These are the characters <code>'&#92;u0030'</code> through
     * <code>'&#92;u0039'</code> and  <code>'&#92;u0061'</code> through
     * <code>'&#92;u0066'</code>.
     *
     * @param   l a <code>long</code> to be converted to a string.
     * @return  the string representation of the unsigned <code>long</code>
     * 		value represented by the argument in hexadecimal
     *		(base&nbsp;16).
     */
    public static String toHexString(long l) {
        return Strings.toHexString(l);
    }

    /**
     * Returns a string representation of the <code>float</code>
     * argument. All characters mentioned below are ASCII characters.
     * <ul>
     * <li>If the argument is NaN, the result is the string
     * &quot;<code>NaN</code>&quot;.
     * <li>Otherwise, the result is a string that represents the sign and
     *     magnitude (absolute value) of the argument. If the sign is
     *     negative, the first character of the result is
     *     '<code>-</code>' (<code>'&#92;u002D'</code>); if the sign is
     *     positive, no sign character appears in the result. As for
     *     the magnitude <i>m</i>:
     * <ul>
     * <li>If <i>m</i> is infinity, it is represented by the characters
     *     <code>"Infinity"</code>; thus, positive infinity produces
     *     the result <code>"Infinity"</code> and negative infinity
     *     produces the result <code>"-Infinity"</code>.
     * <li>If <i>m</i> is zero, it is represented by the characters
     *     <code>"0.0"</code>; thus, negative zero produces the result
     *     <code>"-0.0"</code> and positive zero produces the result
     *     <code>"0.0"</code>.
     * <li> If <i>m</i> is greater than or equal to 10<sup>-3</sup> but
     *      less than 10<sup>7</sup>, then it is represented as the
     *      integer part of <i>m</i>, in decimal form with no leading
     *      zeroes, followed by '<code>.</code>'
     *      (<code>'&#92;u002E'</code>), followed by one or more
     *      decimal digits representing the fractional part of
     *      <i>m</i>.
     * <li> If <i>m</i> is less than 10<sup>-3</sup> or greater than or
     *      equal to 10<sup>7</sup>, then it is represented in
     *      so-called "computerized scientific notation." Let <i>n</i>
     *      be the unique integer such that 10<sup><i>n</i> </sup>&lt;=
     *      <i>m</i> &lt; 10<sup><i>n</i>+1</sup>; then let <i>a</i>
     *      be the mathematically exact quotient of <i>m</i> and
     *      10<sup><i>n</i></sup> so that 1 &lt;= <i>a</i> &lt; 10.
     *      The magnitude is then represented as the integer part of
     *      <i>a</i>, as a single decimal digit, followed by
     *      '<code>.</code>' (<code>'&#92;u002E'</code>), followed by
     *      decimal digits representing the fractional part of
     *      <i>a</i>, followed by the letter '<code>E</code>'
     *      (<code>'&#92;u0045'</code>), followed by a representation
     *      of <i>n</i> as a decimal integer, as produced by the
     *      method <code>{@link
     *      java.lang.Integer#toString(int)}</code>.
     * </ul>
     * </ul>
     * How many digits must be printed for the fractional part of
     * <i>m</i> or <i>a</i>? There must be at least one digit
     * to represent the fractional part, and beyond that as many, but
     * only as many, more digits as are needed to uniquely distinguish
     * the argument value from adjacent values of type
     * <code>float</code>. That is, suppose that <i>x</i> is the
     * exact mathematical value represented by the decimal
     * representation produced by this method for a finite nonzero
     * argument <i>f</i>. Then <i>f</i> must be the <code>float</code>
     * value nearest to <i>x</i>; or, if two <code>float</code> values are
     * equally close to <i>x</i>, then <i>f</i> must be one of
     * them and the least significant bit of the significand of
     * <i>f</i> must be <code>0</code>.
     * <p>
     *
     * @param   f   the float to be converted.
     * @return a string representation of the argument.
     */
    public static String str(float f) {
        return Strings.str(f);
    }

    /**
     * Returns a string representation of the <code>double</code>
     * argument. All characters mentioned below are ASCII characters.
     * <ul>
     * <li>If the argument is NaN, the result is the string
     *     &quot;<code>NaN</code>&quot;.
     * <li>Otherwise, the result is a string that represents the sign and
     * magnitude (absolute value) of the argument. If the sign is negative,
     * the first character of the result is '<code>-</code>'
     * (<code>'&#92;u002D'</code>); if the sign is positive, no sign character
     * appears in the result. As for the magnitude <i>m</i>:
     * <ul>
     * <li>If <i>m</i> is infinity, it is represented by the characters
     * <code>"Infinity"</code>; thus, positive infinity produces the result
     * <code>"Infinity"</code> and negative infinity produces the result
     * <code>"-Infinity"</code>.
     *
     * <li>If <i>m</i> is zero, it is represented by the characters
     * <code>"0.0"</code>; thus, negative zero produces the result
     * <code>"-0.0"</code> and positive zero produces the result
     * <code>"0.0"</code>.
     *
     * <li>If <i>m</i> is greater than or equal to 10<sup>-3</sup> but less
     * than 10<sup>7</sup>, then it is represented as the integer part of
     * <i>m</i>, in decimal form with no leading zeroes, followed by
     * '<code>.</code>' (<code>'&#92;u002E'</code>), followed by one or
     * more decimal digits representing the fractional part of <i>m</i>.
     *
     * <li>If <i>m</i> is less than 10<sup>-3</sup> or greater than or
     * equal to 10<sup>7</sup>, then it is represented in so-called
     * "computerized scientific notation." Let <i>n</i> be the unique
     * integer such that 10<sup><i>n</i></sup> &lt;= <i>m</i> &lt;
     * 10<sup><i>n</i>+1</sup>; then let <i>a</i> be the
     * mathematically exact quotient of <i>m</i> and
     * 10<sup><i>n</i></sup> so that 1 &lt;= <i>a</i> &lt; 10. The
     * magnitude is then represented as the integer part of <i>a</i>,
     * as a single decimal digit, followed by '<code>.</code>'
     * (<code>'&#92;u002E'</code>), followed by decimal digits
     * representing the fractional part of <i>a</i>, followed by the
     * letter '<code>E</code>' (<code>'&#92;u0045'</code>), followed
     * by a representation of <i>n</i> as a decimal integer, as
     * produced by the method {@link Integer#toString(int)}.
     * </ul>
     * </ul>
     * How many digits must be printed for the fractional part of
     * <i>m</i> or <i>a</i>? There must be at least one digit to represent
     * the fractional part, and beyond that as many, but only as many, more
     * digits as are needed to uniquely distinguish the argument value from
     * adjacent values of type <code>double</code>. That is, suppose that
     * <i>x</i> is the exact mathematical value represented by the decimal
     * representation produced by this method for a finite nonzero argument
     * <i>d</i>. Then <i>d</i> must be the <code>double</code> value nearest
     * to <i>x</i>; or if two <code>double</code> values are equally close
     * to <i>x</i>, then <i>d</i> must be one of them and the least
     * significant bit of the significand of <i>d</i> must be <code>0</code>.
     * <p>
     *
     * @param   d   the <code>double</code> to be converted.
     * @return a string representation of the argument.
     */
    public static String str(double d) {
        return Strings.str(d);
    }

    /**
     * Exits the BTrace session -- note that the particular client's tracing
     * session exits and not the observed/traced program! After exit call,
     * the trace action method terminates immediately and no other probe action
     * method (of that client) will be called after that.
     *
     * @param exitCode exit value sent to the client
     */
    public static void exit(int exitCode) {
        Sys.exit(exitCode);
    }

    /**
     * This is same as exit(int) except that the exit code
     * is zero.
     *
     * @see #exit(int)
     */
    public static void exit() {
        Sys.exit();
    }

    /**
     * accessing jvmstat (perf) int counter
     */
    public static long perfInt(String name) {
        return Counters.perfInt(name);
    }

    /**
     * accessing jvmstat (perf) long counter
     */
    public static long perfLong(String name) {
        return Counters.perfLong(name);
    }

    /**
     * accessing jvmstat (perf) String counter
     */
    public static String perfString(String name) {
        return Counters.perfString(name);
    }

    /**
     * Operating on maps
     */
    // Create a new map
    public static <K, V> Map<K, V> newHashMap() {
        return Collections.newHashMap();
    }

    public static <K, V> Map<K, V> newWeakMap() {
        return Collections.newWeakMap();
    }

    public static <V> Deque<V> newDeque() {
        return Collections.newDeque();
    }

    // get a particular item from a Map
    public static <K, V> V get(Map<K, V> map, Object key) {
        return Collections.get(map, key);
    }

    // check whether an item exists
    public static <K, V> boolean containsKey(Map<K, V> map, Object key) {
        return Collections.containsKey(map, key);
    }

    public static <K, V> boolean containsValue(Map<K, V> map, Object value) {
        return Collections.containsValue(map, value);
    }

    // put a particular item into a Map
    public static <K, V> V put(Map<K, V> map, K key, V value) {
        return Collections.put(map, key, value);
    }

    // remove a particular item from a Map
    public static <K, V> V remove(Map<K, V> map, Object key) {
        return Collections.remove(map, key);
    }

    // clear all items from a Map
    public static <K, V> void clear(Map<K, V> map) {
        Collections.clear(map);
    }

    // return the size of a Map
    public static <K, V> int size(Map<K, V> map) {
        return Collections.size(map);
    }

    public static <K, V> boolean isEmpty(Map<K, V> map) {
        return Collections.isEmpty(map);
    }

    // operations on collections
    public static <E> int size(Collection<E> coll) {
       return Collections.size(coll);
    }

    public static <E> boolean isEmpty(Collection<E> coll) {
        return Collections.isEmpty(coll);
    }

    public static <E> boolean contains(Collection<E> coll, Object obj) {
        return Collections.contains(coll, obj);
    }

    public static boolean contains(Object[] array, Object value) {
        return Collections.contains(array, value);
    }

    // operations on Deque
    public static <V> void push(Deque<V> queue, V value) {
        Collections.push(queue, value);
    }

    public static <V> V poll(Deque<V> queue) {
        return Collections.poll(queue);
    }

    public static <V> V peek(Deque<V> queue) {
        return Collections.peek(queue);
    }

    public static <V> void addLast(Deque<V> queue, V value) {
    	Collections.addLast(queue, value);
    }

    public static <V> V peekFirst(Deque<V> queue) {
    	return Collections.peekFirst(queue);
    }

    public static <V> V peekLast(Deque<V> queue) {
    	return Collections.peekLast(queue);
    }

    public static <V> V removeLast(Deque<V> queue) {
    	return Collections.removeLast(queue);
    }

    public static <V> V removeFirst(Deque<V> queue) {
    	return Collections.removeFirst(queue);
    }

    /**
     * Returns n'th command line argument. <code>null</code> if not available.
     *
     * @param n command line argument index
     * @return n'th command line argument
     */
    public static String $(int n) {
        return Sys.$(n);
    }

    /**
     * Returns the process id of the currently BTrace'd process.
     */
    public static int getpid() {
        return Sys.getpid();
    }

    /**
     * Returns the number of command line arguments.
     */
    public static int $length() {
        return Sys.$length();
    }

    // atomic stuff

    /**
     * Creates a new AtomicInteger with the given initial value.
     *
     * @param initialValue the initial value
     */
    public static AtomicInteger newAtomicInteger(int initialValue) {
        return Atomic.newAtomicInteger(initialValue);
    }

    /**
     * Gets the current value of the given AtomicInteger.
     *
     * @param ai AtomicInteger whose value is returned.
     * @return the current value
     */
    public static int get(AtomicInteger ai) {
        return Atomic.get(ai);

    }

    /**
     * Sets to the given value to the given AtomicInteger.
     *
     * @param ai AtomicInteger whose value is set.
     * @param newValue the new value
     */
    public static void set(AtomicInteger ai, int newValue) {
        Atomic.set(ai, newValue);
    }

    /**
     * Eventually sets to the given value to the given AtomicInteger.
     *
     * @param ai AtomicInteger whose value is lazily set.
     * @param newValue the new value
     */
    public static void lazySet(AtomicInteger ai, int newValue) {
        Atomic.lazySet(ai, newValue);
    }

    /**
     * Atomically sets the value of given AtomitInteger to the given
     * updated value if the current value {@code ==} the expected value.
     *
     * @param ai AtomicInteger whose value is compared and set.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public static boolean compareAndSet(AtomicInteger ai, int expect, int update) {
        return Atomic.compareAndSet(ai, expect, update);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * <p>May <a href="package-summary.html#Spurious">fail spuriously</a>
     * and does not provide ordering guarantees, so is only rarely an
     * appropriate alternative to {@code compareAndSet}.
     *
     * @param ai AtomicInteger whose value is weakly compared and set.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public static boolean weakCompareAndSet(AtomicInteger ai, int expect, int update) {
        return Atomic.weakCompareAndSet(ai, expect, update);
    }

    /**
     * Atomically increments by one the current value of given AtomicInteger.
     *
     * @param ai AtomicInteger that is incremented.
     * @return the previous value
     */

    public static int getAndIncrement(AtomicInteger ai) {
        return Atomic.getAndIncrement(ai);
    }


    /**
     * Atomically decrements by one the current value of given AtomicInteger.
     *
     * @param ai AtomicInteger that is decremented.
     * @return the previous value
     */
    public static int getAndDecrement(AtomicInteger ai) {
        return Atomic.getAndDecrement(ai);
    }


    /**
     * Atomically increments by one the current value of given AtomicInteger.
     *
     * @param ai AtomicInteger that is incremented.
     * @return the updated value
     */
    public static int incrementAndGet(AtomicInteger ai) {
        return Atomic.incrementAndGet(ai);
    }

    /**
     * Atomically decrements by one the current value of given AtomicInteger.
     *
     * @param ai AtomicInteger whose value is decremented.
     * @return the updated value
     */
    public static int decrementAndGet(AtomicInteger ai) {
        return Atomic.decrementAndGet(ai);
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param ai AtomicInteger whose value is added to.
     * @param delta the value to add
     * @return the previous value
     */
    public static int getAndAdd(AtomicInteger ai, int delta) {
        return Atomic.getAndAdd(ai, delta);
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param ai AtomicInteger whose value is added to.
     * @param delta the value to add
     * @return the updated value
     */
    public static int addAndGet(AtomicInteger ai, int delta) {
        return Atomic.addAndGet(ai, delta);
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param ai AtomicInteger whose value is set.
     * @param newValue the new value
     * @return the previous value
     */
    public static int getAndSet(AtomicInteger ai, int newValue) {
        return Atomic.getAndSet(ai, newValue);
    }

    /**
     * Creates a new AtomicLong with the given initial value.
     *
     * @param initialValue the initial value
     */
    public static AtomicLong newAtomicLong(long initialValue) {
        return Atomic.newAtomicLong(initialValue);
    }


    /**
     * Gets the current value the given AtomicLong.
     *
     * @param al AtomicLong whose value is returned.
     * @return the current value
     */

    public static long get(AtomicLong al) {
        return Atomic.get(al);
    }

    /**
     * Sets to the given value.
     *
     * @param al AtomicLong whose value is set.
     * @param newValue the new value
     */
    public static void set(AtomicLong al, long newValue) {
        Atomic.set(al, newValue);
    }

    /**
     * Eventually sets to the given value to the given AtomicLong.
     *
     * @param al AtomicLong whose value is set.
     * @param newValue the new value
     */
    public static void lazySet(AtomicLong al, long newValue) {
        Atomic.lazySet(al, newValue);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * @param al AtomicLong whose value is compared and set.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public static boolean compareAndSet(AtomicLong al, long expect, long update) {
        return Atomic.compareAndSet(al, expect, update);
    }

    /**
     * Atomically sets the value to the given updated value
     * if the current value {@code ==} the expected value.
     *
     * <p>May fail spuriously
     * and does not provide ordering guarantees, so is only rarely an
     * appropriate alternative to {@code compareAndSet}.
     *
     * @param al AtomicLong whose value is compared and set.
     * @param expect the expected value
     * @param update the new value
     * @return true if successful.
     */
    public static boolean weakCompareAndSet(AtomicLong al, long expect, long update) {
        return Atomic.weakCompareAndSet(al, expect, update);
    }

    /**
     * Atomically increments by one the current value.
     *
     * @param al AtomicLong whose value is incremented.
     * @return the previous value
     */
    public static long getAndIncrement(AtomicLong al) {
        return Atomic.getAndIncrement(al);
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @param al AtomicLong whose value is decremented.
     * @return the previous value
     */
    public static long getAndDecrement(AtomicLong al) {
        return Atomic.getAndDecrement(al);
    }


    /**
     * Atomically increments by one the current value.
     *
     * @param al AtomicLong whose value is incremented.
     * @return the updated value
     */
    public static long incrementAndGet(AtomicLong al) {
        return Atomic.incrementAndGet(al);
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @param al AtomicLong whose value is decremented.
     * @return the updated value
     */
    public static long decrementAndGet(AtomicLong al) {
        return Atomic.decrementAndGet(al);
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param al AtomicLong whose value is added to.
     * @param delta the value to add
     * @return the previous value
     */
    public static long getAndAdd(AtomicLong al, long delta) {
        return Atomic.getAndAdd(al, delta);
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param al AtomicLong whose value is added to
     * @param delta the value to add
     * @return the updated value
     */
    public static long addAndGet(AtomicLong al, long delta) {
        return Atomic.addAndGet(al, delta);
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param al AtomicLong that is set.
     * @param newValue the new value
     * @return the previous value
     */
    public static long getAndSet(AtomicLong al, long newValue) {
        return Atomic.getAndSet(al, newValue);
    }

    /**
     * BTrace to DTrace communication chennal.
     * Raise DTrace USDT probe from BTrace.
     *
     * @see #dtraceProbe(String,String,int,int)
     */
    public static int dtraceProbe(String str1, String str2) {
        return D.probe(str1, str2);
    }

    /**
     * BTrace to DTrace communication chennal.
     * Raise DTrace USDT probe from BTrace.
     *
     * @see #dtraceProbe(String,String,int,int)
     */
    public static int dtraceProbe(String str1, String str2, int i1) {
        return D.probe(str1, str2, i1);
    }

    /**
     * BTrace to DTrace communication channel.
     * Raise DTrace USDT probe from BTrace.
     *
     * @param str1 first String param to DTrace probe
     * @param str2 second String param to DTrace probe
     * @param i1 first int param to DTrace probe
     * @param i2 second int param to DTrace probe
     */
    public static int dtraceProbe(String str1, String str2, int i1, int i2) {
        return D.probe(str1, str2, i1, i2);
    }

    /**
     * Gets the system property indicated by the specified key.
     *
     * @param      key   the name of the system property.
     * @return     the string value of the system property,
     *             or <code>null</code> if there is no property with that key.
     *
     * @exception  NullPointerException if <code>key</code> is
     *             <code>null</code>.
     * @exception  IllegalArgumentException if <code>key</code> is empty.
     */
    public static String property(String key) {
        return Sys.Env.property(key);
    }

    /**
     * Returns all Sys properties.
     *
     * @return the system properties
     */
    public static Properties properties() {
        return Sys.Env.properties();
    }

    /**
     * Prints all Sys properties.
     */
    public static void printProperties() {
        Sys.Env.printProperties();
    }

    /**
     * Gets the value of the specified environment variable. An
     * environment variable is a system-dependent external named
     * value.
     *
     * @param  name the name of the environment variable
     * @return the string value of the variable, or <code>null</code>
     *         if the variable is not defined in the system environment
     * @throws NullPointerException if <code>name</code> is <code>null</code>
     */
    public static String getenv(String name) {
        return Sys.Env.getenv(name);
    }

    /**
     * Returns an unmodifiable string map view of the current system environment.
     * The environment is a system-dependent mapping from names to
     * values which is passed from parent to child processes.
     *
     * @return the environment as a map of variable names to values
     */
    public static Map<String, String> getenv() {
        return Sys.Env.getenv();
    }

    /**
     * Prints all system environment values.
     */
    public static void printEnv() {
        Sys.Env.printEnv();
    }

    /**
     * Returns the number of processors available to the Java virtual machine.
     *
     * <p> This value may change during a particular invocation of the virtual
     * machine.  Applications that are sensitive to the number of available
     * processors should therefore occasionally poll this property and adjust
     * their resource usage appropriately. </p>
     *
     * @return  the maximum number of processors available to the virtual
     *          machine; never smaller than one
     */
    public static long availableProcessors() {
        return Sys.Env.availableProcessors();
    }

    // memory usage
    /**
     * Returns the amount of free memory in the Java Virtual Machine.
     * Calling the
     * <code>gc</code> method may result in increasing the value returned
     * by <code>freeMemory.</code>
     *
     * @return  an approximation to the total amount of memory currently
     *          available for future allocated objects, measured in bytes.
     */
    public static long freeMemory() {
        return Sys.Memory.freeMemory();
    }

    /**
     * Returns the total amount of memory in the Java virtual machine.
     * The value returned by this method may vary over time, depending on
     * the host environment.
     * <p>
     * Note that the amount of memory required to hold an object of any
     * given type may be implementation-dependent.
     *
     * @return  the total amount of memory currently available for current
     *          and future objects, measured in bytes.
     */
    public static long totalMemory() {
        return Sys.Memory.totalMemory();
    }

    /**
     * Returns the maximum amount of memory that the Java virtual machine will
     * attempt to use.  If there is no inherent limit then the value {@link
     * java.lang.Long#MAX_VALUE} will be returned. </p>
     *
     * @return  the maximum amount of memory that the virtual machine will
     *          attempt to use, measured in bytes
     */
    public static long maxMemory() {
        return Sys.Memory.maxMemory();
    }

    /**
     * Returns heap memory usage
     */
    public static MemoryUsage heapUsage() {
        return Sys.Memory.heapUsage();
    }

    /**
     * Returns non-heap memory usage
     */
    public static MemoryUsage nonHeapUsage() {
        return Sys.Memory.nonHeapUsage();
    }

    /**
     * Returns the amount of memory in bytes that the Java virtual
     * machine initially requests from the operating system for
     * memory management.
     */
    public static long init(MemoryUsage mu) {
        return Sys.Memory.init(mu);
    }

    /**
     * Returns the amount of memory in bytes that is committed for the Java
     * virtual machine to use. This amount of memory is guaranteed for the
     * Java virtual machine to use.
     */
    public static long committed(MemoryUsage mu) {
        return Sys.Memory.committed(mu);
    }

    /**
     * Returns the maximum amount of memory in bytes that can be used
     * for memory management. This method returns -1 if the maximum memory
     * size is undefined.
     */
    public static long max(MemoryUsage mu) {
        return Sys.Memory.max(mu);
    }

    /**
     * Returns the amount of used memory in bytes.
     */
    public static long used(MemoryUsage mu) {
        return Sys.Memory.used(mu);
    }

    /**
     * Returns the approximate number of objects for
     * which finalization is pending.
     */
    public static long finalizationCount() {
        return Sys.Memory.finalizationCount();
    }

    /**
     * Returns the input arguments passed to the Java virtual machine
     * which does not include the arguments to the <tt>main</tt> method.
     * This method returns an empty list if there is no input argument
     * to the Java virtual machine.
     * <p>
     * Some Java virtual machine implementations may take input arguments
     * from multiple different sources: for examples, arguments passed from
     * the application that launches the Java virtual machine such as
     * the 'java' command, environment variables, configuration files, etc.
     * <p>
     * Typically, not all command-line options to the 'java' command
     * are passed to the Java virtual machine.
     * Thus, the returned input arguments may not
     * include all command-line options.
     *
     * @return a list of <tt>String</tt> objects; each element
     * is an argument passed to the Java virtual machine.
     */
    public static List<String> vmArguments() {
        return Sys.VM.vmArguments();
    }

    /**
     * Prints VM input arguments list.
     *
     * @see #vmArguments
     */
    public static void printVmArguments() {
        Sys.VM.printVmArguments();
    }

    /**
     * Returns the Java virtual machine implementation version.
     * This method is equivalent to {@link Sys#getProperty
     * Sys.getProperty("java.vm.version")}.
     *
     * @return the Java virtual machine implementation version.
     */
    public static String vmVersion() {
        return Sys.VM.vmVersion();
    }

    /**
     * Tests if the Java virtual machine supports the boot class path
     * mechanism used by the bootstrap class loader to search for class
     * files.
     *
     * @return <tt>true</tt> if the Java virtual machine supports the
     * class path mechanism; <tt>false</tt> otherwise.
     */
    public static boolean isBootClassPathSupported() {
        return Sys.VM.isBootClassPathSupported();
    }

    /**
     * Returns the boot class path that is used by the bootstrap class loader
     * to search for class files.
     *
     * <p> Multiple paths in the boot class path are separated by the
     * path separator character of the platform on which the Java
     * virtual machine is running.
     *
     * <p>A Java virtual machine implementation may not support
     * the boot class path mechanism for the bootstrap class loader
     * to search for class files.
     * The {@link #isBootClassPathSupported} method can be used
     * to determine if the Java virtual machine supports this method.
     *
     * @return the boot class path.
     * @throws java.lang.UnsupportedOperationException
     *     if the Java virtual machine does not support this operation.
     */
    public static String bootClassPath() {
        return Sys.VM.bootClassPath();
    }

    /**
     * Returns the Java class path that is used by the system class loader
     * to search for class files.
     * This method is equivalent to {@link Sys#getProperty
     * Sys.getProperty("java.class.path")}.
     *
     * @return the Java class path.
     */
    public static String classPath() {
        return Sys.VM.classPath();
    }

    /**
     * Returns the Java library path.
     * This method is equivalent to {@link Sys#getProperty
     * Sys.getProperty("java.library.path")}.
     *
     * <p> Multiple paths in the Java library path are separated by the
     * path separator character of the platform of the Java virtual machine
     * being monitored.
     *
     * @return the Java library path.
     */
    public static String libraryPath() {
        return Sys.VM.libraryPath();
    }

    /**
     * Returns the current number of live threads including both
     * daemon and non-daemon threads.
     *
     * @return the current number of live threads.
     */
    public static long threadCount() {
        return Sys.VM.threadCount();
    }

    /**
     * Returns the peak live thread count since the Java virtual machine
     * started or peak was reset.
     *
     * @return the peak live thread count.
     */
    public static long peakThreadCount() {
        return Sys.VM.peakThreadCount();
    }

    /**
     * Returns the total number of threads created and also started
     * since the Java virtual machine started.
     *
     * @return the total number of threads started.
     */
    public static long totalStartedThreadCount() {
        return Sys.VM.totalStartedThreadCount();
    }

    /**
     * Returns the current number of live daemon threads.
     *
     * @return the current number of live daemon threads.
     */
    public static long daemonThreadCount() {
        return Sys.VM.daemonThreadCount();
    }


    /**
     * Returns the total CPU time for the current thread in nanoseconds.
     * The returned value is of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     * If the implementation distinguishes between user mode time and system
     * mode time, the returned CPU time is the amount of time that
     * the current thread has executed in user mode or system mode.
     */
    public static long currentThreadCpuTime() {
        return Sys.VM.currentThreadCpuTime();
    }

    /**
     * Returns the CPU time that the current thread has executed
     * in user mode in nanoseconds.
     * The returned value is of nanoseconds precision but
     * not necessarily nanoseconds accuracy.
     */
    public static long currentThreadUserTime() {
        return Sys.VM.currentThreadUserTime();
    }

    /**
     * Returns the total amount of time spent in GarbageCollection up to this point
     * since the application was started.
     * @return
     */
    public static long getTotalGcTime() {
    	return Sys.Memory.getTotalGcTime();
    }

    /**
     * Returns an implementation-specific approximation of the amount of storage consumed by
     * the specified object. The result may include some or all of the object's overhead,
     * and thus is useful for comparison within an implementation but not between implementations.
     *
     * The estimate may change during a single invocation of the JVM.
     *
     * @param objectToSize     the object to size
     * @return an implementation-specific approximation of the amount of storage consumed by the specified object
     * @throws java.lang.NullPointerException if the supplied Object is <code>null</code>.
     */
    public static long sizeof(Object objectToSize) {
        return BTraceRuntime.sizeof(objectToSize);
    }

    /**
     * Dump the snapshot of the Java heap to a file in hprof
     * binary format. Only the live objects are dumped.
     * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
     * directory is created. Under that directory, a file of given
     * fileName is created.
     *
     * @param fileName name of the file to which heap is dumped
     */
    public static void dumpHeap(String fileName) {
        Sys.Memory.dumpHeap(fileName);
    }

    /**
     * Dump the snapshot of the Java heap to a file in hprof
     * binary format.
     * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
     * directory is created. Under that directory, a file of given
     * fileName is created.
     *
     * @param fileName name of the file to which heap is dumped
     * @param live flag that tells whether only live objects are
     *             to be dumped or all objects are to be dumped.
     */
    public static void dumpHeap(String fileName, boolean live) {
        Sys.Memory.dumpHeap(fileName, live);
    }

    /**
     * Runs the garbage collector.
     * <p>
     * Calling the <code>gc</code> method suggests that the Java Virtual
     * Machine expend effort toward recycling unused objects in order to
     * make the memory they currently occupy available for quick reuse.
     * When control returns from the method call, the Java Virtual
     * Machine has made a best effort to reclaim space from all discarded
     * objects. This method calls Sys.gc() to perform GC.
     * </p>
     */
    public static void gc() {
        Sys.Memory.gc();
    }

    /**
     * Runs the finalization methods of any objects pending finalization.
     * <p>
     * Calling this method suggests that the Java Virtual Machine expend
     * effort toward running the <code>finalize</code> methods of objects
     * that have been found to be discarded but whose <code>finalize</code>
     * methods have not yet been run. When control returns from the
     * method call, the Java Virtual Machine has made a best effort to
     * complete all outstanding finalizations. This method calls
     * Sys.runFinalization() to run finalization.
     * </p>
     */
    public static void runFinalization() {
        Sys.Memory.runFinalization();
    }

    /**
     * Serialize a given object into the given file.
     * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
     * directory is created. Under that directory, a file of given
     * fileName is created.
     *
     * @param obj object that has to be serialized.
     * @param fileName name of the file to which the object is serialized.
     */
    public static void serialize(Serializable obj, String fileName) {
        Export.serialize(obj, fileName);
    }

    /**
     * Creates an XML document to persist the tree of the all
     * transitively reachable objects from given "root" object.
     */
    public static String toXML(Object obj) {
        return Export.toXML(obj);
    }

    /**
     * Writes an XML document to persist the tree of the all the
     * transitively reachable objects from the given "root" object.
     * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
     * directory is created. Under that directory, a file of the given
     * fileName is created.
     */
    public static void writeXML(Object obj, String fileName) {
        Export.writeXML(obj, fileName);
    }

    /**
     * Writes a .dot document to persist the tree of the all the
     * transitively reachable objects from the given "root" object.
     * .dot documents can be viewed by Graphviz application (www.graphviz.org)
     * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
     * directory is created. Under that directory, a file of the given
     * fileName is created.
     * @since 1.1
     */
    public static void writeDOT(Object obj, String fileName) {
        Export.writeDOT(obj, fileName);
    }

    // speculative buffer management
    /**
     * Returns an identifier for a new speculative buffer.
     *
     * @return new speculative buffer id
     */
    public static int speculation() {
        return Speculation.speculation();
    }

    /**
     * Sets current speculative buffer id.
     *
     * @param id the speculative buffer id
     */
    public static void speculate(int id) {
        Speculation.speculate(id);
    }

    /**
     * Commits the speculative buffer associated with id.
     *
     * @param id the speculative buffer id
     */
    public static void commit(int id) {
        Speculation.commit(id);
    }

    /**
     * Discards the speculative buffer associated with id.
     *
     * @param id the speculative buffer id
     */
    public static void discard(int id) {
        Speculation.discard(id);
    }

    // aggregation support
    /**
     * Creates a new aggregation based on the given aggregation function type.
     *
     * @param type the aggregating function to be performed on the data being added to the aggregation.
     */
    public static Aggregation newAggregation(AggregationFunction type) {
        return Aggregations.newAggregation(type);
    }

    /**
     * Creates a grouping aggregation key with the provided value. The value must be a String or Number type.
     *
     * @param element1 the value of the aggregation key
     */
    public static AggregationKey newAggregationKey(Object element1) {
        return Aggregations.newAggregationKey(element1);
    }

    /**
     * Creates a composite grouping aggregation key with the provided values. The values must be String or Number types.
     *
     * @param element1 the first element of the composite aggregation key
     * @param element2 the second element of the composite aggregation key
     */
    public static AggregationKey newAggregationKey(Object element1, Object element2) {
        return Aggregations.newAggregationKey(element1, element2);
    }

    /**
     * Creates a composite grouping aggregation key with the provided values. The values must be String or Number types.
     *
     * @param element1 the first element of the composite aggregation key
     * @param element2 the second element of the composite aggregation key
     * @param element3 the third element of the composite aggregation key
     */
    public static AggregationKey newAggregationKey(Object element1, Object element2, Object element3) {
        return Aggregations.newAggregationKey(element1, element2, element3);
    }

    /**
     * Creates a composite grouping aggregation key with the provided values. The values must be String or Number types.
     *
     * @param element1 the first element of the composite aggregation key
     * @param element2 the second element of the composite aggregation key
     * @param element3 the third element of the composite aggregation key
     * @param element4 the fourth element of the composite aggregation key
     */
    public static AggregationKey newAggregationKey(Object element1, Object element2, Object element3, Object element4) {
        return Aggregations.newAggregationKey(element1, element2, element3, element4);
    }

    /**
     * Adds a value to the aggregation with no grouping key. This method should be used when the aggregation
     * is to calculate only a single aggregated value.
     *
     * @param aggregation the aggregation to which the value should be added
     */
    public static void addToAggregation(Aggregation aggregation, long value) {
        Aggregations.addToAggregation(aggregation, value);
    }

    /**
     * Adds a value to the aggregation with a grouping key. This method should be used when the aggregation
     * should effectively perform a "group by" on the key value. The aggregation will calculate a separate
     * aggregated value for each unique aggregation key.
     *
     * @param aggregation the aggregation to which the value should be added
     * @param key the grouping aggregation key
     */
    public static void addToAggregation(Aggregation aggregation, AggregationKey key, long value) {
        Aggregations.addToAggregation(aggregation, key, value);
    }

    /**
     * Resets values within the aggregation to the default. This will affect all values within the aggregation
     * when multiple aggregation keys have been used.
     *
     * @param aggregation the aggregation to be cleared
     */
    public static void clearAggregation(Aggregation aggregation) {
        Aggregations.clearAggregation(aggregation);
    }

    /**
     * Removes all aggregated values from the aggregation except for the largest or smallest
     * <code>abs(count)</code> elements.
     *
     * <p>If <code>count</code> is positive, the largest aggregated values in the aggregation will be
     * preserved. If <code>count</code> is negative the smallest values will be preserved. If <code>count</code>
     * is zero then all elements will be removed.
     *
     * <p>Behavior is intended to be similar to the dtrace <code>trunc()</code> function.
     *
     * @param aggregation the aggregation to be truncated
     * @param count the number of elements to preserve. If negative, the smallest <code>abs(count)</code> elements are preserved.
     */
    public static void truncateAggregation(Aggregation aggregation, int count) {
        Aggregations.truncateAggregation(aggregation, count);
    }

    /**
     * Prints the aggregation.
     */
    public static void printAggregation(String name, Aggregation aggregation) {
        printAggregation(name, aggregation);
    }

    /**
     * Prints aggregation using the provided format
     * @param name The name of the aggregation to be used in the textual output
     * @param aggregation The aggregation to print
     * @param format The format to use. It mimics {@linkplain String#format(java.lang.String, java.lang.Object[]) } behaviour
     *               with the addition of the ability to address the key title as a 0-indexed item
     * @see String#format(java.lang.String, java.lang.Object[])
     * @since 1.1
     */
    public static void printAggregation(String name, Aggregation aggregation, String format) {
        printAggregation(name, aggregation, format);
    }

    /********** Namespaced methods ******************/

    /*
     * Wraps the threads related BTrace utility methods
     * @since 1.2
     */
    public static class Threads {
        // Thread and stack access

        /**
         * Tests whether this thread has been interrupted.  The <i>interrupted
         * status</i> of the thread is unaffected by this method.
         *
         * <p>A thread interruption ignored because a thread was not alive
         * at the time of the interrupt will be reflected by this method
         * returning false.
         *
         * @return <code>true</code> if this thread has been interrupted;
         *         <code>false</code> otherwise.
         */
        public static boolean isInteruppted() {
            return Thread.currentThread().isInterrupted();
        }

        /**
         * Prints the java stack trace of the current thread.
         */
        public static void jstack() {
            jstack(-1);
        }

        /**
         * Prints the java stack trace of the current thread. But,
         * atmost given number of frames.
         *
         * @param numFrames number of frames to be printed. When this is
         *        negative all frames are printed.
         */
        public static void jstack(int numFrames) {
            if (numFrames == 0) return;
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            // passing '5' to skip our own frames to generate stack trace
            BTraceRuntime.stackTrace(st, 5, numFrames);
        }

        /**
         * Prints Java stack traces of all the Java threads.
         */
        public static void jstackAll() {
            jstackAll(-1);
        }

        /**
         * Prints Java stack traces of all the Java threads. But,
         * atmost given number of frames.
         *
         * @param numFrames number of frames to be printed. When this is
         *        negative all frames are printed.
         */
        public static void jstackAll(int numFrames) {
            BTraceRuntime.stackTraceAll(numFrames);
        }

        /**
         * Returns the stack trace of current thread as a String.
         *
         * @return the stack trace as a String.
         */
        public static String jstackStr() {
            return jstackStr(-1);
        }

        /**
         * Returns the stack trace of the current thread as a String
         * but includes atmost the given number of frames.
         *
         * @param  numFrames number of frames to be included. When this is
         *         negative all frames are included.
         * @return the stack trace as a String.
         */
        public static String jstackStr(int numFrames) {
            if (numFrames == 0) {
                return "";
            }
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            // passing '5' to skip our own frames to generate stack trace
            return BTraceRuntime.stackTraceStr(st, 5, numFrames);
        }

        /**
         * Returns the stack traces of all Java threads as a String.
         *
         * @return the stack traces as a String.
         */
        public static String jstackAllStr() {
            return jstackAllStr(-1);
        }

        /**
         * Returns atmost given number of frames in stack traces
         * of all threads as a String.
         *
         * @param numFrames number of frames to be included. When this is
         *        negative all frames are included.
         * @return the stack traces as a String.
         */
        public static String jstackAllStr(int numFrames) {
            if (numFrames == 0) {
                return "";
            }
            return BTraceRuntime.stackTraceAllStr(numFrames);
        }

        /**
         * Prints the stack trace of the given exception object.
         *
         * @param exception throwable for which stack trace is printed.
         */
        public static void jstack(Throwable exception) {
            jstack(exception, -1);
        }

        /**
         * Prints the stack trace of the given exception object. But,
         * prints atmost given number of frames.
         *
         * @param exception throwable for which stack trace is printed.
         * @param numFrames maximum number of frames to be printed.
         */
        public static void jstack(Throwable exception, int numFrames) {
            if (numFrames == 0) {
                return;
            }
            StackTraceElement[] st = exception.getStackTrace();
            println(exception);
            BTraceRuntime.stackTrace("\t", st, 0, numFrames);
            Throwable cause = exception.getCause();
            while (cause != null) {
                println("Caused by:");
                st = cause.getStackTrace();
                BTraceRuntime.stackTrace("\t", st, 0, numFrames);
                cause = cause.getCause();
            }
        }

        /**
         * Returns the stack trace of given exception object as a String.
         *
         * @param exception the throwable for which stack trace is returned.
         */
        public static String jstackStr(Throwable exception) {
            return jstackStr(exception, -1);
        }

        /**
         * Returns stack trace of given exception object as a String.
         *
         * @param exception throwable for which stack trace is returned.
         * @param numFrames maximum number of frames to be returned.
         */
        public static String jstackStr(Throwable exception, int numFrames) {
            if (numFrames == 0) {
                return "";
            }
            StackTraceElement[] st = exception.getStackTrace();
            StringBuilder buf = new StringBuilder();
            buf.append(Strings.str(exception));
            buf.append(BTraceRuntime.stackTraceStr("\t", st, 0, numFrames));
            Throwable cause = exception.getCause();
            while (cause != null) {
                buf.append("Caused by:");
                st = cause.getStackTrace();
                buf.append(BTraceRuntime.stackTraceStr("\t", st, 0, numFrames));
                cause = cause.getCause();
            }
            return buf.toString();
        }

        /**
         * Returns a reference to the currently executing thread object.
         *
         * @return  the currently executing thread.
         */
        public static Thread currentThread() {
            return Thread.currentThread();
        }

        /**
         * Returns the identifier of the given Thread.  The thread ID is a positive
         * <tt>long</tt> number generated when the given thread was created.
         * The thread ID is unique and remains unchanged during its lifetime.
         * When a thread is terminated, the thread ID may be reused.
         */
        public static long threadId(Thread thread) {
            return thread.getId();
        }

        /**
         * Returns the state of the given thread.
         * This method is designed for use in monitoring of the system state,
         * not for synchronization control.
         */
        public static Thread.State threadState(Thread thread) {
            return thread.getState();
        }

        /**
         * Returns <tt>true</tt> if and only if the current thread holds the
         * monitor lock on the specified object.
         *
         * <p>This method is designed to allow a program to assert that
         * the current thread already holds a specified lock:
         * <pre>
         *     assert Thread.holdsLock(obj);
         * </pre>
         *
         * @param  obj the object on which to test lock ownership
         * @throws NullPointerException if obj is <tt>null</tt>
         * @return <tt>true</tt> if the current thread holds the monitor lock on
         *         the specified object.
         */
        public static boolean holdsLock(Object obj) {
            return Thread.holdsLock(obj);
        }

        /**
         * Prints the Java level deadlocks detected (if any).
         */
        public static void deadlocks() {
            deadlocks(true);
        }

        /**
         * Prints deadlocks detected (if any). Optionally prints
         * stack trace of the deadlocked threads.
         *
         * @param stackTrace boolean flag to specify whether to
         *        print stack traces of deadlocked threads or not.
         */
        public static void deadlocks(boolean stackTrace) {
            BTraceRuntime.deadlocks(stackTrace);
        }

        /**
         * Returns the name of the given thread.
         *
         * @param thread thread whose name is returned
         */
        public static String name(Thread thread) {
            return thread.getName();
        }
    }

    /*
     * Wraps the strings related BTrace utility methods
     * @since 1.2
     */
    public static class Strings {
        public static boolean startsWith(String s, String start) {
            return s.startsWith(start);
        }

        public static boolean endsWith(String s, String end) {
            return s.endsWith(end);
        }

        /**
         * This is synonym to "concat".
         *
         * @see #concat(String, String)
         */
        public static String strcat(String str1, String str2) {
            return concat(str1, str2);
        }

        /**
         * Concatenates the specified strings together.
         */
        public static String concat(String str1, String str2) {
            return str1.concat(str2);
        }

        /**
         * Compares two strings lexicographically.
         * The comparison is based on the Unicode value of each character in
         * the strings. The character sequence represented by the first
         * <code>String</code> object is compared lexicographically to the
         * character sequence represented by the second string. The result is
         * a negative integer if the first <code>String</code> object
         * lexicographically precedes the second string. The result is a
         * positive integer if the first <code>String</code> object lexicographically
         * follows the second string. The result is zero if the strings
         * are equal; <code>compareTo</code> returns <code>0</code> exactly when
         * the {@link String#equals(Object)} method would return <code>true</code>.
         */
        public static int compareTo(String str1, String str2) {
            return str1.compareTo(str2);
        }

        /**
         * This is synonym to "compareTo" method.
         *
         * @see #compareTo
         */
        public static int strcmp(String str1, String str2) {
            return str1.compareTo(str2);
        }

        /**
         * Compares two strings lexicographically, ignoring case
         * differences. This method returns an integer whose sign is that of
         * calling <code>compareTo</code> with normalized versions of the strings
         * where case differences have been eliminated by calling
         * <code>Character.toLowerCase(Character.toUpperCase(character))</code> on
         * each character.
         */
        public static int compareToIgnoreCase(String str1, String str2) {
            return str1.compareToIgnoreCase(str2);
        }

        /**
         * This is synonym to "compareToIgnoreCase".
         *
         * @see #compareToIgnoreCase
         */
        public static int stricmp(String str1, String str2) {
            return str1.compareToIgnoreCase(str2);
        }

        /**
         * Find String within String
         */
        public static int strstr(String str1, String str2) {
            return str1.indexOf(str2);
        }

        public static int indexOf(String str1, String str2) {
            return str1.indexOf(str2);
        }

        public static int lastIndexOf(String str1, String str2) {
            return str1.lastIndexOf(str2);
        }

        /**
         * Substring
         */
        public static String substr(String str,  int start, int length) {
            return str.substring(start, length);
        }
        public static String substr(String str, int start) {
            return str.substring(start);
        }

        /**
         * Returns the length of the given string.
         * The length is equal to the number of <a href="Character.html#unicode">Unicode
         * code units</a> in the string.
         *
         * @param str String whose length is calculated.
         * @return  the length of the sequence of characters represented by this
         *          object.
         */
        public static int length(String str) {
            return str.length();
        }

        /**
         * This is synonym for "length".
         *
         * @see #length(String)
         */
        public static int strlen(String str) {
            return str.length();
        }

        // regular expression matching

        /**
         * Compiles the given regular expression into a pattern.  </p>
         *
         * @param  regex
         *         The expression to be compiled
         *
         * @throws  PatternSyntaxException
         *          If the expression's syntax is invalid
         */
        public static Pattern regexp(String regex) {
            return Pattern.compile(regex);
        }

        /**
         * This is synonym for "regexp".
         *
         * @see #regexp(String)
         */
        public static Pattern pattern(String regex) {
            return regexp(regex);
        }

        /**
         * Compiles the given regular expression into a pattern with the given
         * flags.  </p>
         *
         * @param  regex
         *         The expression to be compiled
         *
         * @param  flags
         *         Match flags, a bit mask that may include
         *         {@link Pattern#CASE_INSENSITIVE}, {@link Pattern#MULTILINE}, {@link Pattern#DOTALL},
         *         {@link Pattern#UNICODE_CASE}, {@link Pattern#CANON_EQ}, {@link Pattern#UNIX_LINES},
         *         {@link Pattern#LITERAL} and {@link Pattern#COMMENTS}
         *
         * @throws  IllegalArgumentException
         *          If bit values other than those corresponding to the defined
         *          match flags are set in <tt>flags</tt>
         *
         * @throws  PatternSyntaxException
         *          If the expression's syntax is invalid
         */
        public static Pattern regexp(String regex, int flags) {
            return Pattern.compile(regex, flags);
        }

        /**
         * This is synonym for "regexp".
         *
         * @see #regexp(String, int)
         */
        public static Pattern pattern(String regex, int flags) {
            return regexp(regex, flags);
        }

        /**
         * Matches the given (precompiled) regular expression and attempts
         * to match the given input against it.
         */
        public static boolean matches(Pattern regex, String input) {
            return regex.matcher(input).matches();
        }

        /**
         * Compiles the given regular expression and attempts to match the given
         * input against it.
         *
         * <p> An invocation of this convenience method of the form
         *
         * <blockquote><pre>
         * Pattern.matches(regex, input);</pre></blockquote>
         *
         * behaves in exactly the same way as the expression
         *
         * <blockquote><pre>
         * Pattern.compile(regex).matcher(input).matches()</pre></blockquote>
         *
         * <p> If a pattern is to be used multiple times, compiling it once and reusing
         * it will be more efficient than invoking this method each time.  </p>
         *
         * @param  regex
         *         The expression to be compiled
         *
         * @param  input
         *         The character sequence to be matched
         *
         * @throws  PatternSyntaxException
         *          If the expression's syntax is invalid
         */
        public static boolean matches(String regex, String input) {
            return Pattern.matches(regex, input);
        }

        /**
         * Returns a <tt>String</tt> object representing the specified
         * boolean.  If the specified boolean is <code>true</code>, then
         * the string {@code "true"} will be returned, otherwise the
         * string {@code "false"} will be returned.
         *
         * @param b	the boolean to be converted
         * @return the string representation of the specified <code>boolean</code>
         */
        public static String str(boolean b) {
            return Boolean.toString(b);
        }

        /**
         * Returns a <code>String</code> object representing the
         * specified <code>char</code>.  The result is a string of length
         * 1 consisting solely of the specified <code>char</code>.
         *
         * @param c the <code>char</code> to be converted
         * @return the string representation of the specified <code>char</code>
         */
        public static String str(char c) {
            return Character.toString(c);
        }

        /**
         * Returns a <code>String</code> object representing the
         * specified integer. The argument is converted to signed decimal
         * representation and returned as a string.
         *
         * @param   i   an integer to be converted.
         * @return  a string representation of the argument in base&nbsp;10.
         */
        public static String str(int i) {
            return Integer.toString(i);
        }

        /**
         * Returns a string representation of the integer argument as an
         * unsigned integer in base&nbsp;16.
         * <p>
         * The unsigned integer value is the argument plus 2<sup>32</sup>
         * if the argument is negative; otherwise, it is equal to the
         * argument.  This value is converted to a string of ASCII digits
         * in hexadecimal (base&nbsp;16) with no extra leading
         * <code>0</code>s. If the unsigned magnitude is zero, it is
         * represented by a single zero character <code>'0'</code>
         * (<code>'&#92;u0030'</code>); otherwise, the first character of
         * the representation of the unsigned magnitude will not be the
         * zero character. The following characters are used as
         * hexadecimal digits:
         * <blockquote><pre>
         * 0123456789abcdef
         * </pre></blockquote>
         * These are the characters <code>'&#92;u0030'</code> through
         * <code>'&#92;u0039'</code> and <code>'&#92;u0061'</code> through
         * <code>'&#92;u0066'</code>.
         *
         * @param   i   an integer to be converted to a string.
         * @return  the string representation of the unsigned integer value
         *          represented by the argument in hexadecimal (base&nbsp;16).
         */
        public static String toHexString(int i) {
            return Integer.toHexString(i);
        }

        /**
         * Returns a <code>String</code> object representing the specified
         * <code>long</code>.  The argument is converted to signed decimal
         * representation and returned as a string.
         *
         * @param   l a <code>long</code> to be converted.
         * @return  a string representation of the argument in base&nbsp;10.
         */
        public static String str(long l) {
            return Long.toString(l);
        }

        /**
         * Returns a string representation of the object. In general, the
         * <code>toString</code> method returns a string that
         * "textually represents" this object. The result should
         * be a concise but informative representation that is easy for a
         * person to read. For bootstrap classes, returns the result of
         * calling Object.toString() override. For non-bootstrap classes,
         * default toString() value [className@hashCode] is returned.
         *
         * @param  obj the object whose string representation is returned
         * @return a string representation of the given object.
         */
        public static String str(Object obj) {
            if (obj == null) {
                return "null";
            } else if (obj instanceof String)	 {
                return (String) obj;
            } else if (obj.getClass().getClassLoader() == null) {
                return obj.toString();
            } else {
                return identityStr(obj);
            }
        }

        /**
         * Returns a string representation of the <code>long</code>
         * argument as an unsigned integer in base&nbsp;16.
         * <p>
         * The unsigned <code>long</code> value is the argument plus
         * 2<sup>64</sup> if the argument is negative; otherwise, it is
         * equal to the argument.  This value is converted to a string of
         * ASCII digits in hexadecimal (base&nbsp;16) with no extra
         * leading <code>0</code>s.  If the unsigned magnitude is zero, it
         * is represented by a single zero character <code>'0'</code>
         * (<code>'&#92;u0030'</code>); otherwise, the first character of
         * the representation of the unsigned magnitude will not be the
         * zero character. The following characters are used as
         * hexadecimal digits:
         * <blockquote><pre>
         * 0123456789abcdef
         * </pre></blockquote>
         * These are the characters <code>'&#92;u0030'</code> through
         * <code>'&#92;u0039'</code> and  <code>'&#92;u0061'</code> through
         * <code>'&#92;u0066'</code>.
         *
         * @param   l a <code>long</code> to be converted to a string.
         * @return  the string representation of the unsigned <code>long</code>
         * 		value represented by the argument in hexadecimal
         *		(base&nbsp;16).
         */
        public static String toHexString(long l) {
            return Long.toHexString(l);
        }

        /**
         * Returns a string representation of the <code>float</code>
         * argument. All characters mentioned below are ASCII characters.
         * <ul>
         * <li>If the argument is NaN, the result is the string
         * &quot;<code>NaN</code>&quot;.
         * <li>Otherwise, the result is a string that represents the sign and
         *     magnitude (absolute value) of the argument. If the sign is
         *     negative, the first character of the result is
         *     '<code>-</code>' (<code>'&#92;u002D'</code>); if the sign is
         *     positive, no sign character appears in the result. As for
         *     the magnitude <i>m</i>:
         * <ul>
         * <li>If <i>m</i> is infinity, it is represented by the characters
         *     <code>"Infinity"</code>; thus, positive infinity produces
         *     the result <code>"Infinity"</code> and negative infinity
         *     produces the result <code>"-Infinity"</code>.
         * <li>If <i>m</i> is zero, it is represented by the characters
         *     <code>"0.0"</code>; thus, negative zero produces the result
         *     <code>"-0.0"</code> and positive zero produces the result
         *     <code>"0.0"</code>.
         * <li> If <i>m</i> is greater than or equal to 10<sup>-3</sup> but
         *      less than 10<sup>7</sup>, then it is represented as the
         *      integer part of <i>m</i>, in decimal form with no leading
         *      zeroes, followed by '<code>.</code>'
         *      (<code>'&#92;u002E'</code>), followed by one or more
         *      decimal digits representing the fractional part of
         *      <i>m</i>.
         * <li> If <i>m</i> is less than 10<sup>-3</sup> or greater than or
         *      equal to 10<sup>7</sup>, then it is represented in
         *      so-called "computerized scientific notation." Let <i>n</i>
         *      be the unique integer such that 10<sup><i>n</i> </sup>&lt;=
         *      <i>m</i> &lt; 10<sup><i>n</i>+1</sup>; then let <i>a</i>
         *      be the mathematically exact quotient of <i>m</i> and
         *      10<sup><i>n</i></sup> so that 1 &lt;= <i>a</i> &lt; 10.
         *      The magnitude is then represented as the integer part of
         *      <i>a</i>, as a single decimal digit, followed by
         *      '<code>.</code>' (<code>'&#92;u002E'</code>), followed by
         *      decimal digits representing the fractional part of
         *      <i>a</i>, followed by the letter '<code>E</code>'
         *      (<code>'&#92;u0045'</code>), followed by a representation
         *      of <i>n</i> as a decimal integer, as produced by the
         *      method <code>{@link
         *      java.lang.Integer#toString(int)}</code>.
         * </ul>
         * </ul>
         * How many digits must be printed for the fractional part of
         * <i>m</i> or <i>a</i>? There must be at least one digit
         * to represent the fractional part, and beyond that as many, but
         * only as many, more digits as are needed to uniquely distinguish
         * the argument value from adjacent values of type
         * <code>float</code>. That is, suppose that <i>x</i> is the
         * exact mathematical value represented by the decimal
         * representation produced by this method for a finite nonzero
         * argument <i>f</i>. Then <i>f</i> must be the <code>float</code>
         * value nearest to <i>x</i>; or, if two <code>float</code> values are
         * equally close to <i>x</i>, then <i>f</i> must be one of
         * them and the least significant bit of the significand of
         * <i>f</i> must be <code>0</code>.
         * <p>
         *
         * @param   f   the float to be converted.
         * @return a string representation of the argument.
         */
        public static String str(float f) {
            return Float.toString(f);
        }

        /**
         * Returns a string representation of the <code>double</code>
         * argument. All characters mentioned below are ASCII characters.
         * <ul>
         * <li>If the argument is NaN, the result is the string
         *     &quot;<code>NaN</code>&quot;.
         * <li>Otherwise, the result is a string that represents the sign and
         * magnitude (absolute value) of the argument. If the sign is negative,
         * the first character of the result is '<code>-</code>'
         * (<code>'&#92;u002D'</code>); if the sign is positive, no sign character
         * appears in the result. As for the magnitude <i>m</i>:
         * <ul>
         * <li>If <i>m</i> is infinity, it is represented by the characters
         * <code>"Infinity"</code>; thus, positive infinity produces the result
         * <code>"Infinity"</code> and negative infinity produces the result
         * <code>"-Infinity"</code>.
         *
         * <li>If <i>m</i> is zero, it is represented by the characters
         * <code>"0.0"</code>; thus, negative zero produces the result
         * <code>"-0.0"</code> and positive zero produces the result
         * <code>"0.0"</code>.
         *
         * <li>If <i>m</i> is greater than or equal to 10<sup>-3</sup> but less
         * than 10<sup>7</sup>, then it is represented as the integer part of
         * <i>m</i>, in decimal form with no leading zeroes, followed by
         * '<code>.</code>' (<code>'&#92;u002E'</code>), followed by one or
         * more decimal digits representing the fractional part of <i>m</i>.
         *
         * <li>If <i>m</i> is less than 10<sup>-3</sup> or greater than or
         * equal to 10<sup>7</sup>, then it is represented in so-called
         * "computerized scientific notation." Let <i>n</i> be the unique
         * integer such that 10<sup><i>n</i></sup> &lt;= <i>m</i> &lt;
         * 10<sup><i>n</i>+1</sup>; then let <i>a</i> be the
         * mathematically exact quotient of <i>m</i> and
         * 10<sup><i>n</i></sup> so that 1 &lt;= <i>a</i> &lt; 10. The
         * magnitude is then represented as the integer part of <i>a</i>,
         * as a single decimal digit, followed by '<code>.</code>'
         * (<code>'&#92;u002E'</code>), followed by decimal digits
         * representing the fractional part of <i>a</i>, followed by the
         * letter '<code>E</code>' (<code>'&#92;u0045'</code>), followed
         * by a representation of <i>n</i> as a decimal integer, as
         * produced by the method {@link Integer#toString(int)}.
         * </ul>
         * </ul>
         * How many digits must be printed for the fractional part of
         * <i>m</i> or <i>a</i>? There must be at least one digit to represent
         * the fractional part, and beyond that as many, but only as many, more
         * digits as are needed to uniquely distinguish the argument value from
         * adjacent values of type <code>double</code>. That is, suppose that
         * <i>x</i> is the exact mathematical value represented by the decimal
         * representation produced by this method for a finite nonzero argument
         * <i>d</i>. Then <i>d</i> must be the <code>double</code> value nearest
         * to <i>x</i>; or if two <code>double</code> values are equally close
         * to <i>x</i>, then <i>d</i> must be one of them and the least
         * significant bit of the significand of <i>d</i> must be <code>0</code>.
         * <p>
         *
         * @param   d   the <code>double</code> to be converted.
         * @return a string representation of the argument.
         */
        public static String str(double d) {
            return Double.toString(d);
        }

        /**
         * Safely creates a new instance of an appendable string buffer <br>
         * @param threadSafe Specifies whether the buffer should be thread safe
         * @return Returns either {@linkplain StringBuilder} or {@linkplain StringBuffer}
         *         instance depending on whether the instance is required to be
         *         thread safe or not, respectively.
         * @since 1.2
         */
        public static Appendable newStringBuilder(boolean threadSafe) {
            return BTraceRuntime.newStringBuilder(threadSafe);
        }

        /**
         * Safely creates a new instance of an appendable string buffer <br>
         * The buffer will not be thread safe.
         * @return Returns a new instance of {@linkplain StringBuilder} class
         * @since 1.2
         */
        public static Appendable newStringBuilder() {
            return BTraceRuntime.newStringBuilder();
        }

        /**
         * Appends a string to an appendable buffer created by {@linkplain BTraceUtils.Strings#newStringBuilder()}
         * @param buffer The appendable buffer to append to
         * @param strToAppend The string to append
         * @return Returns the same appendable buffer instance
         * @since 1.2
         */
        public static Appendable append(Appendable buffer, String strToAppend) {
            return BTraceRuntime.append(buffer, strToAppend);

        }

        /**
         * Checks the length of an appendable buffer created by {@linkplain BTraceUtils.Strings#newStringBuilder()}
         * @param buffer The appendable buffer instance
         * @return Returns the length of the text contained by the buffer
         * @since 1.2
         */
        public static int length(Appendable buffer) {
            return BTraceRuntime.length(buffer);
        }
    }

    /*
     * Wraps the numbers related BTrace utility methods
     * @since 1.2
     */
    public static class Numbers {
        /**
         * Returns a <code>double</code> value with a positive sign, greater
         * than or equal to <code>0.0</code> and less than <code>1.0</code>.
         * Returned values are chosen pseudorandomly with (approximately)
         * uniform distribution from that range.
         */
        public static double random() {
            return Math.random();
        }

        /**
         * Returns the natural logarithm (base <i>e</i>) of a <code>double</code>
         * value.  Special cases:
         * <ul><li>If the argument is NaN or less than zero, then the result
         * is NaN.
         * <li>If the argument is positive infinity, then the result is
         * positive infinity.
         * <li>If the argument is positive zero or negative zero, then the
         * result is negative infinity.</ul>
         *
         * <p>The computed result must be within 1 ulp of the exact result.
         * Results must be semi-monotonic.
         *
         * @param   a   a value
         * @return  the value ln&nbsp;<code>a</code>, the natural logarithm of
         *          <code>a</code>.
         */
        public strictfp static double log(double a) {
            return Math.log(a);
        }

        /**
         * Returns the base 10 logarithm of a <code>double</code> value.
         * Special cases:
         *
         * <ul><li>If the argument is NaN or less than zero, then the result
         * is NaN.
         * <li>If the argument is positive infinity, then the result is
         * positive infinity.
         * <li>If the argument is positive zero or negative zero, then the
         * result is negative infinity.
         * <li> If the argument is equal to 10<sup><i>n</i></sup> for
         * integer <i>n</i>, then the result is <i>n</i>.
         * </ul>
         *
         * <p>The computed result must be within 1 ulp of the exact result.
         * Results must be semi-monotonic.
         *
         * @param   a   a value
         * @return  the base 10 logarithm of  <code>a</code>.
         */
        public strictfp static double log10(double a) {
            return Math.log10(a);
        }

        /**
         * Returns Euler's number <i>e</i> raised to the power of a
         * <code>double</code> value.  Special cases:
         * <ul><li>If the argument is NaN, the result is NaN.
         * <li>If the argument is positive infinity, then the result is
         * positive infinity.
         * <li>If the argument is negative infinity, then the result is
         * positive zero.</ul>
         *
         * <p>The computed result must be within 1 ulp of the exact result.
         * Results must be semi-monotonic.
         *
         * @param   a   the exponent to raise <i>e</i> to.
         * @return  the value <i>e</i><sup><code>a</code></sup>,
         *          where <i>e</i> is the base of the natural logarithms.
         */
        public strictfp static double exp(double a) {
            return Math.exp(a);
        }

        /**
         * Returns <code>true</code> if the specified number is a
         * Not-a-Number (NaN) value, <code>false</code> otherwise.
         *
         * @param   d  the value to be tested.
         * @return  <code>true</code> if the value of the argument is NaN;
         *          <code>false</code> otherwise.
         */
        public static boolean isNaN(double d) {
            return Double.isNaN(d);
        }

        /**
         * Returns <code>true</code> if the specified number is a
         * Not-a-Number (NaN) value, <code>false</code> otherwise.
         *
         * @param   f the value to be tested.
         * @return  <code>true</code> if the value of the argument is NaN;
         *          <code>false</code> otherwise.
         */
        public static boolean isNaN(float f) {
            return Float.isNaN(f);
        }

        /**
         * Returns <code>true</code> if the specified number is infinitely
         * large in magnitude, <code>false</code> otherwise.
         *
         * @param   d the value to be tested.
         * @return  <code>true</code> if the value of the argument is positive
         *          infinity or negative infinity; <code>false</code> otherwise.
         */
        public static boolean isInfinite(double d) {
            return Double.isInfinite(d);
        }

        /**
         * Returns <code>true</code> if the specified number is infinitely
         * large in magnitude, <code>false</code> otherwise.
         *
         * @param   f the value to be tested.
         * @return  <code>true</code> if the value of the argument is positive
         *          infinity or negative infinity; <code>false</code> otherwise.
         */
        public static boolean isInfinite(float f) {
            return Float.isInfinite(f);
        }

        // string parsing methods

        /**
         * Parses the string argument as a boolean.  The <code>boolean</code>
         * returned represents the value <code>true</code> if the string argument
         * is not <code>null</code> and is equal, ignoring case, to the string
         * {@code "true"}. <p>
         * Example: {@code Boolean.parseBoolean("True")} returns <tt>true</tt>.<br>
         * Example: {@code Boolean.parseBoolean("yes")} returns <tt>false</tt>.
         *
         * @param      s   the <code>String</code> containing the boolean
         *                 representation to be parsed
         * @return     the boolean represented by the string argument
         */
        public static boolean parseBoolean(String s) {
            return Boolean.parseBoolean(s);
        }

        /**
         * Parses the string argument as a signed decimal
         * <code>byte</code>. The characters in the string must all be
         * decimal digits, except that the first character may be an ASCII
         * minus sign <code>'-'</code> (<code>'&#92;u002D'</code>) to
         * indicate a negative value. The resulting <code>byte</code> value is
         * returned.
         *
         * @param s		a <code>String</code> containing the
         *                  <code>byte</code> representation to be parsed
         * @return 		the <code>byte</code> value represented by the
         *                  argument in decimal
         */
        public static byte parseByte(String s) {
            return Byte.parseByte(s);
        }

        /**
         * Parses the string argument as a signed decimal
         * <code>short</code>. The characters in the string must all be
         * decimal digits, except that the first character may be an ASCII
         * minus sign <code>'-'</code> (<code>'&#92;u002D'</code>) to
         * indicate a negative value. The resulting <code>short</code> value is
         * returned.
         *
         * @param s		a <code>String</code> containing the <code>short</code>
         *                  representation to be parsed
         * @return          the <code>short</code> value represented by the
         *                  argument in decimal.
         */
        public static short parseShort(String s) {
            return Short.parseShort(s);
        }

        /**
         * Parses the string argument as a signed decimal integer. The
         * characters in the string must all be decimal digits, except that
         * the first character may be an ASCII minus sign <code>'-'</code>
         * (<code>'&#92;u002D'</code>) to indicate a negative value. The resulting
         * integer value is returned.
         *
         * @param s	   a <code>String</code> containing the <code>int</code>
         *             representation to be parsed
         * @return     the integer value represented by the argument in decimal.
         */
        public static int parseInt(String s) {
            return Integer.parseInt(s);
        }

        /**
         * Parses the string argument as a signed decimal
         * <code>long</code>.  The characters in the string must all be
         * decimal digits, except that the first character may be an ASCII
         * minus sign <code>'-'</code> (<code>&#92;u002D'</code>) to
         * indicate a negative value. The resulting <code>long</code>
         * value is returned.
         * <p>
         * Note that neither the character <code>L</code>
         * (<code>'&#92;u004C'</code>) nor <code>l</code>
         * (<code>'&#92;u006C'</code>) is permitted to appear at the end
         * of the string as a type indicator, as would be permitted in
         * Java programming language source code.
         *
         * @param      s   a <code>String</code> containing the <code>long</code>
         *             representation to be parsed
         * @return     the <code>long</code> represented by the argument in
         *		   decimal.
         */
        public static long parseLong(String s) {
            return Long.parseLong(s);
        }

        /**
         * Returns a new <code>float</code> initialized to the value
         * represented by the specified <code>String</code>, as performed
         * by the <code>valueOf</code> method of class <code>Float</code>.
         *
         * @param      s   the string to be parsed.
         * @return the <code>float</code> value represented by the string
         *         argument.
         */
        public static float parseFloat(String s) {
            return Float.parseFloat(s);
        }

        /**
         * Returns a new <code>double</code> initialized to the value
         * represented by the specified <code>String</code>, as performed
         * by the <code>valueOf</code> methcod of class
         * <code>Double</code>.
         *
         * @param      s   the string to be parsed.
         * @return the <code>double</code> value represented by the string
         *         argument.
         */
        public static double parseDouble(String s) {
            return Double.parseDouble(s);
        }

        // boxing methods

        /**
         * Returns a <tt>Boolean</tt> instance representing the specified
         * <tt>boolean</tt> value.  If the specified <tt>boolean</tt> value
         * is <tt>true</tt>, this method returns <tt>Boolean.TRUE</tt>;
         * if it is <tt>false</tt>, this method returns <tt>Boolean.FALSE</tt>.
         *
         * @param  b a boolean value.
         * @return a <tt>Boolean</tt> instance representing <tt>b</tt>.
         */
        public static Boolean box(boolean b) {
            return Boolean.valueOf(b);
        }

        /**
         * Returns a <tt>Character</tt> instance representing the specified
         * <tt>char</tt> value.
         *
         * @param  c a char value.
         * @return a <tt>Character</tt> instance representing <tt>c</tt>.
         */
        public static Character box(char c) {
            return Character.valueOf(c);
        }

        /**
         * Returns a <tt>Byte</tt> instance representing the specified
         * <tt>byte</tt> value.
         *
         * @param  b a byte value.
         * @return a <tt>Byte</tt> instance representing <tt>b</tt>.
         */
        public static Byte box(byte b) {
            return Byte.valueOf(b);
        }

        /**
         * Returns a <tt>Short</tt> instance representing the specified
         * <tt>short</tt> value.
         *
         * @param  s a short value.
         * @return a <tt>Short</tt> instance representing <tt>s</tt>.
         */
        public static Short box(short s) {
            return Short.valueOf(s);
        }

        /**
         * Returns a <tt>Integer</tt> instance representing the specified
         * <tt>int</tt> value.
         *
         * @param  i an <code>int</code> value.
         * @return a <tt>Integer</tt> instance representing <tt>i</tt>.
         */
        public static Integer box(int i) {
            return Integer.valueOf(i);
        }

        /**
         * Returns a <tt>Long</tt> instance representing the specified
         * <tt>long</tt> value.
         *
         * @param  l a long value.
         * @return a <tt>Long</tt> instance representing <tt>l</tt>.
         */
        public static Long box(long l) {
            return Long.valueOf(l);
        }

        /**
         * Returns a <tt>Float</tt> instance representing the specified
         * <tt>float</tt> value.
         *
         * @param  f a float value.
         * @return a <tt>Float</tt> instance representing <tt>f</tt>.
         */
        public static Float box(float f) {
            return Float.valueOf(f);
        }

        /**
         * Returns a <tt>Double</tt> instance representing the specified
         * <tt>double</tt> value.
         *
         * @param  d a double value.
         * @return a <tt>Double</tt> instance representing <tt>d</tt>.
         */
        public static Double box(double d) {
            return Double.valueOf(d);
        }

        // unboxing methods

        /**
         * Returns the value of the given <tt>Boolean</tt> object as a boolean
         * primitive.
         *
         * @param b the Boolean object whose value is returned.
         * @return  the primitive <code>boolean</code> value of the object.
         */
        public static boolean unbox(Boolean b) {
            return b.booleanValue();
        }

        /**
         * Returns the value of the given <tt>Character</tt> object as a char
         * primitive.
         *
         * @param ch the Character object whose value is returned.
         * @return  the primitive <code>char</code> value of the object.
         */
        public static char unbox(Character ch) {
            return ch.charValue();
        }

        /**
         * Returns the value of the specified Byte as a <code>byte</code>.
         *
         * @param b Byte that is unboxed
         * @return  the byte value represented by the <code>Byte</code>.
         */
        public static byte unbox(Byte b) {
            return b.byteValue();
        }

        /**
         * Returns the short value represented by <code>Short</code>.
         *
         * @param s Short that is unboxed.
         * @return  the short value represented by the <code>Short</code>.
         */
        public static short unbox(Short s) {
            return s.shortValue();
        }

        /**
         * Returns the value of represented by <code>Integer</code>.
         *
         * @param i Integer that is unboxed.
         * @return  the int value represented by the <code>Integer</code>.
         */
        public static int unbox(Integer i) {
            return i.intValue();
        }

        /**
         * Returns the long value represented by the specified <code>Long</code>.
         *
         * @param l Long to be unboxed.
         * @return  the long value represented by the <code>Long</code>.
         */
        public static long unbox(Long l) {
            return l.longValue();
        }

        /**
         * Returns the float value represented by the specified <code>Float</code>.
         *
         * @param f Float to be unboxed.
         * @return  the float value represented by the <code>Float</code>.
         */
        public static float unbox(Float f) {
            return f.floatValue();
        }

        /**
         * Returns the double value represented by the specified <code>Double</code>.
         *
         * @param d Double to be unboxed.
         */
        public static double unbox(Double d) {
            return d.doubleValue();
        }
    }

    /*
     * Wraps the time related BTrace utility methods
     * @since 1.2
     */
    public static class Time {
        /**
         * Returns the current time in milliseconds.  Note that
         * while the unit of time of the return value is a millisecond,
         * the granularity of the value depends on the underlying
         * operating system and may be larger.  For example, many
         * operating systems measure time in units of tens of
         * milliseconds.
         *
         * @return  the difference, measured in milliseconds, between
         *          the current time and midnight, January 1, 1970 UTC.
         */
        public static long millis() {
            return java.lang.System.currentTimeMillis();
        }

        /**
         * Returns the current value of the most precise available system
         * timer, in nanoseconds.
         *
         * <p>This method can only be used to measure elapsed time and is
         * not related to any other notion of system or wall-clock time.
         * The value returned represents nanoseconds since some fixed but
         * arbitrary time (perhaps in the future, so values may be
         * negative).  This method provides nanosecond precision, but not
         * necessarily nanosecond accuracy. No guarantees are made about
         * how frequently values change. Differences in successive calls
         * that span greater than approximately 292 years (2<sup>63</sup>
         * nanoseconds) will not accurately compute elapsed time due to
         * numerical overflow.
         *
         * @return The current value of the system timer, in nanoseconds.
         */
        public static long nanos() {
            return java.lang.System.nanoTime();
        }

        /**
         * <p>Generates a string timestamp (current date&time)
         * @param format The format to be used - see {@linkplain SimpleDateFormat}
         * @return Returns a string representing current date&time
         * @since 1.1
         */
        public static String timestamp(String format) {
            return new SimpleDateFormat(format).format(Calendar.getInstance().getTime());
        }

        /**
         * <p>Generates a string timestamp (current date&time) in the default system format
         * @return Returns a string representing current date&time
         * @since 1.1
         */
        public static String timestamp() {
            return new SimpleDateFormat().format(Calendar.getInstance().getTime());
        }
    }

    /*
     * Wraps the collections related BTrace utility methods
     * @since 1.2
     */
    public static class Collections {
//        public static <T> void forEach(Collection<T> coll, String itemLambda) {
//            for(T item : coll) {
//                BTraceRuntime.lambda(itemLambda, item);
//            }
//        }

        // Create a new map
        public static <K, V> Map<K, V> newHashMap() {
            return BTraceRuntime.newHashMap();
        }

        public static <K, V> Map<K, V> newWeakMap() {
            return BTraceRuntime.newWeakMap();
        }

        public static <V> Deque<V> newDeque() {
            return BTraceRuntime.newDeque();
        }
        
        // get a particular item from a Map
        public static <K, V> V get(Map<K, V> map, Object key) {
            return BTraceRuntime.get(map, key);
        }

        // check whether an item exists
        public static <K, V> boolean containsKey(Map<K, V> map, Object key) {
            return BTraceRuntime.containsKey(map, key);
        }

        public static <K, V> boolean containsValue(Map<K, V> map, Object value) {
            return BTraceRuntime.containsKey(map, value);
        }

        // put a particular item into a Map
        public static <K, V> V put(Map<K, V> map, K key, V value) {
            return BTraceRuntime.put(map, key, value);
        }

        // remove a particular item from a Map
        public static <K, V> V remove(Map<K, V> map, Object key) {
            return BTraceRuntime.remove(map, key);
        }

        // clear all items from a Map
        public static <K, V> void clear(Map<K, V> map) {
            BTraceRuntime.clear(map);
        }

        // return the size of a Map
        public static <K, V> int size(Map<K, V> map) {
            return BTraceRuntime.size(map);
        }

        public static <K, V> boolean isEmpty(Map<K, V> map) {
            return BTraceRuntime.isEmpty(map);
        }

        // operations on collections
        public static <E> int size(Collection<E> coll) {
           return BTraceRuntime.size(coll);
        }

        public static <E> boolean isEmpty(Collection<E> coll) {
            return BTraceRuntime.isEmpty(coll);
        }

        public static <E> boolean contains(Collection<E> coll, Object obj) {
            return BTraceRuntime.contains(coll, obj);
        }

        public static boolean contains(Object[] array, Object value) {
            for (Object each : array) {
                if (compare(each, value)) {
                    return true;
                }
            }
            return false;
        }

        // operations on Deque
        public static <V> void push(Deque<V> queue, V value) {
            BTraceRuntime.push(queue, value);
        }

        public static <V> V poll(Deque<V> queue) {
            return BTraceRuntime.poll(queue);
        }

        public static <V> V peek(Deque<V> queue) {
            return BTraceRuntime.peek(queue);
        }

        public static <V> void addLast(Deque<V> queue, V value) {
            BTraceRuntime.addLast(queue, value);
        }

        public static <V> V peekFirst(Deque<V> queue) {
            return BTraceRuntime.peekFirst(queue);
        }

        public static <V> V peekLast(Deque<V> queue) {
            return BTraceRuntime.peekLast(queue);
        }

        public static <V> V removeLast(Deque<V> queue) {
            return BTraceRuntime.removeLast(queue);
        }

        public static <V> V removeFirst(Deque<V> queue) {
            return BTraceRuntime.removeFirst(queue);
        }
    }

    /*
     * Wraps the atomicity related BTrace utility methods
     * @since 1.2
     */
    public static class Atomic {
        /**
         * Creates a new AtomicInteger with the given initial value.
         *
         * @param initialValue the initial value
         */
        public static AtomicInteger newAtomicInteger(int initialValue) {
            return BTraceRuntime.newAtomicInteger(initialValue);
        }

        /**
         * Gets the current value of the given AtomicInteger.
         *
         * @param ai AtomicInteger whose value is returned.
         * @return the current value
         */
        public static int get(AtomicInteger ai) {
            return BTraceRuntime.get(ai);

        }

        /**
         * Sets to the given value to the given AtomicInteger.
         *
         * @param ai AtomicInteger whose value is set.
         * @param newValue the new value
         */
        public static void set(AtomicInteger ai, int newValue) {
            BTraceRuntime.set(ai, newValue);
        }

        /**
         * Eventually sets to the given value to the given AtomicInteger.
         *
         * @param ai AtomicInteger whose value is lazily set.
         * @param newValue the new value
         */
        public static void lazySet(AtomicInteger ai, int newValue) {
            BTraceRuntime.lazySet(ai, newValue);
        }

        /**
         * Atomically sets the value of given AtomitInteger to the given
         * updated value if the current value {@code ==} the expected value.
         *
         * @param ai AtomicInteger whose value is compared and set.
         * @param expect the expected value
         * @param update the new value
         * @return true if successful. False return indicates that
         * the actual value was not equal to the expected value.
         */
        public static boolean compareAndSet(AtomicInteger ai, int expect, int update) {
            return BTraceRuntime.compareAndSet(ai, expect, update);
        }

        /**
         * Atomically sets the value to the given updated value
         * if the current value {@code ==} the expected value.
         *
         * <p>May <a href="package-summary.html#Spurious">fail spuriously</a>
         * and does not provide ordering guarantees, so is only rarely an
         * appropriate alternative to {@code compareAndSet}.
         *
         * @param ai AtomicInteger whose value is weakly compared and set.
         * @param expect the expected value
         * @param update the new value
         * @return true if successful.
         */
        public static boolean weakCompareAndSet(AtomicInteger ai, int expect, int update) {
            return BTraceRuntime.weakCompareAndSet(ai, expect, update);
        }

        /**
         * Atomically increments by one the current value of given AtomicInteger.
         *
         * @param ai AtomicInteger that is incremented.
         * @return the previous value
         */

        public static int getAndIncrement(AtomicInteger ai) {
            return BTraceRuntime.getAndIncrement(ai);
        }


        /**
         * Atomically decrements by one the current value of given AtomicInteger.
         *
         * @param ai AtomicInteger that is decremented.
         * @return the previous value
         */
        public static int getAndDecrement(AtomicInteger ai) {
            return BTraceRuntime.getAndDecrement(ai);
        }


        /**
         * Atomically increments by one the current value of given AtomicInteger.
         *
         * @param ai AtomicInteger that is incremented.
         * @return the updated value
         */
        public static int incrementAndGet(AtomicInteger ai) {
            return BTraceRuntime.incrementAndGet(ai);
        }

        /**
         * Atomically decrements by one the current value of given AtomicInteger.
         *
         * @param ai AtomicInteger whose value is decremented.
         * @return the updated value
         */
        public static int decrementAndGet(AtomicInteger ai) {
            return BTraceRuntime.decrementAndGet(ai);
        }

        /**
         * Atomically adds the given value to the current value.
         *
         * @param ai AtomicInteger whose value is added to.
         * @param delta the value to add
         * @return the previous value
         */
        public static int getAndAdd(AtomicInteger ai, int delta) {
            return BTraceRuntime.getAndAdd(ai, delta);
        }

        /**
         * Atomically adds the given value to the current value.
         *
         * @param ai AtomicInteger whose value is added to.
         * @param delta the value to add
         * @return the updated value
         */
        public static int addAndGet(AtomicInteger ai, int delta) {
            return BTraceRuntime.addAndGet(ai, delta);
        }

        /**
         * Atomically sets to the given value and returns the old value.
         *
         * @param ai AtomicInteger whose value is set.
         * @param newValue the new value
         * @return the previous value
         */
        public static int getAndSet(AtomicInteger ai, int newValue) {
            return BTraceRuntime.getAndSet(ai, newValue);
        }

        /**
         * Creates a new AtomicLong with the given initial value.
         *
         * @param initialValue the initial value
         */
        public static AtomicLong newAtomicLong(long initialValue) {
            return BTraceRuntime.newAtomicLong(initialValue);
        }


        /**
         * Gets the current value the given AtomicLong.
         *
         * @param al AtomicLong whose value is returned.
         * @return the current value
         */

        public static long get(AtomicLong al) {
            return BTraceRuntime.get(al);
        }

        /**
         * Sets to the given value.
         *
         * @param al AtomicLong whose value is set.
         * @param newValue the new value
         */
        public static void set(AtomicLong al, long newValue) {
            BTraceRuntime.set(al, newValue);
        }

        /**
         * Eventually sets to the given value to the given AtomicLong.
         *
         * @param al AtomicLong whose value is set.
         * @param newValue the new value
         */
        public static void lazySet(AtomicLong al, long newValue) {
            BTraceRuntime.lazySet(al, newValue);
        }

        /**
         * Atomically sets the value to the given updated value
         * if the current value {@code ==} the expected value.
         *
         * @param al AtomicLong whose value is compared and set.
         * @param expect the expected value
         * @param update the new value
         * @return true if successful. False return indicates that
         * the actual value was not equal to the expected value.
         */
        public static boolean compareAndSet(AtomicLong al, long expect, long update) {
            return BTraceRuntime.compareAndSet(al, expect, update);
        }

        /**
         * Atomically sets the value to the given updated value
         * if the current value {@code ==} the expected value.
         *
         * <p>May fail spuriously
         * and does not provide ordering guarantees, so is only rarely an
         * appropriate alternative to {@code compareAndSet}.
         *
         * @param al AtomicLong whose value is compared and set.
         * @param expect the expected value
         * @param update the new value
         * @return true if successful.
         */
        public static boolean weakCompareAndSet(AtomicLong al, long expect, long update) {
            return BTraceRuntime.weakCompareAndSet(al, expect, update);
        }

        /**
         * Atomically increments by one the current value.
         *
         * @param al AtomicLong whose value is incremented.
         * @return the previous value
         */
        public static long getAndIncrement(AtomicLong al) {
            return BTraceRuntime.getAndIncrement(al);
        }

        /**
         * Atomically decrements by one the current value.
         *
         * @param al AtomicLong whose value is decremented.
         * @return the previous value
         */
        public static long getAndDecrement(AtomicLong al) {
            return BTraceRuntime.getAndDecrement(al);
        }


        /**
         * Atomically increments by one the current value.
         *
         * @param al AtomicLong whose value is incremented.
         * @return the updated value
         */
        public static long incrementAndGet(AtomicLong al) {
            return BTraceRuntime.incrementAndGet(al);
        }

        /**
         * Atomically decrements by one the current value.
         *
         * @param al AtomicLong whose value is decremented.
         * @return the updated value
         */
        public static long decrementAndGet(AtomicLong al) {
            return BTraceRuntime.decrementAndGet(al);
        }

        /**
         * Atomically adds the given value to the current value.
         *
         * @param al AtomicLong whose value is added to.
         * @param delta the value to add
         * @return the previous value
         */
        public static long getAndAdd(AtomicLong al, long delta) {
            return BTraceRuntime.getAndAdd(al, delta);
        }

        /**
         * Atomically adds the given value to the current value.
         *
         * @param al AtomicLong whose value is added to
         * @param delta the value to add
         * @return the updated value
         */
        public static long addAndGet(AtomicLong al, long delta) {
            return BTraceRuntime.addAndGet(al, delta);
        }

        /**
         * Atomically sets to the given value and returns the old value.
         *
         * @param al AtomicLong that is set.
         * @param newValue the new value
         * @return the previous value
         */
        public static long getAndSet(AtomicLong al, long newValue) {
            return BTraceRuntime.getAndSet(al, newValue);
        }
    }

    /*
     * Wraps the aggregations related BTrace utility methods
     * @since 1.2
     */
    public static class Aggregations {
        /**
         * Creates a new aggregation based on the given aggregation function type.
         *
         * @param type the aggregating function to be performed on the data being added to the aggregation.
         */
        public static Aggregation newAggregation(AggregationFunction type) {
            return BTraceRuntime.newAggregation(type);
        }

        /**
         * Creates a grouping aggregation key with the provided value. The value must be a String or Number type.
         *
         * @param element1 the value of the aggregation key
         */
        public static AggregationKey newAggregationKey(Object element1) {
            return BTraceRuntime.newAggregationKey(element1);
        }

        /**
         * Creates a composite grouping aggregation key with the provided values. The values must be String or Number types.
         *
         * @param element1 the first element of the composite aggregation key
         * @param element2 the second element of the composite aggregation key
         */
        public static AggregationKey newAggregationKey(Object element1, Object element2) {
            return BTraceRuntime.newAggregationKey(element1, element2);
        }

        /**
         * Creates a composite grouping aggregation key with the provided values. The values must be String or Number types.
         *
         * @param element1 the first element of the composite aggregation key
         * @param element2 the second element of the composite aggregation key
         * @param element3 the third element of the composite aggregation key
         */
        public static AggregationKey newAggregationKey(Object element1, Object element2, Object element3) {
            return BTraceRuntime.newAggregationKey(element1, element2, element3);
        }

        /**
         * Creates a composite grouping aggregation key with the provided values. The values must be String or Number types.
         *
         * @param element1 the first element of the composite aggregation key
         * @param element2 the second element of the composite aggregation key
         * @param element3 the third element of the composite aggregation key
         * @param element4 the fourth element of the composite aggregation key
         */
        public static AggregationKey newAggregationKey(Object element1, Object element2, Object element3, Object element4) {
            return BTraceRuntime.newAggregationKey(element1, element2, element3, element4);
        }

        /**
         * Adds a value to the aggregation with no grouping key. This method should be used when the aggregation
         * is to calculate only a single aggregated value.
         *
         * @param aggregation the aggregation to which the value should be added
         */
        public static void addToAggregation(Aggregation aggregation, long value) {
            BTraceRuntime.addToAggregation(aggregation, value);
        }

        /**
         * Adds a value to the aggregation with a grouping key. This method should be used when the aggregation
         * should effectively perform a "group by" on the key value. The aggregation will calculate a separate
         * aggregated value for each unique aggregation key.
         *
         * @param aggregation the aggregation to which the value should be added
         * @param key the grouping aggregation key
         */
        public static void addToAggregation(Aggregation aggregation, AggregationKey key, long value) {
            BTraceRuntime.addToAggregation(aggregation, key, value);
        }

        /**
         * Resets values within the aggregation to the default. This will affect all values within the aggregation
         * when multiple aggregation keys have been used.
         *
         * @param aggregation the aggregation to be cleared
         */
        public static void clearAggregation(Aggregation aggregation) {
            BTraceRuntime.clearAggregation(aggregation);
        }

        /**
         * Removes all aggregated values from the aggregation except for the largest or smallest
         * <code>abs(count)</code> elements.
         *
         * <p>If <code>count</code> is positive, the largest aggregated values in the aggregation will be
         * preserved. If <code>count</code> is negative the smallest values will be preserved. If <code>count</code>
         * is zero then all elements will be removed.
         *
         * <p>Behavior is intended to be similar to the dtrace <code>trunc()</code> function.
         *
         * @param aggregation the aggregation to be truncated
         * @param count the number of elements to preserve. If negative, the smallest <code>abs(count)</code> elements are preserved.
         */
        public static void truncateAggregation(Aggregation aggregation, int count) {
            BTraceRuntime.truncateAggregation(aggregation, count);
        }
    }

    /*
     * Wraps the speculation related BTrace utility methods
     * @since 1.2
     */
    public static class Speculation {
        /**
         * Returns an identifier for a new speculative buffer.
         *
         * @return new speculative buffer id
         */
        public static int speculation() {
            return BTraceRuntime.speculation();
        }

        /**
         * Sets current speculative buffer id.
         *
         * @param id the speculative buffer id
         */
        public static void speculate(int id) {
            BTraceRuntime.speculate(id);
        }

        /**
         * Commits the speculative buffer associated with id.
         *
         * @param id the speculative buffer id
         */
        public static void commit(int id) {
            BTraceRuntime.commit(id);
        }

        /**
         * Discards the speculative buffer associated with id.
         *
         * @param id the speculative buffer id
         */
        public static void discard(int id) {
            BTraceRuntime.discard(id);
        }
    }

    /*
     * Wraps the references related BTrace utility methods
     * @since 1.2
     */
    public static class References {
        /**
         * Creates and returns a weak reference to the given object.
         *
         * @param obj object for which a weak reference is created.
         * @return a weak reference to the given object.
         */
        public static WeakReference weakRef(Object obj) {
            return new WeakReference<Object>(obj);
        }

        /**
         * Creates and returns a soft reference to the given object.
         *
         * @param obj object for which a soft reference is created.
         * @return a soft reference to the given object.
         */
        public static SoftReference softRef(Object obj) {
            return new SoftReference<Object>(obj);
        }

        /**
         * Returns the given reference object's referent.  If the reference object has
         * been cleared, either by the program or by the garbage collector, then
         * this method returns <code>null</code>.
         *
         * @param ref reference object whose referent is returned.
         * @return	 The object to which the reference refers, or
         *		 <code>null</code> if the reference object has been cleared.
         */
        public static Object deref(Reference ref) {
            if (ref.getClass().getClassLoader() == null) {
                return ref.get();
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    /*
     * Wraps the reflection related BTrace utility methods
     * @since 1.2
     */
    public static class Reflective {
        /**
         * Returns the runtime class of the given Object.
         *
         * @param  obj the Object whose Class is returned
         * @return the Class object of given object
         */
        public static Class classOf(Object obj) {
            return obj.getClass();
        }

        /**
         * Returns the Class object representing the class or interface
         * that declares the field represented by the given Field object.

         * @param field whose declaring Class is returned
         */
        public static Class declaringClass(Field field) {
            return field.getDeclaringClass();
        }

        /**
         * Returns the name of the given Class object.
         */
        public static String name(Class clazz) {
            return clazz.getName();
        }

        /**
         * Returns the name of the Field object.
         *
         * @param  field Field for which name is returned
         * @return name of the given field
         */
        public static String name(Field field) {
            return field.getName();
        }

        /**
         * Returns the type of the Field object.
         *
         * @param  field Field for which type is returned
         * @return type of the given field
         */
        public static Class type(Field field) {
            return field.getType();
        }

        /**
         * Returns the access flags of the given Class.
         */
        public static int accessFlags(Class clazz) {
            return clazz.getModifiers();
        }

        /**
         * Returns the access flags of the given Field.
         */
        public static int accessFlags(Field field) {
            return field.getModifiers();
        }

        /**
         * Returns the current context class loader
         */
        public static ClassLoader contextClassLoader() {
            return Thread.currentThread().getContextClassLoader();
        }

        // get Class of the given name

        /**
         * Returns Class object for given class name.
         */
        public static Class classForName(String name) {
            ClassLoader callerLoader = Reflection.getCallerClass(STACK_DEC).getClassLoader();
            return classForName(name, callerLoader);
        }

        /**
         * Returns the Class for the given class name
         * using the given class loader.
         */
        public static Class classForName(String name, ClassLoader cl) {
            try {
                    return Class.forName(name, false, cl);
            } catch (ClassNotFoundException exp) {
                throw translate(exp);
            }
        }

        /**
         * Determines if the class or interface represented by the first
         * <code>Class</code> object is either the same as, or is a superclass or
         * superinterface of, the class or interface represented by the second
         * <code>Class</code> parameter. It returns <code>true</code> if so;
         * otherwise it returns <code>false</code>.
         */
        public static boolean isAssignableFrom(Class<?> a, Class<?> b) {
            return a.isAssignableFrom(b);
        }


        /**
         * Determines if the specified <code>Object</code> is assignment-compatible
         * with the object represented by the specified <code>Class</code>. This method is
         * the dynamic equivalent of the Java language <code>instanceof</code>
         * operator. The method returns <code>true</code> if the specified
         * <code>Object</code> argument is non-null and can be cast to the
         * reference type represented by this <code>Class</code> object without
         * raising a <code>ClassCastException.</code> It returns <code>false</code>
         * otherwise.
         *
         * @param  clazz the class that is checked.
         * @param  obj the object to check.
         * @return  true if <code>obj</code> is an instance of the given class.
         */
        public static boolean isInstance(Class clazz, Object obj) {
            return clazz.isInstance(obj);
        }

        /**
         * Returns the <code>Class</code> representing the superclass of the entity
         * (class, interface, primitive type or void) represented by the given
         * <code>Class</code>.  If the given <code>Class</code> represents either the
         * <code>Object</code> class, an interface, a primitive type, or void, then
         * null is returned.  If the given object represents an array class then the
         * <code>Class</code> object representing the <code>Object</code> class is
         * returned.
         *
         * @param clazz the Class whose super class is returned.
         * @return the superclass of the class represented by the given object.
         */
        public static Class getSuperclass(Class clazz) {
            return clazz.getSuperclass();
        }

        /**
         * Determines if the specified <code>Class</code> object represents an
         * interface type.
         *
         * @param clazz the Class object to check.
         * @return  <code>true</code> if the Class represents an interface;
         *          <code>false</code> otherwise.
         */
        public static boolean isInterface(Class clazz) {
            return clazz.isInterface();
        }

        /**
         * Determines if the given <code>Class</code> object represents an array class.
         *
         * @param clazz Class object to check.
         * @return  <code>true</code> if the given object represents an array class;
         *          <code>false</code> otherwise.
         */
        public static boolean isArray(Class clazz) {
            return clazz.isArray();
        }

        /**
         * Returns whether the given Class represent primitive type or not.
         */
        public static boolean isPrimitive(Class clazz) {
            return clazz.isPrimitive();
        }

        /**
         * returns component type of an array Class.
         */
        public static Class getComponentType(Class clazz) {
            return clazz.getComponentType();
        }

        // Accessing fields by reflection

        /**
         * Returns a <code>Field</code> object that reflects the specified declared
         * field of the class or interface represented by the given <code>Class</code>
         * object. The <code>name</code> parameter is a <code>String</code> that
         * specifies the simple name of the desired field. Returns <code>null</code> on not finding
         * field if throwException parameter is <code>false</code>. Else throws a <code>RuntimeException</code>
         * when field is not found.
         *
         * @param clazz Class whose field is returned
         * @param name the name of the field
         * @param throwException whether to throw exception on failing to find field or not
         * @return the <code>Field</code> object for the specified field in this
         * class
         */
        public static Field field(Class clazz, String name, boolean throwException) {
            return getField(clazz, name, throwException);
        }

        /**
         * Returns a <code>Field</code> object that reflects the specified declared
         * field of the class or interface represented by the given <code>Class</code>
         * object. The <code>name</code> parameter is a <code>String</code> that
         * specifies the simple name of the desired field. Throws a <code>RuntimeException</code>
         * when field is not found.
         *
         * @param clazz Class whose field is returned
         * @param name the name of the field
         * @return the <code>Field</code> object for the specified field in this
         * class
         */
        public static Field field(Class clazz, String name) {
            return field(clazz, name, true);
        }

        /**
         * Returns a <code>Field</code> object that reflects the specified declared
         * field of the class or interface represented by the given <code>Class</code>
         * object. The <code>name</code> parameter is a <code>String</code> that
         * specifies the simple name of the desired field. Returns <code>null</code> on not finding
         * field if throwException parameter is <code>false</code>. Else throws a <code>RuntimeException</code>
         * when field is not found.
         *
         * @param clazz Class whose field is returned
         * @param name the name of the field
         * @param throwException whether to throw exception on failing to find field or not
         * @return the <code>Field</code> object for the specified field in this
         * class
         */
        public static Field field(String clazz, String name, boolean throwException) {
            ClassLoader callerLoader = Reflection.getCallerClass(STACK_DEC).getClassLoader();
            return field(classForName(clazz, callerLoader), name, throwException);
        }

        /**
         * Returns a <code>Field</code> object that reflects the specified declared
         * field of the class or interface represented by the given <code>Class</code>
         * object. The <code>name</code> parameter is a <code>String</code> that
         * specifies the simple name of the desired field. Throws a <code>RuntimeException</code>
         * when field is not found.
         *
         * @param clazz Class whose field is returned
         * @param name the name of the field
         * @return the <code>Field</code> object for the specified field in this
         * class
         */
        public static Field field(String clazz, String name) {
            ClassLoader callerLoader = Reflection.getCallerClass(STACK_DEC).getClassLoader();
            return field(classForName(clazz, callerLoader), name);
        }

        // field value get methods

        /**
         * Gets the value of a static <code>byte</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>byte</code> field
         */
        public static byte getByte(Field field) {
            checkStatic(field);
            try {
                return field.getByte(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>byte</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>byte</code> value
         * from
         * @return the value of the <code>byte</code> field
         */
        public static byte getByte(Field field, Object obj) {
            try {
                return field.getByte(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static <code>short</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>short</code> field
         */
        public static short getShort(Field field) {
            checkStatic(field);
            try {
                return field.getShort(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>short</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>short</code> value
         * from
         * @return the value of the <code>short</code> field
         */
        public static short getShort(Field field, Object obj) {
            try {
                return field.getShort(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static <code>int</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>int</code> field
         */
        public static int getInt(Field field) {
            checkStatic(field);
            try {
                return field.getInt(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>int</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>int</code> value
         * from
         * @return the value of the <code>int</code> field
         */
        public static int getInt(Field field, Object obj) {
            try {
                return field.getInt(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static <code>long</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>long</code> field
         */
        public static long getLong(Field field) {
            checkStatic(field);
            try {
                return field.getLong(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>long</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>long</code> value
         * from
         * @return the value of the <code>long</code> field
         */
        public static long getLong(Field field, Object obj) {
            try {
                return field.getLong(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static <code>float</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>float</code> field
         */
        public static float getFloat(Field field) {
            checkStatic(field);
            try {
                return field.getFloat(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>float</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>float</code> value
         * from
         * @return the value of the <code>float</code> field
         */
        public static float getFloat(Field field, Object obj) {
            try {
                return field.getFloat(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static <code>double</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>double</code> field
         */
        public static double getDouble(Field field) {
            checkStatic(field);
            try {
                return field.getDouble(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>double</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>double</code> value
         * from
         * @return the value of the <code>double</code> field
         */
        public static double getDouble(Field field, Object obj) {
            try {
                return field.getDouble(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static <code>boolean</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>boolean</code> field
         */
        public static boolean getBoolean(Field field) {
            checkStatic(field);
            try {
                return field.getBoolean(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>boolean</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>boolean</code> value
         * from
         * @return the value of the <code>boolean</code> field
         */
        public static boolean getBoolean(Field field, Object obj) {
            try {
                return field.getBoolean(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static <code>char</code> field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the <code>char</code> field
         */
        public static char getChar(Field field) {
            checkStatic(field);
            try {
                return field.getChar(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance <code>char</code> field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the <code>char</code> value
         * from
         * @return the value of the <code>char</code> field
         */
        public static char getChar(Field field, Object obj) {
            try {
                return field.getChar(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of a static reference field.
         *
         * @param field Field object whose value is returned.
         * @return the value of the reference field
         */
        public static Object get(Field field) {
            checkStatic(field);
            try {
                return field.get(null);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Gets the value of an instance reference field.
         *
         * @param field Field object whose value is returned.
         * @param obj the object to extract the reference value
         * from
         * @return the value of the reference field
         */
        public static Object get(Field field, Object obj) {
            try {
                return field.get(obj);
            } catch (Exception exp) {
                throw translate(exp);
            }
        }

        /**
         * Print all instance fields of an object as name-value
         * pairs. Includes the inherited fields as well.
         *
         * @param obj Object whose fields are printed.
         */
        public static void printFields(Object obj) {
            printFields(obj, false);
        }

        /**
         * Print all instance fields of an object as name-value
         * pairs. Includes the inherited fields as well. Optionally,
         * prints name of the declaring class before each field - so that
         * if same named field in super class chain may be disambiguated.
         *
         * @param obj Object whose fields are printed.
         * @param classNamePrefix flag to tell whether to prefix field names
         *        names by class name or not.
         */
        public static void printFields(Object obj, boolean classNamePrefix) {
            StringBuilder buf = new StringBuilder();
            buf.append('{');
            addFieldValues(buf, obj, obj.getClass(), classNamePrefix);
            buf.append('}');
            println(buf.toString());
        }

        /**
         * Print all static fields of the class as name-value
         * pairs. Includes the inherited fields as well.
         *
         * @param clazz Class whose static fields are printed.
         */
        public static void printStaticFields(Class clazz) {
            printStaticFields(clazz, false);
        }

        /**
         * Print all static fields of the class as name-value
         * pairs. Includes the inherited fields as well. Optionally,
         * prints name of the declaring class before each field - so that
         * if same named field in super class chain may be disambigated.
         *
         * @param clazz Class whose static fields are printed.
         * @param classNamePrefix flag to tell whether to prefix field names
         *        names by class name or not.
         */
        public static void printStaticFields(Class clazz, boolean classNamePrefix) {
            StringBuilder buf = new StringBuilder();
            buf.append('{');
            addStaticFieldValues(buf, clazz, classNamePrefix);
            buf.append('}');
            println(buf.toString());
        }
    }

    /*
     * Wraps the data export related BTrace utility methods
     * @since 1.2
     */
    public static class Export {
        /**
         * Serialize a given object into the given file.
         * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
         * directory is created. Under that directory, a file of given
         * fileName is created.
         *
         * @param obj object that has to be serialized.
         * @param fileName name of the file to which the object is serialized.
         */
        public static void serialize(Serializable obj, String fileName) {
            BTraceRuntime.serialize(obj, fileName);
        }

        /**
         * Creates an XML document to persist the tree of the all
         * transitively reachable objects from given "root" object.
         */
        public static String toXML(Object obj) {
            return BTraceRuntime.toXML(obj);
        }

        /**
         * Writes an XML document to persist the tree of the all the
         * transitively reachable objects from the given "root" object.
         * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
         * directory is created. Under that directory, a file of the given
         * fileName is created.
         */
        public static void writeXML(Object obj, String fileName) {
            BTraceRuntime.writeXML(obj, fileName);
        }

        /**
         * Writes a .dot document to persist the tree of the all the
         * transitively reachable objects from the given "root" object.
         * .dot documents can be viewed by Graphviz application (www.graphviz.org)
         * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
         * directory is created. Under that directory, a file of the given
         * fileName is created.
         * @since 1.1
         */
        public static void writeDOT(Object obj, String fileName) {
            BTraceRuntime.writeDOT(obj, fileName);
        }
    }

    /*
     * Wraps the OS related BTrace utility methods
     * @since 1.2
     */
    public static class Sys {
        /*
         * Wraps the environment related BTrace utility methods
         * @since 1.2
         */
        public static class Env {
            /**
             * Gets the system property indicated by the specified key.
             *
             * @param      key   the name of the system property.
             * @return     the string value of the system property,
             *             or <code>null</code> if there is no property with that key.
             *
             * @exception  NullPointerException if <code>key</code> is
             *             <code>null</code>.
             * @exception  IllegalArgumentException if <code>key</code> is empty.
             */
            public static String property(String key) {
                return BTraceRuntime.property(key);
            }

            /**
             * Returns all Sys properties.
             *
             * @return the system properties
             */
            public static Properties properties() {
                return BTraceRuntime.properties();
            }

            /**
             * Prints all Sys properties.
             */
            public static void printProperties() {
                BTraceRuntime.printMap(properties());
            }

            /**
             * Gets the value of the specified environment variable. An
             * environment variable is a system-dependent external named
             * value.
             *
             * @param  name the name of the environment variable
             * @return the string value of the variable, or <code>null</code>
             *         if the variable is not defined in the system environment
             * @throws NullPointerException if <code>name</code> is <code>null</code>
             */
            public static String getenv(String name) {
                return BTraceRuntime.getenv(name);
            }

            /**
             * Returns an unmodifiable string map view of the current system environment.
             * The environment is a system-dependent mapping from names to
             * values which is passed from parent to child processes.
             *
             * @return the environment as a map of variable names to values
             */
            public static Map<String, String> getenv() {
                return BTraceRuntime.getenv();
            }

            /**
             * Prints all system environment values.
             */
            public static void printEnv() {
                BTraceRuntime.printMap(getenv());
            }

            /**
             * Returns the number of processors available to the Java virtual machine.
             *
             * <p> This value may change during a particular invocation of the virtual
             * machine.  Applications that are sensitive to the number of available
             * processors should therefore occasionally poll this property and adjust
             * their resource usage appropriately. </p>
             *
             * @return  the maximum number of processors available to the virtual
             *          machine; never smaller than one
             */
            public static long availableProcessors() {
                return Runtime.getRuntime().availableProcessors();
            }
        }

        /*
         * Wraps the memory related BTrace utility methods
         * @since 1.2
         */
        public static class Memory {
            // memory usage
            /**
             * Returns the amount of free memory in the Java Virtual Machine.
             * Calling the
             * <code>gc</code> method may result in increasing the value returned
             * by <code>freeMemory.</code>
             *
             * @return  an approximation to the total amount of memory currently
             *          available for future allocated objects, measured in bytes.
             */
            public static long freeMemory() {
                return Runtime.getRuntime().freeMemory();
            }

            /**
             * Returns the total amount of memory in the Java virtual machine.
             * The value returned by this method may vary over time, depending on
             * the host environment.
             * <p>
             * Note that the amount of memory required to hold an object of any
             * given type may be implementation-dependent.
             *
             * @return  the total amount of memory currently available for current
             *          and future objects, measured in bytes.
             */
            public static long totalMemory() {
                return Runtime.getRuntime().totalMemory();
            }

            /**
             * Returns the maximum amount of memory that the Java virtual machine will
             * attempt to use.  If there is no inherent limit then the value {@link
             * java.lang.Long#MAX_VALUE} will be returned. </p>
             *
             * @return  the maximum amount of memory that the virtual machine will
             *          attempt to use, measured in bytes
             */
            public static long maxMemory() {
                return Runtime.getRuntime().maxMemory();
            }

            /**
             * Returns heap memory usage
             */
            public static MemoryUsage heapUsage() {
                return BTraceRuntime.heapUsage();
            }

            /**
             * Returns non-heap memory usage
             */
            public static MemoryUsage nonHeapUsage() {
                return BTraceRuntime.nonHeapUsage();
            }

            /**
             * Returns the amount of memory in bytes that the Java virtual
             * machine initially requests from the operating system for
             * memory management.
             */
            public static long init(MemoryUsage mu) {
                return mu.getInit();
            }

            /**
             * Returns the amount of memory in bytes that is committed for the Java
             * virtual machine to use. This amount of memory is guaranteed for the
             * Java virtual machine to use.
             */
            public static long committed(MemoryUsage mu) {
                return mu.getCommitted();
            }

            /**
             * Returns the maximum amount of memory in bytes that can be used
             * for memory management. This method returns -1 if the maximum memory
             * size is undefined.
             */
            public static long max(MemoryUsage mu) {
                return mu.getMax();
            }

            /**
             * Returns the amount of used memory in bytes.
             */
            public static long used(MemoryUsage mu) {
                return mu.getUsed();
            }

            /**
             * Returns the approximate number of objects for
             * which finalization is pending.
             */
            public static long finalizationCount() {
                return BTraceRuntime.finalizationCount();
            }

            /**
             * Dump the snapshot of the Java heap to a file in hprof
             * binary format. Only the live objects are dumped.
             * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
             * directory is created. Under that directory, a file of given
             * fileName is created.
             *
             * @param fileName name of the file to which heap is dumped
             */
            public static void dumpHeap(String fileName) {
                dumpHeap(fileName, true);
            }

            /**
             * Dump the snapshot of the Java heap to a file in hprof
             * binary format.
             * Under the current dir of traced app, ./btrace&lt;pid>/&lt;btrace-class>/
             * directory is created. Under that directory, a file of given
             * fileName is created.
             *
             * @param fileName name of the file to which heap is dumped
             * @param live flag that tells whether only live objects are
             *             to be dumped or all objects are to be dumped.
             */
            public static void dumpHeap(String fileName, boolean live) {
                BTraceRuntime.dumpHeap(fileName, live);
            }

            /**
             * Runs the garbage collector.
             * <p>
             * Calling the <code>gc</code> method suggests that the Java Virtual
             * Machine expend effort toward recycling unused objects in order to
             * make the memory they currently occupy available for quick reuse.
             * When control returns from the method call, the Java Virtual
             * Machine has made a best effort to reclaim space from all discarded
             * objects. This method calls Sys.gc() to perform GC.
             * </p>
             */
            public static void gc() {
                java.lang.System.gc();
            }

            /**
             * Returns the total amount of time spent in GarbageCollection up to this point
             * since the application was started.
             * @return
             */
            public static long getTotalGcTime() {
                return BTraceRuntime.getTotalGcTime();
            }

            /**
             * Returns an overview of available memory pools <br>
             * It is possible to provide a text format the overview will use
             * @param poolFormat The text format string to format the overview. <br>
             *                   Exactly 5 arguments are passed to the format function. <br>
             *                   The format defaults to ";%1$s;%2$d;%3$d;%4$d;%5$d;Memory]"
             * @return Returns the formatted value of memory pools overview
             * @since 1.2
             */
            public static String getMemoryPoolUsage(String poolFormat) {
                return BTraceRuntime.getMemoryPoolUsage(poolFormat);
            }

            /**
             * Runs the finalization methods of any objects pending finalization.
             * <p>
             * Calling this method suggests that the Java Virtual Machine expend
             * effort toward running the <code>finalize</code> methods of objects
             * that have been found to be discarded but whose <code>finalize</code>
             * methods have not yet been run. When control returns from the
             * method call, the Java Virtual Machine has made a best effort to
             * complete all outstanding finalizations. This method calls
             * Sys.runFinalization() to run finalization.
             * </p>
             */
            public static void runFinalization() {
                java.lang.System.runFinalization();
            }
        }

        /*
         * Wraps the VM related BTrace utility methods
         * @since 1.2
         */
        public static class VM {
            /**
             * Returns the input arguments passed to the Java virtual machine
             * which does not include the arguments to the <tt>main</tt> method.
             * This method returns an empty list if there is no input argument
             * to the Java virtual machine.
             * <p>
             * Some Java virtual machine implementations may take input arguments
             * from multiple different sources: for examples, arguments passed from
             * the application that launches the Java virtual machine such as
             * the 'java' command, environment variables, configuration files, etc.
             * <p>
             * Typically, not all command-line options to the 'java' command
             * are passed to the Java virtual machine.
             * Thus, the returned input arguments may not
             * include all command-line options.
             *
             * @return a list of <tt>String</tt> objects; each element
             * is an argument passed to the Java virtual machine.
             */
            public static List<String> vmArguments() {
                return BTraceRuntime.getInputArguments();
            }

            /**
             * Prints VM input arguments list.
             *
             * @see #vmArguments
             */
            public static void printVmArguments() {
                println(vmArguments());
            }

            /**
             * Returns the Java virtual machine implementation version.
             * This method is equivalent to {@link Sys#getProperty
             * Sys.getProperty("java.vm.version")}.
             *
             * @return the Java virtual machine implementation version.
             */
            public static String vmVersion() {
                return BTraceRuntime.getVmVersion();
            }

            /**
             * Tests if the Java virtual machine supports the boot class path
             * mechanism used by the bootstrap class loader to search for class
             * files.
             *
             * @return <tt>true</tt> if the Java virtual machine supports the
             * class path mechanism; <tt>false</tt> otherwise.
             */
            public static boolean isBootClassPathSupported() {
                return BTraceRuntime.isBootClassPathSupported();
            }

            /**
             * Returns the boot class path that is used by the bootstrap class loader
             * to search for class files.
             *
             * <p> Multiple paths in the boot class path are separated by the
             * path separator character of the platform on which the Java
             * virtual machine is running.
             *
             * <p>A Java virtual machine implementation may not support
             * the boot class path mechanism for the bootstrap class loader
             * to search for class files.
             * The {@link #isBootClassPathSupported} method can be used
             * to determine if the Java virtual machine supports this method.
             *
             * @return the boot class path.
             * @throws java.lang.UnsupportedOperationException
             *     if the Java virtual machine does not support this operation.
             */
            public static String bootClassPath() {
                return BTraceRuntime.getBootClassPath();
            }

            /**
             * Returns the Java class path that is used by the system class loader
             * to search for class files.
             * This method is equivalent to {@link Sys#getProperty
             * Sys.getProperty("java.class.path")}.
             *
             * @return the Java class path.
             */
            public static String classPath() {
                return Sys.Env.property("java.class.path");
            }

            /**
             * Returns the Java library path.
             * This method is equivalent to {@link Sys#getProperty
             * Sys.getProperty("java.library.path")}.
             *
             * <p> Multiple paths in the Java library path are separated by the
             * path separator character of the platform of the Java virtual machine
             * being monitored.
             *
             * @return the Java library path.
             */
            public static String libraryPath() {
                return Sys.Env.property("java.library.path");
            }

            /**
             * Returns the current number of live threads including both
             * daemon and non-daemon threads.
             *
             * @return the current number of live threads.
             */
            public static long threadCount() {
                return BTraceRuntime.getThreadCount();
            }

            /**
             * Returns the peak live thread count since the Java virtual machine
             * started or peak was reset.
             *
             * @return the peak live thread count.
             */
            public static long peakThreadCount() {
                return BTraceRuntime.getPeakThreadCount();
            }

            /**
             * Returns the total number of threads created and also started
             * since the Java virtual machine started.
             *
             * @return the total number of threads started.
             */
            public static long totalStartedThreadCount() {
                return BTraceRuntime.getTotalStartedThreadCount();
            }

            /**
             * Returns the current number of live daemon threads.
             *
             * @return the current number of live daemon threads.
             */
            public static long daemonThreadCount() {
                return BTraceRuntime.getDaemonThreadCount();
            }

            /**
             * Returns the start time of the Java virtual machine in milliseconds.
             * This method returns the approximate time when the Java virtual
             * machine started.
             *
             * @return start time of the Java virtual machine in milliseconds.
             */
            public static long vmStartTime() {
                return BTraceRuntime.vmStartTime();
            }

            /**
             * Returns the uptime of the Java virtual machine in milliseconds.
             *
             * @return uptime of the Java virtual machine in milliseconds.
             */
            public static long vmUptime() {
                return BTraceRuntime.vmUptime();
            }

            /**
             * Returns the total CPU time for the current thread in nanoseconds.
             * The returned value is of nanoseconds precision but
             * not necessarily nanoseconds accuracy.
             * If the implementation distinguishes between user mode time and system
             * mode time, the returned CPU time is the amount of time that
             * the current thread has executed in user mode or system mode.
             */
            public static long currentThreadCpuTime() {
                return BTraceRuntime.getCurrentThreadCpuTime();
            }

            /**
             * Returns the CPU time that the current thread has executed
             * in user mode in nanoseconds.
             * The returned value is of nanoseconds precision but
             * not necessarily nanoseconds accuracy.
             */
            public static long currentThreadUserTime() {
                return BTraceRuntime.getCurrentThreadUserTime();
            }
        }

        /**
         * Returns n'th command line argument. <code>null</code> if not available.
         *
         * @param n command line argument index
         * @return n'th command line argument
         */
        public static String $(int n) {
            return BTraceRuntime.$(n);
        }

        /**
         * Returns the process id of the currently BTrace'd process.
         */
        public static int getpid() {
            int pid = -1;
            try {
                pid = Integer.parseInt($(0));
            } catch (Exception ignored) {
            }
            return pid;
        }

        /**
         * Returns the number of command line arguments.
         */
        public static int $length() {
            return BTraceRuntime.$length();
        }

        /**
         * Exits the BTrace session -- note that the particular client's tracing
         * session exits and not the observed/traced program! After exit call,
         * the trace action method terminates immediately and no other probe action
         * method (of that client) will be called after that.
         *
         * @param exitCode exit value sent to the client
         */
        public static void exit(int exitCode) {
            BTraceRuntime.exit(exitCode);
        }

        /**
         * This is same as exit(int) except that the exit code
         * is zero.
         *
         * @see #exit(int)
         */
        public static void exit() {
            exit(0);
        }
    }

    /*
     * Wraps the jvmstat counters related BTrace utility methods
     * @since 1.2
     */
    public static class Counters {
        /**
         * accessing jvmstat (perf) int counter
         */
        public static long perfInt(String name) {
            return BTraceRuntime.perfInt(name);
        }

        /**
         * accessing jvmstat (perf) long counter
         */
        public static long perfLong(String name) {
            return BTraceRuntime.perfLong(name);
        }

        /**
         * accessing jvmstat (perf) String counter
         */
        public static String perfString(String name) {
            return BTraceRuntime.perfString(name);
        }
    }

    /*
     * Wraps the dtrace related BTrace utility methods
     * @since 1.2
     */
    public static class D {
        /**
         * BTrace to DTrace communication chennal.
         * Raise DTrace USDT probe from BTrace.
         *
         * @see #dtraceProbe(String,String,int,int)
         */
        public static int probe(String str1, String str2) {
            return probe(str1, str2, -1, -1);
        }

        /**
         * BTrace to DTrace communication chennal.
         * Raise DTrace USDT probe from BTrace.
         *
         * @see #dtraceProbe(String,String,int,int)
         */
        public static int probe(String str1, String str2, int i1) {
            return probe(str1, str2, i1, -1);
        }

        /**
         * BTrace to DTrace communication channel.
         * Raise DTrace USDT probe from BTrace.
         *
         * @param str1 first String param to DTrace probe
         * @param str2 second String param to DTrace probe
         * @param i1 first int param to DTrace probe
         * @param i2 second int param to DTrace probe
         */
        public static int probe(String str1, String str2, int i1, int i2) {
            return BTraceRuntime.dtraceProbe(str1, str2, i1, i2);
        }
    }

    // Internals only below this point
    private static void checkStatic(Field field) {
        if (! Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException(field.getName() +
                " is not a static field");
        }
    }

    private static Field getField(final Class clazz, final String name,
            final boolean throwError) {
        return AccessController.doPrivileged(new PrivilegedAction<Field>() {
            public Field run() {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (Exception exp) {
                    if (throwError) {
                       throw translate(exp);
                    } else {
                       return null;
                    }
                }
            }
        });
    }

    private static Field[] getAllFields(final Class clazz) {
        return AccessController.doPrivileged(new PrivilegedAction<Field[]>() {
            public Field[] run() {
                try {
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field f : fields) {
                        f.setAccessible(true);
                    }
                    return fields;
                } catch (Exception exp) {
                    throw translate(exp);
                }
            }
        });
    }

    private static void addFieldValues(StringBuilder buf, Object obj,
        Class clazz,  boolean classNamePrefix) {
        Field[] fields = getAllFields(clazz);
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            if (! Modifier.isStatic(modifiers)) {
                if (classNamePrefix) {
                    buf.append(f.getDeclaringClass().getName());
                    buf.append('.');
                }
                buf.append(f.getName());
                buf.append('=');
                try {
                    buf.append(Strings.str(f.get(obj)));
                } catch (Exception exp) {
                    throw translate(exp);
                }
                buf.append(", ");
            }
        }
        Class sc = clazz.getSuperclass();
        if (sc != null) {
           addFieldValues(buf, obj, sc, classNamePrefix);
        }
    }

    private static void addStaticFieldValues(StringBuilder buf,
        Class clazz, boolean classNamePrefix) {
        Field[] fields = getAllFields(clazz);
        for (Field f : fields) {
            int modifiers = f.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                if (classNamePrefix) {
                    buf.append(f.getDeclaringClass().getName());
                    buf.append('.');
                }
                buf.append(f.getName());
                buf.append('=');
                try {
                    buf.append(Strings.str(f.get(null)));
                } catch (Exception exp) {
                    throw translate(exp);
                }
                buf.append(", ");
            }
        }
        Class sc = clazz.getSuperclass();
        if (sc != null) {
           addStaticFieldValues(buf, sc, classNamePrefix);
        }
    }

    private static RuntimeException translate(Exception exp) {
        if (exp instanceof RuntimeException) {
            return (RuntimeException) exp;
        } else {
            return new RuntimeException(exp);
        }
    }
}
