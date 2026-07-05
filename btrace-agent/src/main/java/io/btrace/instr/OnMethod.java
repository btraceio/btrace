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
package io.btrace.instr;

import io.btrace.core.ArgsMap;
import io.btrace.core.annotations.Sampled;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to store data of the annotation OnMethod. We can not read the OnMethod
 * annotation using reflection API [because we strip {@code @OnMethod} annotated methods before
 * defineClass]. Instead, we read OnMethod annotation while parsing the BTrace class and store the
 * data in an instance of this class. Please note that the get/set methods have to be in sync with
 * OnMethod annotation.
 *
 * @author A. Sundararajan
 */
public final class OnMethod extends SpecialParameterHolder {
  private static final Logger log = LoggerFactory.getLogger(OnMethod.class);

  private String clazz;
  private volatile Pattern compiledClassPattern;
  private static final AtomicReferenceFieldUpdater<OnMethod, Pattern> compiledClassPatternUpdater =
      AtomicReferenceFieldUpdater.newUpdater(OnMethod.class, Pattern.class, "compiledClassPattern");
  private String method = "";
  private boolean exactTypeMatch;
  private String type = "";
  private Location loc = new Location();
  // target method name on which this annotation is specified
  private String targetName;
  // target method descriptor on which this annotation is specified
  private String targetDescriptor;
  private boolean classRegexMatcher = false;
  private boolean methodRegexMatcher = false;
  private boolean classAnnotationMatcher = false;
  private boolean methodAnnotationMatcher = false;
  private boolean subtypeMatcher = false;
  private int samplerMean = 0;
  private Sampled.Sampler samplerKind = Sampled.Sampler.None;
  private Level level = null;
  private boolean isCalled = false;
  private BTraceMethodNode bmn;

  public OnMethod() {
    // need this to deserialize from the probe descriptor
  }

  public OnMethod(BTraceMethodNode bmn) {
    this.bmn = bmn;
  }

  public void copyFrom(OnMethod other) {
    super.copyFrom(other);
    setClazz(other.getClazz());
    setMethod(other.getMethod());
    setExactTypeMatch(other.isExactTypeMatch());
    setType(other.getType());
    // Deep-copy the Location: the source OnMethod is a template cached in a process-wide map, and
    // applyArgs mutates the Location in place. Sharing the reference would let concurrent sessions
    // corrupt each other's substituted @OnProbe location values.
    Location otherLoc = other.getLocation();
    setLocation(otherLoc != null ? new Location(otherLoc) : null);
    setLevel(other.getLevel());
  }

  public String getClazz() {
    return clazz;
  }

  public void setClazz(String clazz) {
    if (clazz.charAt(0) == '+') {
      subtypeMatcher = true;
      clazz = clazz.substring(1);
    } else {
      subtypeMatcher = false;
      if (clazz.charAt(0) == '@') {
        classAnnotationMatcher = true;
        clazz = clazz.substring(1);
      } else {
        classAnnotationMatcher = false;
      }
      if (clazz.charAt(0) == '/' && Constants.REGEX_SPECIFIER.matcher(clazz).matches()) {
        classRegexMatcher = true;
        clazz = clazz.substring(1, clazz.length() - 1);
      } else {
        classRegexMatcher = false;
      }
    }
    this.clazz = clazz;
    this.compiledClassPattern = null;
  }

  public Pattern getClassPattern() {
    if (!classRegexMatcher) {
      return null;
    }

    if (compiledClassPattern == null) {
      compiledClassPatternUpdater.updateAndGet(
          this,
          pattern -> {
            if (pattern == null) {
              try {
                pattern = Pattern.compile(clazz);
              } catch (PatternSyntaxException e) {
                log.warn("Invalid regex pattern in OnMethod: {}, defaulting to '.*", clazz, e);
                pattern = Pattern.compile(".*");
              }
            }
            return pattern;
          });
    }

    return compiledClassPattern;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    char firstChar = method.isEmpty() ? 0 : method.charAt(0);
    if (firstChar == '@') {
      methodAnnotationMatcher = true;
      method = method.substring(1);
    } else {
      methodAnnotationMatcher = false;
    }
    firstChar = method.isEmpty() ? 0 : method.charAt(0);
    if (firstChar == '/' && Constants.REGEX_SPECIFIER.matcher(method).matches()) {
      methodRegexMatcher = true;
      method = method.substring(1, method.length() - 1);
    } else {
      methodRegexMatcher = false;
    }
    this.method = method;
  }

