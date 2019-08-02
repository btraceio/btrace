package org.openjdk.btrace.instr;

import org.openjdk.btrace.core.SharedSettings;

import java.io.FileInputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProbeDump {
    public static void main(String[] args) throws Exception {
        String path = args[0];

        BTraceProbeFactory bpf = new BTraceProbeFactory(SharedSettings.GLOBAL);

        BTraceProbe bp = bpf.createProbe(new FileInputStream(path));

        FileSystem fs = FileSystems.getDefault();
        Path p = fs.getPath(args[1]);
        Files.write(p.resolve(bp.getClassName().replace(".", "_") + "_full.class"), bp.getFullBytecode());
        Files.write(p.resolve(bp.getClassName().replace(".", "_") + "_dh.class"), bp.getDataHolderBytecode());
    }
}
