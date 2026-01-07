package test.shim;

public final class ShimServiceThrow implements ShimService {
  public static final ShimServiceThrow INSTANCE = new ShimServiceThrow();
  private ShimServiceThrow() {}

  @Override
  public int value() { throw new IllegalStateException("shim-throw"); }
}

