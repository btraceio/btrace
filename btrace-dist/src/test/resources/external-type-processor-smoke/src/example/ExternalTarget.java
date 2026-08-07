package example.target;

public final class ExternalTarget {
  private ExternalTarget() {}

  public static String marker() {
    return "external-type-explicit-ok";
  }
}
