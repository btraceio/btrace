package resources;

/**
 * Target class used by the @ExternalType integration tests.
 *
 * This class exists only on the target application's classpath — it is never
 * on the extension's compile classpath — which is exactly the scenario
 * @ExternalType is designed for.
 */
public final class ExternalData {
  private final int value;

  public ExternalData(int value) {
    this.value = value;
  }

  public int value() {
    return value;
  }

  public static String tag() {
    return "ext-data-ok";
  }
}
