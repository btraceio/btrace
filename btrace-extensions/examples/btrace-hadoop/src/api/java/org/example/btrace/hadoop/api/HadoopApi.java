package org.example.btrace.hadoop.api;

/**
 * Hadoop example API exposed to BTrace probes. Uses Object hand-off to avoid
 * bootstrap coupling to application types.
 */
public interface HadoopApi {
  void onOpen(Object fileSystem, Object path);
  void onCreate(Object fileSystem, Object path);
}

