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
package org.example.btrace.hadoop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.example.btrace.hadoop.impl.HadoopApiImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HadoopApiImplTest {
  @BeforeEach
  void reset() {
    Path.reset();
    FileSystem.reset();
  }

  @Test
  void openUsesReflectivePathAndFileSystemMethods() {
    HadoopApiImpl api = new HadoopApiImpl();

    api.onOpen(new FileSystem(URI.create("hdfs://cluster-a")), new Path("/tmp/input"));

    assertTrue(Path.toStringCalled, "Expected Path.toString() to be invoked");
    assertTrue(FileSystem.getUriCalled, "Expected FileSystem.getUri() to be invoked");
  }

  @Test
  void createUsesReflectivePathAndFileSystemMethods() {
    HadoopApiImpl api = new HadoopApiImpl();

    api.onCreate(new FileSystem(URI.create("hdfs://cluster-b")), new Path("/tmp/output"));

    assertTrue(Path.toStringCalled, "Expected Path.toString() to be invoked");
    assertTrue(FileSystem.getUriCalled, "Expected FileSystem.getUri() to be invoked");
  }
}
