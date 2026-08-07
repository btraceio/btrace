package example;

public final class ExternalTypeSmoke {
  private ExternalTypeSmoke() {}

  public static void main(String[] args) {
    System.out.println(ExternalApi$Ext.marker(ExternalTypeSmoke.class.getClassLoader()));
  }
}
