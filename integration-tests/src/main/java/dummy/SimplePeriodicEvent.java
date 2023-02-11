package dummy;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Period;

@Label("Simple periodic event")
@Period("everyChunk")
@Category("BTrace")
@Enabled
public class SimplePeriodicEvent extends Event {
  @Label("value")
  private int value;

  public SimplePeriodicEvent() {}

  public void setValue(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
