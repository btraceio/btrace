package example;

import io.btrace.core.extensions.ExternalType;

@ExternalType("example.target.ExternalChild")
public interface ChildApi {
  String marker();
}
