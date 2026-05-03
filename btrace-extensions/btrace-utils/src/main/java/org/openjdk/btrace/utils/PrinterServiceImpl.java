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
package org.openjdk.btrace.utils;

import org.openjdk.btrace.core.extensions.Extension;

public final class PrinterServiceImpl extends Extension implements PrinterService {
  private static final String NL = System.getProperty("line.separator");

  @Override
  public void print(String s) {
    if (s != null) {
      getContext().send(s);
    }
  }

  @Override
  public void println(String s) {
    print(s + NL);
  }

  @Override
  public void println() {
    print(NL);
  }

  @Override
  public void close() {
    if (getContext().getArgs().containsKey("extensionCloseTest")) {
      getContext().send("extension close: btrace-utils");
    }
  }
}
