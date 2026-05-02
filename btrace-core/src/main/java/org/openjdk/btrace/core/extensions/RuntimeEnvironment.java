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
package org.openjdk.btrace.core.extensions;

/**
 * Provides runtime environment information for {@link ExtensionConfigurator} to detect application
 * context and make probe selection decisions.
 *
 * <p>This interface allows configurators to detect which framework or role the JVM is running
 * (e.g., Spark driver vs executor, Hadoop namenode vs datanode) by checking for presence of
 * classes, system properties, or environment variables.
 */
public interface RuntimeEnvironment {

  /**
   * Checks if a class is available in the application classloader.
   *
   * <p>This is useful for detecting frameworks or roles, e.g.:
   *
   * <ul>
   *   <li>{@code hasClass("org.apache.spark.SparkContext")} - Spark driver
   *   <li>{@code hasClass("org.apache.spark.executor.Executor")} - Spark executor
   *   <li>{@code hasClass("org.apache.hadoop.hdfs.server.namenode.NameNode")} - HDFS NameNode
   * </ul>
   *
   * @param className fully qualified class name
   * @return true if the class can be loaded
   */
  boolean hasClass(String className);

  /**
   * Gets a system property value.
   *
   * @param key the property key
   * @return the property value, or null if not set
   */
  String getSystemProperty(String key);

  /**
   * Gets a system property value with a default.
   *
   * @param key the property key
   * @param defaultValue value to return if property is not set
   * @return the property value, or defaultValue if not set
   */
  String getSystemProperty(String key, String defaultValue);

  /**
   * Gets an environment variable value.
   *
   * @param name the environment variable name
   * @return the value, or null if not set
   */
  String getEnv(String name);

  /**
   * Returns the application classloader (typically the thread context classloader).
   *
   * @return the classloader, or null if not available
   */
  ClassLoader getClassLoader();

  /**
   * Returns the JVM's main class name, if available.
   *
   * @return the main class name, or null if not determinable
   */
  String getMainClassName();
}
