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

import java.util.concurrent.atomic.LongAdder;

/**
 * Aggregation function for computing the mean value.
 *
 * <p>
 *
 * @author Christian Glencross
 */
class Average implements AggregationValue {

  private final LongAdder sum = new LongAdder();
  private final LongAdder count = new LongAdder();

  @Override
  public void clear() {
    sum.reset();
    count.reset();
  }

  @Override
  public void add(long delta) {
    sum.add(delta);
    count.increment();
  }

  @Override
  public long getValue() {
    long currentCount = count.sum();
    if (currentCount == 0) {
      return 0; // Avoid division by zero
    }
    return (int) (sum.sum() / currentCount);
  }

  @Override
  public Object getData() {
    return getValue();
  }
}
