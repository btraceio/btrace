package example;

import java.lang.reflect.Constructor;

public final class ExternalTypeSmoke {
  private ExternalTypeSmoke() {}

  public static void main(String[] args) throws Exception {
    Class<?> parentType = Class.forName("example.target.ExternalParent");
    Constructor<?> constructor = parentType.getConstructor();
    Object child = ParentApi$Ext.child(constructor.newInstance());
    System.out.println(ChildApi$Ext.marker(child));
  }
}
