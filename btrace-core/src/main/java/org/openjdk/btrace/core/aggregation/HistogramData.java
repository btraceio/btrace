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
    for (int i = 0; i < counts.length; i++) {
      totalCount += counts[i];
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
