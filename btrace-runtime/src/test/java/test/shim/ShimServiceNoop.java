package test.shim;

public final class ShimServiceNoop implements ShimService {
  public static final ShimServiceNoop INSTANCE = new ShimServiceNoop();
  private ShimServiceNoop() {}

  @Override
  public int value() { return 42; }
}

