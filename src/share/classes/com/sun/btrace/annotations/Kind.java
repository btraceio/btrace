/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.btrace.annotations;

/**
 * This enum is specified in the Location
 * annotation to specify probe point kind.
 * This enum identifies various "points" of 
 * interest within a Java method's bytecode.
 *
 * @author A. Sundararajan
 */
public enum Kind {
    /**
     * array element load
     */
    ARRAY_GET,  

    /**
     * array element store
     */
    ARRAY_SET,  

    /**
     * method call
     */
    CALL,
       
    /**
     * exception catch
     */
    CATCH,     

    /**
     * checkcast
     */
    CHECKCAST,  

    /**
     * method entry
     */
    ENTRY,      
 
    /**
     * "return" because of no-catch
     */
    ERROR,      

    /**
     * getting a field value
     */
    FIELD_GET, 

    /**
     * setting a field value
     */
    FIELD_SET,  

    /**
     * instanceof check
     */
    INSTANCEOF, 

    /**
     * source line number
     */
    LINE,       

    /**
     * new object created
     */
    NEW,        

    /**
     * new array created
     */
    NEWARRAY,   

    /**
     * return from method
     */
    RETURN,     

    /**
     * entry into a synchronized block
     */
    SYNC_ENTRY, 

    /**
     * exit from a synchronized block
     */
    SYNC_EXIT, 

    /**
     * throwing an exception
     */
    THROW       
};
