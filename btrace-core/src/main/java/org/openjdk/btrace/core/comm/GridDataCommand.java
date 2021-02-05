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

package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.openjdk.btrace.core.aggregation.HistogramData;

/**
 * A data command that holds tabular data.
 *
 * <p>The elements contained within the grid must be of type Number, String or HistogramData.
 *
 * @author Christian Glencross
 */
public class GridDataCommand extends DataCommand {
  private static final Pattern INDEX_PATTERN = Pattern.compile("%(\\d)+\\$");
  private static final HashMap<Class<?>, String> typeFormats = new HashMap<>();

  static {
    typeFormats.put(Integer.class, "%15d");
    typeFormats.put(Short.class, "%15d");
    typeFormats.put(Byte.class, "%15d");
    typeFormats.put(Long.class, "%15d");
    typeFormats.put(BigInteger.class, "%15d");
    typeFormats.put(Double.class, "%15f");
    typeFormats.put(Float.class, "%15f");
    typeFormats.put(BigDecimal.class, "%15f");
    typeFormats.put(String.class, "%-50s");
  }

  private List<Object[]> data;
  private String format;

  /**
   * Used when deserializing a {@linkplain GridDataCommand} instance.<br>
   * The instance is then initialized by calling the {@linkplain
   * GridDataCommand#read(java.io.ObjectInput) } method
   */
  public GridDataCommand() {
    this(null, null);
  }

  /**
   * Creates a new instance of {@linkplain GridDataCommand} with implicit format
   *
   * @param name The aggregation name
   * @param data The aggregation data
   */
  public GridDataCommand(String name, List<Object[]> data) {
    this(name, data, null);
  }

  /**
   * Creates a new instance of {@linkplain GridDataCommand} with explicit format
   *
   * @param name The aggregation name
   * @param data The aggregation data
   * @param format The format to use. It mimics {@linkplain String#format(java.lang.String,
   *     java.lang.Object[]) } behaviour with the addition of the ability to address the key title
   *     as a 0-indexed item
   * @see String#format(java.lang.String, java.lang.Object[])
   */
  public GridDataCommand(String name, List<Object[]> data, String format) {
    super(GRID_DATA, name, false);
    this.data = data;
    this.format = format;
  }

  public List<Object[]> getData() {
    return data;
  }

  /**
   * Calculates the necessary field width of each column
   *
   * @param objects a list of objects
   * @return a map containing as key the column number and as value the width of the longest value
   */
  private Map<Integer, Integer> getColumnWidth(List<Object[]> objects) {
    Map<Integer, Integer> columnWidth = new LinkedHashMap<>();
    for (Object[] obj : objects) {
      for (int column = 0; column < obj.length; ++column) {
        int length = obj[column].toString().length();
        Integer width = 0;
        if (columnWidth.containsKey(column)) {
          width = columnWidth.get(column);
        }
        if (length > width) {
          columnWidth.put(column, length);
        }
      }
    }
    return columnWidth;
  }

  @Override
  public void print(PrintWriter out) {

    if (data != null) {
      if (name != null && !name.isEmpty()) {
        out.println(name);
      }
      Map<Integer, Integer> columnWidth = getColumnWidth(data);
      for (Object[] dataRow : data) {

        // Convert histograms to strings, and pretty-print multi-line text
        Object[] printRow = dataRow.clone();
        for (int i = 0; i < printRow.length; i++) {
          if (printRow[i] == null) {
            printRow[i] = "<null>";
          }
          if (printRow[i] instanceof HistogramData) {
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            ((HistogramData) printRow[i]).print(writer);
            writer.flush();
            printRow[i] = buffer.toString();
          }
          if (printRow[i] instanceof String) {
            String value = (String) printRow[i];
            if (value.contains("\n")) {
              printRow[i] = reformatMultilineValue(value);
            }
          }
        }

        // Format the text
        String usedFormat = format;
        if (usedFormat == null || usedFormat.length() == 0) {
          StringBuilder buffer = new StringBuilder();
          for (int i = 0; i < printRow.length; i++) {
            buffer.append("  ");
            buffer.append(getFormat(printRow[i], columnWidth, i));
          }
          usedFormat = buffer.toString();
        }
        String line = String.format(usedFormat, printRow);

        out.println(line);
      }
    }
    out.flush();
  }

  /**
   * Get the format of an object
   *
   * @param object get the format of this object
   * @param columnWidth a map containing as key the column number and as value the width of the
   *     longest value
   * @param column get the format of that column number
   * @return the format of an object
   */
  private String getFormat(Object object, Map<Integer, Integer> columnWidth, Integer column) {
    if (object == null) {
      return "%-15s";
    }
    String usedFormat = typeFormats.get(object.getClass());
    if (usedFormat == null) {
      return "%-15s";
    }
    if (columnWidth != null && column != null && columnWidth.containsKey(column)) {
      usedFormat = usedFormat.replaceFirst("\\d+", String.valueOf(columnWidth.get(column)));
    }
    return usedFormat;
  }

  /**
   * Takes a multi-line value, prefixes and appends a blank line, and inserts tab characters at the
   * start of every line. This is derived from how dtrace displays stack traces, and it makes for
   * pretty readable output.
   */
  private String reformatMultilineValue(String value) {
    StringBuilder result = new StringBuilder();
    result.append("\n");
    for (String line : value.split("\n")) {
      result.append("\t").append(line);
      result.append("\n");
    }
    return result.toString();
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeUTF(name != null ? name : "");
    if (data != null) {
      out.writeUTF(format != null ? format : "");
      out.writeInt(data.size());
      for (Object[] row : data) {
        out.writeInt(row.length);
        for (Object cell : row) {
          out.writeObject(cell);
        }
      }
    } else {
      out.writeInt(0);
    }
  }

  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    name = in.readUTF();
    format = in.readUTF();
    if (format.length() == 0) format = null;

    int rowCount = in.readInt();
    data = new ArrayList<>(rowCount);
    for (int i = 0; i < rowCount; i++) {
      int cellCount = in.readInt();
      Object[] row = new Object[cellCount];
      for (int j = 0; j < cellCount; j++) {
        row[j] = in.readObject();
      }
      data.add(row);
    }
  }
}
