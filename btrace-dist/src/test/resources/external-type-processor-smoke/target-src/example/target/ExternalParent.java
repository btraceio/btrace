package example.target;

public final class ExternalParent {
  public String name;
  public static ExternalChild DEFAULT_CHILD = new ExternalChild();

  public ExternalParent() {
    this("external-type-field-initial");
  }

  public ExternalParent(String name) {
    this.name = name;
  }

  public ExternalChild childField;

  public ExternalParent(ExternalChild child) {
    this.childField = child;
  }

  public ExternalChild child() {
    return new ExternalChild();
  }

  public String describe(String text) {
    return "external-type-overload-text-ok";
  }

  public String describe(ExternalChild child) {
    return "external-type-overload-child-ok";
  }

  public String _() {
    return "external-type-overload-underscore-ok";
  }

  public String _(String ignored) {
    return "external-type-overload-underscore-ok";
  }
}
