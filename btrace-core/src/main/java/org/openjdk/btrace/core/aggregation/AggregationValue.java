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

/**
 * An element of aggregated data stored in an {@link Aggregation}.
 *
 * <p>Concrete implementations of this interface will implement different aggregating functions.
 *
 * <p>
 *
 * @author Christian Glencross
 */
public interface AggregationValue {

  /**
   * Adds a data item to the aggregated value.
   *
   * @param data the data value
   */
  void add(long data);

  /** Removes all data items previously added. */
  void clear();

  /**
   * @return the aggregated value of all data items added since the aggregation was created or last
   *     cleared. The aggregation function is determined by the concrete implementation of the
   *     interface.
   */
  long getValue();

  /**
   * @return an object representation of the aggregated value. For most implementations this may be
   *     equivalent to <code>Integer.valueOf( getValue() )</code>. More complex aggregations such
   *     may return objects representing histograms, etc.
   */
  Object getData();
}
