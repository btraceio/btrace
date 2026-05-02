/*
 * Copyright (c) 2008, 2024, Jaroslav Bachorik <j.bachorik@btrace.io>.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openjdk.btrace.core.types;

/**
 * This interface type is used in BTrace programs to tell that any reference type [object or array]
 * is allowed in the place where it is used. We use that for method signature matching when
 * signature needs to be specified loosely. Note that we don't want to use java.lang.Object -
 * because user may want to match java.lang.Object exactly.
 *
 * @author A. Sundararajan
 */
public interface AnyType {
  AnyType VOID = new AnyType() {};
}
