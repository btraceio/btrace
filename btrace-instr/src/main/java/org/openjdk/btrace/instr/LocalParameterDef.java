package org.openjdk.btrace.instr;

import java.util.Objects;

final class LocalParameterDef {
  final int idx;
  final String name;
  final boolean mutable;

  LocalParameterDef(int idx, String name, boolean mutable) {
    this.idx = idx;
    this.name = name;
    this.mutable = mutable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LocalParameterDef that = (LocalParameterDef) o;
    return idx == that.idx && mutable == that.mutable && name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(idx, name, mutable);
  }

  @Override
  public String toString() {
    return "LocalParameterDef{"
        + "idx="
        + idx
        + ", name='"
        + name
        + '\''
        + ", mutable="
        + mutable
        + '}';
  }
}
