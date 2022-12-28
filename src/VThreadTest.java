import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class VThreadTest implements Runnable {
    final RingBuffer<Object> queue = new RingBuffer<>(1024);
    final List<Consumer> consumers = new ArrayList<>();
    final AtomicInteger counter = new AtomicInteger();

    @Override
    public void run() {
        while(true) {
            try {
                while(!queue.available()) {
                    if(counter.get()==0)
                        return;
                }
                Object o = queue.get();
                for (Consumer c : consumers) {
                    c.submit(o);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private class Consumer implements Runnable {
        private final int n_messages;
        private final RingBuffer<Object> queue = new RingBuffer<>(16);
        public Consumer(int n_messages) {
            this.n_messages = n_messages;
        }
        @Override
        public void run() {
            for(int i=0;i<n_messages;i++) {
                Object o = queue.get();
            }
            System.out.println("consumer finished");
            counter.decrementAndGet();
        }
        void submit(Object o) throws InterruptedException {
            queue.put(o);
        }
    }
    private class Producer implements Runnable {
        final int n_messages;
        public Producer(int n_messages) {
            this.n_messages = n_messages;
        }

        @Override
        public void run() {
            for(int i=0;i<n_messages;i++) {
                queue.put(new Object());
            }
            System.out.println("producer finished");
            counter.decrementAndGet();
        }
    }
    public VThreadTest(int n_producers, int n_consumers, int n_messages) {
        counter.set(n_consumers+n_producers);
        for(int i=0;i<n_consumers;i++) {
            Consumer c = new Consumer(n_messages*n_producers);
            Thread.startVirtualThread(c);
            consumers.add(c);
        }
        for(int i=0;i<n_producers;i++) {
            Thread.startVirtualThread(new Producer(n_messages));
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if(args.length !=3)
            throw new IllegalArgumentException("usage: VThreadTest n_producers n_consumers n_messages");

        int n_producers = Integer.parseInt(args[0]);
        int n_consumers = Integer.parseInt(args[1]);
        int n_messages = Integer.parseInt(args[2]);

        VThreadTest m = new VThreadTest(n_producers,n_consumers,n_messages);
//        Thread t = Thread.startVirtualThread(m);
        long start = System.currentTimeMillis();
        Thread t = new Thread(m,"VThreadTest");
        t.start();
        t.join();
        System.out.format("time = %.3f",((System.currentTimeMillis()-start)/1000.0));
    }
}
