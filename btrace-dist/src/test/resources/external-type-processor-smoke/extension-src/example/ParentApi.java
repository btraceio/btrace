package example;

import io.btrace.core.extensions.ExternalType;

@ExternalType("example.target.ExternalParent")
public interface ParentApi {
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
