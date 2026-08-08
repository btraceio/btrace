package example;

import java.lang.reflect.Constructor;

public final class ExternalTypeSmoke {
  private ExternalTypeSmoke() {}

  public static void main(String[] args) throws Exception {
    Class<?> parentType = Class.forName("example.target.ExternalParent");
    Constructor<?> constructor = parentType.getConstructor();
    Object parent = constructor.newInstance();
    Object child = ParentApi$Ext.child(parent);
    if (!"external-type-overload-text-ok".equals(ParentApi$Ext.describeText(parent, "text"))) {
      throw new AssertionError();
    }
    if (!"external-type-overload-child-ok".equals(ParentApi$Ext.describeChild(parent, child))) {
      throw new AssertionError();
    }
    if (!"external-type-overload-underscore-ok".equals(ParentApi$Ext.underscore(parent))) {
      throw new AssertionError();
    }
    System.out.println(ChildApi$Ext.marker(child));
    System.out.println("external-type-overload-text-ok");
  }
}
