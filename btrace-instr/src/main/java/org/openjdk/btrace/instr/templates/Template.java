/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
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

package org.openjdk.btrace.instr.templates;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A template descriptor
 *
 * @author Jaroslav Bachorik
 * @since 1.3
 */
public final class Template {
  static final String OWNER = "#";
  private final String name;
  private final String sig;
  private final Set<String> tags = new HashSet<>();

  private final String id;

  public Template(String name, String sig) {
    this.name = name;
    this.sig = sig;
    id = getId(OWNER, name, sig);
  }

  public Template(String name, String sig, String... tags) {
    this.name = name;
    this.sig = sig;
    this.tags.addAll(Arrays.asList(tags));
    id = getId(OWNER, name, sig);
  }

  static String getId(String owner, String name, String sig) {
    return owner + '#' + name + "#" + sig;
  }

  public String getOwner() {
    return OWNER;
  }

  public String getName() {
    return name;
  }

  public String getSig() {
    return sig;
  }

  public void insert(MethodVisitor mv) {
    insert(mv, Collections.emptySet());
  }

  public void insert(MethodVisitor mv, String... tags) {
    insert(mv, new HashSet<>(Arrays.asList(tags)));
  }

  public void insert(MethodVisitor mv, Set<String> tags) {
    StringBuilder sb = new StringBuilder(name);
    boolean head = true;
    for (String t : tags) {
      if (head) {
        head = false;
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(t);
    }
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, sb.toString(), sig, false);
  }

  public Set<String> getTags() {
    return Collections.unmodifiableSet(tags);
  }

  void setTags(String... tags) {
    setTags(Arrays.asList(tags));
  }

  void setTags(Collection<String> tags) {
    this.tags.clear();
    this.tags.addAll(tags);
  }

  public Map<String, String> getTagMap() {
    Map<String, String> tMap = new HashMap<>();

    for (String t : tags) {
      int idx = t.indexOf('=');
      if (idx > -1) {
        String key, val;
        key = t.substring(0, idx);
        val = t.substring(idx + 1);
        tMap.put(key, val);
      } else {
        tMap.put(t, "");
      }
    }
    return tMap;
  }

  String getId() {
    return id;
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 97 * hash + (name != null ? name.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    Template other = (Template) obj;
    return Objects.equals(name, other.name);
  }

  @Override
  public String toString() {
    return "Template{" + "name=" + name + ", tags=" + tags + '}';
  }

  public final Template duplicate() {
    return new Template(name, sig);
  }
}
