/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "com_sun_btrace_BTraceRuntime.h"
#include <sys/sdt.h>

/*
 * Class:     com_sun_btrace_BTraceRuntime
 * Method:    dtraceProbe0
 * Signature: (Ljava/lang/String;Ljava/lang/String;II)I
 */
JNIEXPORT jint JNICALL Java_com_sun_btrace_BTraceRuntime_dtraceProbe0
  (JNIEnv *env, jclass cls, jstring str1, jstring str2, jint i1, jint i2) {
  int result = 0;
  const char* cstr1;
  const char* cstr2;
  if (str1 != NULL) {
    cstr1 = (*env)->GetStringUTFChars(env, str1, NULL);
    if (cstr1 == NULL) return 0;
  } else {
    cstr1 = NULL;
  }

  if (str2 != NULL) {
    cstr2 = (*env)->GetStringUTFChars(env, str2, NULL);
  } else {
    cstr2 = NULL;
  } 
  if (cstr2 == NULL) {
    if (cstr1) (*env)->ReleaseStringUTFChars(env, str1, cstr1);
    return 0;
  }

  DTRACE_PROBE5(btrace, event, cstr1, cstr2, i1, i2, &result);

  if (cstr1) (*env)->ReleaseStringUTFChars(env, str1, cstr1);
  if (cstr2) (*env)->ReleaseStringUTFChars(env, str2, cstr2);
  return result;
}
