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

/**
 * A generalized super-class for various runtime classes representing the BTrace annotations
 *
 * @author Jaroslav Bachorik
 */
abstract class SpecialParameterHolder {
  private int selfParameter = -1;
  private int methodParameter = -1;
  private int classNameParameter = -1;
  private int returnParameter = -1;
  private int targetMethodOrFieldParameter = -1;
  private int targetInstanceParameter = -1;
  private int durationParameter = -1;

  private boolean methodFqn = false;
  private boolean targetMethodFqn = false;

  public final int getSelfParameter() {
    return selfParameter;
  }

  public final void setSelfParameter(int selfParameter) {
    this.selfParameter = selfParameter;
  }

  public final int getClassNameParameter() {
    return classNameParameter;
  }

  public final void setClassNameParameter(int classNameParameter) {
    this.classNameParameter = classNameParameter;
  }

  public final int getMethodParameter() {
    return methodParameter;
  }

  public final void setMethodParameter(int methodParameter) {
    this.methodParameter = methodParameter;
  }

  public final boolean isMethodFqn() {
    return methodFqn;
  }

  public final void setMethodFqn(boolean val) {
    methodFqn = val;
  }

  public final boolean isTargetMethodOrFieldFqn() {
    return targetMethodFqn;
  }

  public final void setTargetMethodOrFieldFqn(boolean val) {
    targetMethodFqn = val;
  }

  public final int getReturnParameter() {
    return returnParameter;
  }

  public final void setReturnParameter(int returnParameter) {
    this.returnParameter = returnParameter;
  }

  public final int getTargetMethodOrFieldParameter() {
    return targetMethodOrFieldParameter;
  }

  public final void setTargetMethodOrFieldParameter(int calledMethodParameter) {
    targetMethodOrFieldParameter = calledMethodParameter;
  }

  public final int getTargetInstanceParameter() {
    return targetInstanceParameter;
  }

  public final void setTargetInstanceParameter(int calledInstanceParameter) {
    targetInstanceParameter = calledInstanceParameter;
  }

  public final int getDurationParameter() {
    return durationParameter;
  }

  public final void setDurationParameter(int durationParameter) {
    this.durationParameter = durationParameter;
  }

  public final void copyFrom(SpecialParameterHolder other) {
    classNameParameter = other.classNameParameter;
    durationParameter = other.durationParameter;
    methodParameter = other.methodParameter;
    returnParameter = other.returnParameter;
    selfParameter = other.selfParameter;
    targetInstanceParameter = other.targetInstanceParameter;
    targetMethodOrFieldParameter = other.targetMethodOrFieldParameter;
    methodFqn = other.methodFqn;
    targetMethodFqn = other.targetMethodFqn;
  }
}
