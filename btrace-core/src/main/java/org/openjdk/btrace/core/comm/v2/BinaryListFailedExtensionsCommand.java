package org.openjdk.btrace.core.comm.v2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Binary implementation of the ListFailedExtensionsCommand.
 * This command is used to list extensions that failed to load.
 */
public class BinaryListFailedExtensionsCommand extends BinaryCommand {
    private final List<String> failures = new ArrayList<>();

    static {
        BinaryCommand.registerCommand(LIST_FAILED_EXTENSIONS, BinaryListFailedExtensionsCommand::new);
    }

    public BinaryListFailedExtensionsCommand() {
        super(LIST_FAILED_EXTENSIONS, true);
    }

    public void setFailures(Collection<String> failures) {
        this.failures.clear();
        if (failures != null) {
            this.failures.addAll(failures);
        }
    }

    public List<String> getFailures() {
        return new ArrayList<>(failures);
    }

    @Override
    protected void write(OutputStream out) throws IOException {
        BinaryProtocol.writeInt(out, failures.size());
        for (String failure : failures) {
            BinaryProtocol.writeString(out, failure);
        }
    }

    @Override
    protected void read(InputStream in) throws IOException {
        int count = BinaryProtocol.readInt(in);
        failures.clear();
        for (int i = 0; i < count; i++) {
            failures.add(BinaryProtocol.readString(in));
        }
    }
}
