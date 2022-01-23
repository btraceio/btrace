package org.openjdk.btrace.runtime;

public final class ValueHolder {
  private long v1 = 0;
  private double v2 = Double.NaN;
  private Object v3 = null;

  public void setByte(byte b) {
    this.v1 = b;
  }

  public void setChar(char c) {
    this.v1 = c;
  }

  public void setInt(int i) {
    this.v1 = i;
  }

  public void setLong(long l) {
    this.v1 = l;
  }

  public void setBoolean(boolean z) {
    this.v1 = z ? 1 : 0;
  }

  public void setFloat(float f) {
    this.v2 = f;
  }

  public void setDouble(double d) {
    this.v2 = d;
  }

  public void setObject(Object o) {
    this.v3 = o;
  }

  public byte getByte() {
    return (byte) v1;
  }

  public char getChar() {
    return (char) v1;
  }

  public int getInt() {
    return (int) v1;
  }

  public long getLong() {
    return v1;
  }

  public float getFloat() {
    return (float) v2;
  }

  public double getDouble() {
    return v2;
  }

  public boolean getBoolean() {
    return v1 == 1;
  }

  public Object getObject() {
    return v3;
  }
}
