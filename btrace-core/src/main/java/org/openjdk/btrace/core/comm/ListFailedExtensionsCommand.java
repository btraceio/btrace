package org.openjdk.btrace.core.comm;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.PrintWriter;
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
