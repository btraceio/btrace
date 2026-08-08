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
package io.btrace.test.ext;

import io.btrace.core.extensions.Extension;
import io.btrace.extension.util.ClassLoadingUtil;

public final class ExternalTypeTestServiceImpl extends Extension
    implements ExternalTypeTestService {
  @Override
  public String tag() {
    return ExternalDataType$Ext.tag();
  }

  @Override
  public String explicitTag(Object externalData) {
    ClassLoader applicationLoader = ClassLoadingUtil.definingLoader(externalData);
    if (applicationLoader == null) applicationLoader = ClassLoader.getSystemClassLoader();
    return ExternalDataType$Ext.explicitTag(applicationLoader);
  }

  @Override
  public String childMarker(Object externalData) {
    Object child = ExternalDataType$Ext.child(externalData);
    return ExternalChildType$Ext.marker(child);
  }

  @Override
  public String overloadMarkers(Object externalData) {
    Object child = ExternalDataType$Ext.child(externalData);
    return ExternalDataType$Ext.describeText(externalData, "ok")
        + ","
        + ExternalDataType$Ext.describeChild(externalData, child);
  }

  @Override
  public String acceptedChildMarker(Object externalData) {
    Object child = ExternalDataType$Ext.child(externalData);
    return ExternalDataType$Ext.acceptChild(externalData, child);
  }

  @Override
  public int value(Object externalData) {
    return ExternalDataType$Ext.value(externalData);
  }
}
