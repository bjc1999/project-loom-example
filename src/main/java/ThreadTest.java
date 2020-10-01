import lombok.SneakyThrows;

public class ThreadTest {
    static int a;
    public static void main(String[] args) throws InterruptedException {
        int CONCURRENT_THREAD_COUNT = 1_000_000;
        Thread[] allThreads = new Thread[CONCURRENT_THREAD_COUNT];
        long start = System.currentTimeMillis();
        for(int l=0; l<CONCURRENT_THREAD_COUNT;l++) {
            Thread t = new Thread(new Runnable() {
                @SneakyThrows
                @Override
                public void run() {
                    for (int i = 0; i < 1_000; i++) {
                        a = 123 * 678;
                    }
                    Thread.sleep(5000);
                }
            });
            t.start();
            allThreads[l] = t;
        }
        long end = System.currentTimeMillis();
        System.out.println("Time elapsed: " + (end-start)/1000 + "s");
    }
}