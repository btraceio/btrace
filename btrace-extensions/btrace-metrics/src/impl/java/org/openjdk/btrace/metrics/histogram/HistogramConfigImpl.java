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
package org.openjdk.btrace.metrics.histogram;

public final class HistogramConfigImpl implements HistogramConfig {
  private final long lowestDiscernibleValue;
  private final long highestTrackableValue;
  private final int numberOfSignificantValueDigits;

  public HistogramConfigImpl(long lowest, long highest, int digits) {
    this.lowestDiscernibleValue = lowest;
    this.highestTrackableValue = highest;
    this.numberOfSignificantValueDigits = digits;
  }

  @Override
  public long getLowestDiscernibleValue() {
    return lowestDiscernibleValue;
  }

  @Override
  public long getHighestTrackableValue() {
    return highestTrackableValue;
  }

  @Override
  public int getNumberOfSignificantValueDigits() {
    return numberOfSignificantValueDigits;
  }
}
