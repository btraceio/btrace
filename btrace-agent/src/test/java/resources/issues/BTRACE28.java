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
package resources.issues;

/**
 * @author Jaroslav Bachorik jaroslav.bachorik@sun.com
 */
public class BTRACE28 {
  private void serveResource(String param1, String param2) {
    String resourceType = "resourceType";
    String contentType = "contentType";
    int indice, tempIndice;
    byte[] tempArr;
    byte[] mainArr = new byte[0];
    byte[] byteArr = new byte[65535];

    StringBuilder sb = new StringBuilder();

    try {
      sb.append("hooo");
      System.err.println("i am here");
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }
}
