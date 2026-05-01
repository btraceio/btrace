package org.apache.spark.scheduler;

public final class StageInfo {
  public static boolean nameCalled;
  public static boolean numTasksCalled;

  private final String name;
  private final int numTasks;

  public StageInfo(String name, int numTasks) {
    this.name = name;
    this.numTasks = numTasks;
  }

  public String name() {
    nameCalled = true;
    return name;
  }

  public int numTasks() {
    numTasksCalled = true;
    return numTasks;
  }

  public static void reset() {
    nameCalled = false;
    numTasksCalled = false;
  }
}
