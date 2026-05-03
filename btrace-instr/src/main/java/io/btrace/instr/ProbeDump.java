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
package io.btrace.instr;

import java.io.FileInputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import io.btrace.core.SharedSettings;

public class ProbeDump {
  public static void main(String[] args) throws Exception {
    String path = args[0];

    BTraceProbeFactory bpf = new BTraceProbeFactory(SharedSettings.GLOBAL);

    BTraceProbe bp = bpf.createProbe(new FileInputStream(path));

    FileSystem fs = FileSystems.getDefault();
    Path p = fs.getPath(args[1]);
    Files.write(
        p.resolve(bp.getClassName().replace(".", "_") + "_full.class"), bp.getFullBytecode());
    Files.write(
        p.resolve(bp.getClassName().replace(".", "_") + "_dh.class"), bp.getDataHolderBytecode());
  }
}
