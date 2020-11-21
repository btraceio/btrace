package dummy;

import jdk.jfr.Category;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Registered;

@Label("Simple Event")
@Registered
@Enabled
@Category("BTrace")
@Name("btrace.SimpleEvent")
public class SimpleEvent extends Event {
  @Label("value")
  private long value;

  public SimpleEvent() {}

  public long getValue() {
    return value;
  }

  public void setValue(long value) {
    this.value = value;
  }
}
