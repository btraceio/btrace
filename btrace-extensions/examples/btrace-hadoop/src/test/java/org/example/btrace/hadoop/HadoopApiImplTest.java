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
