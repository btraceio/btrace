package org.example.btrace.spark.api;

import org.openjdk.btrace.core.extensions.ExternalType;

/**
 * Build-time contract for {@code org.apache.spark.scheduler.SparkListenerJobStart}.
 *
 * <p>The annotation processor generates {@code SparkListenerJobStartType$Ext} in this same package
 * with lazy-resolving static dispatchers for each declared method.
 */
@ExternalType("org.apache.spark.scheduler.SparkListenerJobStart")
public interface SparkListenerJobStartType {
  int jobId();

  long time();
}
