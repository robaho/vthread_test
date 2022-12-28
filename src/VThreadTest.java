import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class VThreadTest implements Runnable {
    final RingBuffer<Object> queue = new RingBuffer<>(1024);
    final List<Consumer> consumers = new ArrayList<>();
    final AtomicInteger counter = new AtomicInteger();

    volatile Thread main;

    @Override
    public void run() {
        main = Thread.currentThread();

        while(true) {
            try {
                Object o = queue.get();
                for (Consumer c : consumers) {
                    c.submit(o);
                }
            } catch (InterruptedException e) {
                // expected when all consumers and producers complete
                return;
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
                try {
                    Object o = queue.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println("consumer finished");
            if(counter.decrementAndGet()==0)
                main.interrupt();;
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
            if(counter.decrementAndGet()==0)
                main.interrupt();;
        }
    }
    public VThreadTest(int n_producers, int n_consumers, int n_messages) {
        counter.set(n_consumers+n_producers);
        for(int i=0;i<n_consumers;i++) {
            Consumer c = new Consumer(n_messages*n_producers);
//            Thread t = new Thread(c,"Consumer");
//            t.start();
            Thread.startVirtualThread(c);
            consumers.add(c);
        }
        for(int i=0;i<n_producers;i++) {
            Producer p = new Producer(n_messages);
//            Thread t = new Thread(p,"Producer");
//            t.start();
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
