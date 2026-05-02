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
package org.openjdk.btrace.core.aggregation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A key identifying an element of data in an aggregation. This represents a tuple of object values
 * contained in an Object[] array. Elements in the tuple may be null or of type {@link String} or
 * {@link Number}.
 *
 * <p>
 *
 * @author Christian Glencross
 */
public final class AggregationKey {

  private static final Set<Class<?>> validKeyElementTypes = new HashSet<>();

  static {
    validKeyElementTypes.add(String.class);
    validKeyElementTypes.add(Boolean.class);
    validKeyElementTypes.add(Byte.class);
    validKeyElementTypes.add(Character.class);
    validKeyElementTypes.add(Short.class);
    validKeyElementTypes.add(Integer.class);
    validKeyElementTypes.add(Long.class);
  }

  private final Object[] elements;

  public AggregationKey(Object[] elements) {

    // Validate that no unusual datatypes are in the key. These
    // values may end up getting serialized to the client so we do not want
    // anything unusual.
    for (Object element : elements) {
      if (element != null
          && (element.getClass() != String.class)
          && (element.getClass() != Boolean.class)
          && (element.getClass() != Byte.class)
          && (element.getClass() != Character.class)
          && (element.getClass() != Short.class)
          && (element.getClass() != Integer.class)
          && (element.getClass() != Long.class)) {
        throw new IllegalArgumentException(
            "Aggregation key element type '" + element.getClass().getName() + "' is not supported");
      }
    }

    this.elements = elements;
  }

  public Object[] getElements() {
    return elements;
  }

  @Override
  public int hashCode() {
    int prime = 31;
    int result = 1;
    result = prime * result + Arrays.hashCode(elements);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AggregationKey other = (AggregationKey) obj;
    return Arrays.equals(elements, other.elements);
  }
}
