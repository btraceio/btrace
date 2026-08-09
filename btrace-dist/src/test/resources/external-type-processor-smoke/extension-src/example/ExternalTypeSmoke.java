package example;

import java.lang.reflect.Constructor;

public final class ExternalTypeSmoke {
  private ExternalTypeSmoke() {}

  public static void main(String[] args) throws Exception {
    Class<?> parentType = Class.forName("example.target.ExternalParent");
    Constructor<?> constructor = parentType.getConstructor();
    Object parent = constructor.newInstance();
    if (!"external-type-field-initial".equals(ParentApi$Ext.name(parent))) {
      throw new AssertionError();
    }
    ParentApi$Ext.setName(parent, "external-type-field-ok");
    if (!"external-type-field-ok".equals(ParentApi$Ext.name(parent))) {
      throw new AssertionError();
    }
    if (!"external-type-chain-ok".equals(ChildApi$Ext.marker(ParentApi$Ext.defaultChild()))) {
      throw new AssertionError();
    }
    Object created = ParentApi$Ext.create("external-type-constructor-ok");
    if (!ParentApi$Ext.isParent(created)
        || !"external-type-constructor-ok".equals(ParentApi$Ext.name(ParentApi$Ext.castParent(created)))) {
      throw new AssertionError();
    }
    Object child = ParentApi$Ext.child(parent);
    ParentApi$Ext.setChildField(parent, child);
    if (!"external-type-chain-ok".equals(ChildApi$Ext.marker(ParentApi$Ext.childField(parent)))) {
      throw new AssertionError();
    }
    Object childParent = ParentApi$Ext.createWithChild(child);
    if (!"external-type-chain-ok".equals(ChildApi$Ext.marker(ParentApi$Ext.childField(childParent)))) {
      throw new AssertionError();
    }
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
    System.out.println("external-type-field-ok");
    System.out.println("external-type-constructor-ok");
    System.out.println("external-type-predicate-ok");
    System.out.println("external-type-marked-field-constructor-ok");
  }
}
