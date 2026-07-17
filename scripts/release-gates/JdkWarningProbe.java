import io.btrace.core.JavaVersionCheck;

public final class JdkWarningProbe {
  private JdkWarningProbe() {}

  public static void main(String[] args) {
    JavaVersionCheck.warnIfDeprecatedJvm();
    // Deliberately repeat in this same JVM: the release gate requires the warning's once-only
    // guard to suppress a second invocation on deprecated JDKs.
    JavaVersionCheck.warnIfDeprecatedJvm();
    System.out.println("JAVA_FEATURE=" + JavaVersionCheck.javaFeatureVersion());
  }
}
