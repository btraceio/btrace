import io.btrace.core.JavaVersionCheck;

public final class JdkWarningProbe {
  private JdkWarningProbe() {}

  public static void main(String[] args) {
    // Invoke twice on purpose: the gate requires exactly one warning on Java < 17, which
    // proves the once-per-JVM guard, not just that the warning exists.
    JavaVersionCheck.warnIfDeprecatedJvm();
    JavaVersionCheck.warnIfDeprecatedJvm();
    System.out.println("JAVA_FEATURE=" + JavaVersionCheck.javaFeatureVersion());
  }
}
