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

package com.sun.btrace.samples;

import static com.sun.btrace.BTraceUtils.*;
import com.sun.btrace.annotations.*;

/**
 * A simple BTrace program that prints a class name
 * and method name whenever a webservice is called and
 * also prints time taken by service method. WebService 
 * entry points are annotated javax.jws.WebService and 
 * javax.jws.WebMethod. We insert tracing actions into 
 * every class and method annotated by these annotations. 
 * This way we don't need to know actual webservice 
 * implementor class name.
 */
@BTrace public class WebServiceTracker {
   @OnMethod(
     clazz="@javax.jws.WebService", 
     method="@javax.jws.WebMethod"
   )   
   public static void onWebserviceEntry(@ProbeClassName String pcn, @ProbeMethodName String pmn) {
       print("entering webservice ");
       println(Strings.strcat(Strings.strcat(pcn, "."), pmn));
   }

   @OnMethod(
     clazz="@javax.jws.WebService", 
     method="@javax.jws.WebMethod",
     location=@Location(Kind.RETURN)
   )   
   public static void onWebserviceReturn(@ProbeClassName String pcn , @ProbeMethodName String pmn, @Duration long d) {
       print("leaving web service ");
       println(Strings.strcat(Strings.strcat(pcn, "."), pmn));
       println(Strings.strcat("Time taken (msec) ", Strings.str(d / 1000)));
       println("==========================");
   }

}
