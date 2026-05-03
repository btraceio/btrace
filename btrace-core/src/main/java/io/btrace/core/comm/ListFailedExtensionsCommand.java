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
package io.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Command to list extensions that failed to load during BTrace agent initialization.
 *
 * @since WireIO v.1
 */
public class ListFailedExtensionsCommand extends Command implements PrintableCommand {
  // List of "extensionClassName: errorMessage" entries
  private final List<String> failedExtensions = new CopyOnWriteArrayList<>();

  public ListFailedExtensionsCommand() {
    super(Command.LIST_FAILED_EXTENSIONS, true);
  }

  public void setFailedExtensions(Map<String, String> failures) {
    this.failedExtensions.clear();
    if (failures != null && !failures.isEmpty()) {
      for (Map.Entry<String, String> entry : failures.entrySet()) {
        failedExtensions.add(entry.getKey() + ": " + entry.getValue());
      }
    }
  }

  public void setFailedExtensionsList(List<String> failures) {
    this.failedExtensions.clear();
    if (failures != null && !failures.isEmpty()) {
      this.failedExtensions.addAll(failures);
    }
  }

  public List<String> getFailedExtensions() {
    return new ArrayList<>(failedExtensions);
  }

  @Override
  protected void write(ObjectOutput out) throws IOException {
    out.writeInt(failedExtensions.size());
    for (String failure : failedExtensions) {
      out.writeUTF(failure);
    }
  }

  @SuppressWarnings("RedundantThrows")
  @Override
  protected void read(ObjectInput in) throws IOException, ClassNotFoundException {
    int count = in.readInt();
    for (int i = 0; i < count; i++) {
      failedExtensions.add(in.readUTF());
    }
  }

  @Override
  public void print(PrintWriter out) {
    if (failedExtensions.isEmpty()) {
      out.println("No extension failures detected.");
    } else {
      out.println("Failed Extensions:");
      int cntr = 1;
      for (String failure : failedExtensions) {
        out.println("  " + cntr++ + ". " + failure);
      }
    }
  }
}
