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
package io.btrace.test.ext;

/**
 * Integration-test service: exercises @ExternalType adapters against a class
 * (resources.ExternalData) that lives only in the target application's classloader.
 */
public interface ExternalTypeTestService {
  /** Returns the static tag from resources.ExternalData via its @ExternalType adapter. */
  String tag();

  /** Returns the static tag using the supplied target object's defining loader. */
  String explicitTag(Object externalData);

  /** Chains the opaque ExternalData child result into the child adapter. */
  String childMarker(Object externalData);

  String overloadMarkers(Object externalData);

  /** Passes an opaque child back through a target-type adapter parameter. */
  String acceptedChildMarker(Object externalData);

  /**
   * Returns the instance value from an resources.ExternalData object via its @ExternalType adapter.
   */
  int value(Object externalData);
}
