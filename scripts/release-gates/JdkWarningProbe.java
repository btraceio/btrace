import io.btrace.core.JavaVersionCheck;

public final class JdkWarningProbe {
  private JdkWarningProbe() {}

  public static void main(String[] args) {
    JavaVersionCheck.warnIfDeprecatedJvm();
    JavaVersionCheck.warnIfDeprecatedJvm();
    System.out.println("JAVA_FEATURE=" + JavaVersionCheck.javaFeatureVersion());
  }
}
