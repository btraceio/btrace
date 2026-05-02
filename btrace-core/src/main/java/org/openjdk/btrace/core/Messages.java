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
package org.openjdk.btrace.core;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public final class Messages {
  private static final ResourceBundle messages;

  static {
    messages =
        ResourceBundle.getBundle(
            "org.openjdk.btrace.core.messages",
            Locale.getDefault(),
            Messages.class.getClassLoader());
  }

  private Messages() {}

  public static String get(String key) {
    return messages.getString(key);
  }

  public static String format(String key, Object... args) {
    return MessageFormat.format(get(key), args);
  }
}
