public class SpinTest {
    private static class Spinner implements Runnable {
        @Override
        public void run() {
            while(true) {
                Thread.yield();
            }
        }
    }
    public static void main(String[] args) throws InterruptedException {
        final int NTHREADS=Runtime.getRuntime().availableProcessors()+1;
        for(int i=0;i<NTHREADS;i++) {
            Thread.startVirtualThread(new Spinner());
        }
        Thread.sleep(1000);
        Thread starver = Thread.startVirtualThread(new Runnable() {
            @Override
            public void run() {
                while(true) {
                    // The following will never run...
                    System.out.println("I'm running!");
                }
            }
        });
        starver.join();
    }
}
