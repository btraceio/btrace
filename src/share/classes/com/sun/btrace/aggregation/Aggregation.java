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
package com.sun.btrace.aggregation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BTrace stores the results of aggregating functions in an Aggregation. The aggregated values may be grouped using a
 * composite {@link AggregationKey}.
 * <p>
 *
 * @author Christian Glencross
 */
public class Aggregation implements Cloneable {

    private static final AggregationKey NULL_AGGREGATION_KEY = new AggregationKey(new Object[0]);
    private final AggregationFunction type;
    private final ConcurrentHashMap<AggregationKey, AggregationValue> values = new ConcurrentHashMap<>();

    /**
     * Creates an aggregation.
     *
     * @param type
     *            the type of aggregation function to use
     *
     */
    public Aggregation(AggregationFunction type) {
        super();
        this.type = type;
    }

    /**
     * Adds an item of data to the aggregation with an empty key. This method is recommended if the aggregation will
     * contain only a single value.
     *
     * @param data
     *            the value to be added
     */
    public void add(long data) {
        add(NULL_AGGREGATION_KEY, data);
    }

    /**
     * Adds an item of data to the aggregation with the specified grouping key.
     *
     * @param key
     *            the aggregation key
     * @param data
     *            the value to be added
     */
    public void add(AggregationKey key, long data) {
        AggregationValue aggregationValue = values.get(key);
        if (aggregationValue == null) {
            aggregationValue = type.newValue();
            AggregationValue existing = values.putIfAbsent(key, aggregationValue);
            if (existing != null) {
                aggregationValue = existing;
            }
        }
        aggregationValue.add(data);
    }

    /**
     * Resets all values in the aggregation to their default.
     */
    public void clear() {
        for (AggregationValue value : values.values()) {
            value.clear();
        }
    }

    /**
     * Reduces the size of the aggregation to the absolute value of <code>count</code>. If count is greater than
     * zero, the largest aggregated values are preserved. If it is less than zero, the smallest aggregated values are
     * preserved. Passing a value of zero clears the aggregation completely.
     *
     * @param count
     *            the absolute number indicates the number of aggregated values to preserve.
     */
    public void truncate(int count) {
        if (count == 0) {
            values.clear();
        } else {
            List<Map.Entry<AggregationKey, AggregationValue>> sortedContents = sort();

            int collectionSize = sortedContents.size();
            int numberToRemove = collectionSize - Math.abs(count);
            if (numberToRemove < 0) {
                return;
            }
            List<Map.Entry<AggregationKey, AggregationValue>> removeContents;
            if (count > 0) {
                // Remove from the start of the list
                removeContents = sortedContents.subList(0, numberToRemove);
            } else {
                removeContents = sortedContents.subList(collectionSize - numberToRemove, collectionSize);
            }
            for (int i = 0; i < removeContents.size(); i++) {
                values.remove(removeContents.get(i).getKey());
            }
        }
    }

    /**
     * Returns details of the aggregation in a tabular format which can be serialized across the wire and formatted for
     * display. The data is represented as a List of rows. The last element in each row represents the aggregated value,
     * the elements before this in the row contain the elements of the aggregating key.
     *
     * @return details of the aggregation in a tabular format.
     */
    public List<Object[]> getData() {
        List<Entry<AggregationKey, AggregationValue>> sortedContents = sort();
        List<Object[]> result = new ArrayList<>(sortedContents.size());

        for (Entry<AggregationKey, AggregationValue> item : sortedContents) {

            Object[] keyElements = item.getKey().getElements();
            int rowSize = keyElements.length + 1;

            Object[] row = new Object[rowSize];
            System.arraycopy(keyElements, 0, row, 0, keyElements.length);
            row[rowSize - 1] = item.getValue().getData();
            result.add(row);
        }

        return result;
    }


    /**
     * Returns a list of the AggregationKeys that belong to this aggregation.
     * @return a list of aggregationsKeys belonging to this aggregation.
     */
    public List<AggregationKey> getKeyData() {
    	List<AggregationKey> keyList = new ArrayList<>();
    	List<Entry<AggregationKey, AggregationValue>> sortedContents = sort();
    	for (Entry<AggregationKey, AggregationValue> item : sortedContents) {
    		keyList.add(item.getKey());
    	}

    	return keyList;
    }

    /**
     * Returns a value for the given key if the key has a value associated with it. Returns zero if the key is not
     * valid for this Aggregation.
     * @param key
     * @return the value for the given key, or zero.
     */
    public Long getValueForKey(AggregationKey key) {
    	AggregationValue aggregationValue = values.get(key);
    	if (aggregationValue != null) {
    		return aggregationValue.getValue();
    	} else {
    		return 0L;
    	}
    }
    /**
     * @return a list of key/value pairs contained in this aggregation by sorted by ascending value.
     */
    private List<Map.Entry<AggregationKey, AggregationValue>> sort() {
        ArrayList<Map.Entry<AggregationKey, AggregationValue>> result = new ArrayList<>(
                values.entrySet());
        Collections.sort(result, new Comparator<Map.Entry<AggregationKey, AggregationValue>>() {

            @Override
            public int compare(Entry<AggregationKey, AggregationValue> o1, Entry<AggregationKey, AggregationValue> o2) {
                long i1 = o1.getValue().getValue();
                long i2 = o2.getValue().getValue();
                if (i1 < i2) {
                    return -1;
                } else if (i1 == i2) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });
        return result;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return new Aggregation(type);
    }

}
