public class FiberTest {
    static int a;
    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        int CONCURRENT_THREAD_COUNT = 1_000_000;
        Fiber[] allFibers = new Fiber[CONCURRENT_THREAD_COUNT];
        for(int l=0; l<CONCURRENT_THREAD_COUNT;l++) {
            Fiber t = Fiber.schedule(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1_000; i++) {
                        a = 123 * 678;
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
            allFibers[l] = t;
        }
        long end = System.currentTimeMillis();
        System.out.println("Time elapsed: " + (end-start)/1000 + "s");
    }
}
