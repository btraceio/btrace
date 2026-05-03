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

  @SuppressWarnings("RedundantThrows")
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
