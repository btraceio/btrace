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

import java.util.Collection;
import java.util.HashSet;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * This class is used to store data of the annotation OnProbe. We can not read the OnMethod
 * annotation using reflection API [because we strip {@code @OnProbe} annotated methods before
 * defineClass]. Instead, we read OnProbe annotation while parsing the BTrace class and store the
 * data in an instance of this class. Please note that the get/set methods have to be in sync with
 * OnProbe annotation.
 *
 * @author A. Sundararajan
 */
public final class OnProbe extends SpecialParameterHolder {
  private String namespace;
  private String name;
  // target method name on which this annotation is specified
  private String targetName;
  // target method descriptor on which this annotation is specified
  private String targetDescriptor;
  private Collection<OnMethod> onMethods;
  private BTraceMethodNode bmn;

  public OnProbe(BTraceMethodNode bmn) {
    this.bmn = bmn;
  }

  public OnProbe() {
    // need this to deserialize from the probe descriptor
  }

  @XmlAttribute
  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  @XmlAttribute
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  @XmlElement(name = "map")
  public Collection<OnMethod> getOnMethods() {
    return onMethods;
  }

  public void setOnMethods(Collection<OnMethod> om) {
    onMethods = om;
  }

  public void copyFrom(OnProbe other) {
    super.copyFrom(other);
    namespace = other.namespace;
    name = other.name;
    targetName = other.targetName;
    targetDescriptor = other.targetDescriptor;
    onMethods = new HashSet<>(other.onMethods);
  }

  public BTraceMethodNode getMethodNode() {
    return bmn;
  }

  void setMethodNode(BTraceMethodNode bmn) {
    this.bmn = bmn;
  }
}
