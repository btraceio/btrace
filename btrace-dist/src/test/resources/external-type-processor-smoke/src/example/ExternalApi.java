package example;

import io.btrace.core.extensions.ExternalType;

@ExternalType("example.target.ExternalTarget")
public interface ExternalApi {
  @ExternalType.Static
  String marker();
}
