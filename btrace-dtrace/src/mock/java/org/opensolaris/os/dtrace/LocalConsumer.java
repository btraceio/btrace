package org.opensolaris.os.dtrace;

import java.io.File;

public class LocalConsumer implements Consumer {
  @Override
  public void open() {}

  @Override
  public void setOption(String argref, String s) {}

  @Override
  public void grabProcess(int pid) {}

  @Override
  public void close() {}

  @Override
  public Aggregate getAggregate() throws DTraceException {
    return null;
  }

  @Override
  public void addConsumerListener(ConsumerListener consumerListener) {}

  @Override
  public void go(ExceptionHandler exceptionHandler) {}

  @Override
  public void enable() {}

  @Override
  public void compile(File program, String[] args) {}

  @Override
  public void compile(String program, String[] args) {}
}
