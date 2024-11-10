package resources;

public class ThreadSpawner extends TestApp {
    public static void main(String[] args) throws Exception {
        ThreadSpawner i = new ThreadSpawner();
        i.start();
    }
    @Override
    protected void startWork() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                spawnThread();
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void spawnThread() throws InterruptedException {
        Thread t = new Thread( () -> print("thread started"));
        t.setName("testThread");
        t.start();
        t.join();
    }

    @Override
    public void print(String msg) {
        System.out.println(msg);
        System.out.flush();
    }
}