package example;

import io.btrace.core.extensions.ExternalType;

@ExternalType("example.target.ExternalParent")
public interface ParentApi {
  @ExternalType.Getter("name")
  String name();

  @ExternalType.Setter("name")
  void setName(String name);

  @ExternalType.Static
  @ExternalType.Getter("DEFAULT_CHILD")
  @ExternalType.Type(ChildApi.class)
  Object defaultChild();

  @ExternalType.Constructor
  Object create(String name);

  @ExternalType.InstanceOf
  boolean isParent(Object value);

  @ExternalType.Cast
  Object castParent(Object value);

  @ExternalType.Getter("childField")
  @ExternalType.Type(ChildApi.class)
  Object childField();

  @ExternalType.Setter("childField")
  void setChildField(@ExternalType.Type(ChildApi.class) Object child);

  @ExternalType.Constructor
  Object createWithChild(@ExternalType.Type(ChildApi.class) Object child);

  @ExternalType.Type(ChildApi.class)
  Object child();

  @ExternalType.Overload("describe")
  String describeText(String text);

  @ExternalType.Overload("describe")
  String describeChild(@ExternalType.Type(ChildApi.class) Object child);

  @ExternalType.Overload("_")
  String underscore();

  @ExternalType.Overload("_")
  String underscoreWithText(String ignored);
}
