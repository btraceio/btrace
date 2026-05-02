package org.openjdk.btrace.test.ext;

import org.openjdk.btrace.core.extensions.Extension;

public final class ExternalTypeTestServiceImpl extends Extension implements ExternalTypeTestService {
  @Override
  public String tag() {
    return ExternalDataType$Ext.tag();
  }

  @Override
  public int value(Object externalData) {
    return ExternalDataType$Ext.value(externalData);
  }
}
