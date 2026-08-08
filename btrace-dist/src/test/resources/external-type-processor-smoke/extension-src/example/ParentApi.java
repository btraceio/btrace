package example;

import io.btrace.core.extensions.ExternalType;

@ExternalType("example.target.ExternalParent")
public interface ParentApi {
  @ExternalType.Type(ChildApi.class)
  Object child();
}
