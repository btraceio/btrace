package resources.issues;

public class BTRACE256 {
  public java.util.Random r = new java.util.Random(System.currentTimeMillis());

  public static void main(String[] args) throws Exception {

    Thread t =
        new Thread(
            new Runnable() {
              @Override
              public void run() {
                BTRACE256 test = new BTRACE256();
                try {
                  while (true) {
                    test.doStuff();
                  }
                } catch (Exception e) {
                }
              }
            });

    t.start();
    t.join();
  }

  public void doStuff() throws Exception {
    Thread.sleep(r.nextInt(2000));
    System.out.print(".");
  }
}
