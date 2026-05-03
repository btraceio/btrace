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
package org.openjdk.btrace.instr;

import org.openjdk.btrace.core.annotations.Kind;
import org.openjdk.btrace.core.annotations.Where;

/**
 * This class is used to store data of the annotation Location. We can not read the Location
 * annotation using reflection API [because we strip {@code @OnMethod} annotated methods before
 * defineClass]. Instead, we read Location annotation while parsing the BTrace class and store the
 * data in an instance of this class. Please note that the get/set methods have to be in sync with
 * Location annotation.
 *
 * @author A. Sundararajan
 */
public class Location {
  private String clazz = "";
  private String method = "";
  private String type = "";
  private String field = "";
  private int line = 0;
  private Kind value = Kind.ENTRY;
  private Where where = Where.BEFORE;

  public String getClazz() {
    return clazz;
  }

  public void setClazz(String clazz) {
    this.clazz = clazz;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public int getLine() {
    return line;
  }

  public void setLine(int line) {
    this.line = line;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Kind getValue() {
    return value;
  }

  public void setValue(Kind value) {
    this.value = value;
  }

  public Where getWhere() {
    return where;
  }

  public void setWhere(Where where) {
    this.where = where;
  }

  @Override
  public String toString() {
    return "Location{"
        + "clazz="
        + clazz
        + ", method="
        + method
        + ", type="
        + type
        + ", field="
        + field
        + ", line="
        + line
        + ", value="
        + value
        + ", where="
        + where
        + '}';
  }
}
