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

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * A wire data structure describing histogram data.
 *
 * <p>
 *
 * @author Christian Glencross
 */
public class HistogramData implements Serializable {

  private static final long serialVersionUID = 1L;
  private final long[] values;
  private final long[] counts;

  public HistogramData(long[] values, long[] counts) {
    if (values.length != counts.length) {
      throw new IllegalArgumentException("values and counts are different lengths");
    }
    this.values = values;
    this.counts = counts;
  }

  public long[] getValues() {
    return values;
  }

  public long[] getCounts() {
    return counts;
  }

  public void print(PrintWriter p) {
    int totalCount = 0;
    for (long count : counts) {
      totalCount += count;
    }

    p.println("          value  ------------- Distribution ------------- count");
    for (int i = 0; i < values.length; i++) {
      p.print(String.format("%15d", values[i]));
      p.print(" |");
      long lineLength = (40 * counts[i]) / totalCount;
      for (int j = 0; j < 40; j++) {
        p.print(j < lineLength ? "@" : " ");
      }
      p.print(" ");
      p.println(counts[i]);
    }
  }
}