  public boolean isExactTypeMatch() {
    return exactTypeMatch;
  }

  public void setExactTypeMatch(boolean exactTypeMatch) {
    this.exactTypeMatch = exactTypeMatch;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Location getLocation() {
    return loc;
  }

  public void setLocation(Location loc) {
    this.loc = loc;
  }

  public String getTargetName() {
    return targetName;
  }

  public void setTargetName(String name) {
    targetName = name;
  }

  public String getTargetDescriptor() {
    return targetDescriptor;
  }

  public void setTargetDescriptor(String desc) {
    targetDescriptor = desc;
  }

  public Sampled.Sampler getSamplerKind() {
    return samplerKind;
  }

  public void setSamplerKind(Sampled.Sampler kind) {
    samplerKind = kind;
  }

  public int getSamplerMean() {
    return samplerMean;
  }

  public void setSamplerMean(int mean) {
    samplerMean = mean;
  }

  public Level getLevel() {
    return level;
  }

  public void setLevel(Level level) {
    this.level = level;
  }

  public BTraceMethodNode getMethodNode() {
    return bmn;
  }

  public boolean isClassRegexMatcher() {
    return classRegexMatcher;
  }

  public boolean isMethodRegexMatcher() {
    return methodRegexMatcher;
  }

  public boolean isClassAnnotationMatcher() {
    return classAnnotationMatcher;
  }

  public boolean isMethodAnnotationMatcher() {
    return methodAnnotationMatcher;
  }

  public boolean isSubtypeMatcher() {
    return subtypeMatcher;
  }

  public boolean isCalled() {
    return isCalled;
  }

  public void setCalled() {
    isCalled = true;
  }

  @Override
  public String toString() {
    return "OnMethod{"
        + "clazz="
        + clazz
        + ", method="
        + method
        + ", type="
        + type
        + ", loc="
        + loc
        + ", targetName="
        + targetName
        + ", targetDescriptor="
        + targetDescriptor
        + ", classRegexMatcher="
        + classRegexMatcher
        + ", methodRegexMatcher="
        + methodRegexMatcher
        + ", classAnnotationMatcher="
        + classAnnotationMatcher
        + ", methodAnnotationMatcher="
        + methodAnnotationMatcher
        + ", subtypeMatcher="
        + subtypeMatcher
        + ", samplerMean="
        + samplerMean
        + ", samplerKind="
        + samplerKind
        + ", level="
        + level
        + ", bmn="
        + bmn
        + '}';
  }

  void applyArgs(ArgsMap argsMap) {
    String value = getClazz();
    if (!value.isEmpty()) {
      String templated = argsMap.template(value);
      if (!templated.equals(value)) {
        setClazz(templated);
      }
    }
    value = getMethod();
    if (!value.isEmpty()) {
      String templated = argsMap.template(value);
      if (!templated.equals(value)) {
        setMethod(templated);
      }
    }
    value = getType();
    if (!value.isEmpty()) {
      String templated = argsMap.template(value);
      if (!templated.equals(value)) {
        setType(templated);
      }
    }
    Location loc = getLocation();
    value = loc.getClazz();
    if (!value.isEmpty()) {
      String templated = argsMap.template(value);
      if (!templated.equals(value)) {
        loc.setClazz(templated);
      }
    }
    value = loc.getMethod();
    if (!value.isEmpty()) {
      String templated = argsMap.template(value);
      if (!templated.equals(value)) {
        loc.setMethod(templated);
      }
    }
    value = loc.getField();
    if (!value.isEmpty()) {
      String templated = argsMap.template(value);
      if (!templated.equals(value)) {
        loc.setField(templated);
      }
    }
    value = loc.getType();
    if (!value.isEmpty()) {
      String templated = argsMap.template(value);
      if (!templated.equals(value)) {
        loc.setType(templated);
      }
    }
  }
}
