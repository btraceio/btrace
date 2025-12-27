/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the Classpath exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package org.openjdk.btrace.core.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.openjdk.btrace.core.extensions.Permission;

/**
 * Declares that a BTrace script requests a specific permission.
 *
 * <p>Scripts must declare all permissions required by the extensions they use. The BTrace verifier
 * will check that these permissions are granted before allowing the script to run.
 *
 * <p>Example:
 *
 * <pre>
 * &#64;BTrace
 * &#64;RequestPermission(Permission.NETWORK)
 * &#64;RequestPermission(Permission.THREADS)
 * public class MyScript {
 *   &#64;Injected
 *   private static StatsdExtension statsd;
 *   // ...
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(RequestPermissions.class)
public @interface RequestPermission {
  /**
   * The requested permission.
   *
   * @return permission
   */
  Permission value();
}
