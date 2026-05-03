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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "btrace-probes")
public class ProbeDescriptor {
  private final Map<String, OnProbe> nameToProbeMap;
  private String namespace;
  private Collection<OnProbe> probes;

  public ProbeDescriptor() {
    nameToProbeMap = new HashMap<>();
  }

  @XmlAttribute(name = "namespace")
  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  @XmlElement(name = "probe")
  public Collection<OnProbe> getProbes() {
    return probes;
  }

  public void setProbes(Collection<OnProbe> probes) {
    this.probes = probes;
    for (OnProbe op : probes) {
      nameToProbeMap.put(op.getName(), op);
    }
  }

  public OnProbe findProbe(String name) {
    return nameToProbeMap.get(name);
  }
}
