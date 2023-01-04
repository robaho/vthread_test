import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class VThreadTest {
    final List<Consumer> consumers = new ArrayList<>();
    final AtomicInteger counter = new AtomicInteger();
    volatile Thread main;

    private class Producer implements Runnable {
        private final int n_messages;

        public Producer(int n_messages) {
            this.n_messages = n_messages;
        }
        public void run() {
            Random r = new Random();
            for(int i=0;i<n_messages;i++) {
                Object o = Integer.valueOf(r.nextInt(100));
                for (Consumer c : consumers) {
                    try {
                        c.submit(o);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            if(counter.decrementAndGet()==0)
                main.interrupt();;
        }
    }

    private class Consumer implements Runnable {
        private final int n_messages;
//        private final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(16);
        private final RingBuffer<Object> queue = new RingBuffer<>(16);
        public Consumer(int n_messages) {
            this.n_messages = n_messages;
        }
        @Override
        public void run() {
            for(int i=0;i<n_messages;i++) {
                try {
                    Object o = queue.take();
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
    public VThreadTest(int n_producers, int n_consumers, int n_messages) {
        main=Thread.currentThread();
        counter.set(n_consumers+n_producers);
        for(int i=0;i<n_consumers;i++) {
            Consumer c = new Consumer(n_messages*n_producers);
            consumers.add(c);
        }
        for(Consumer c : consumers) {
//            Thread t = new Thread(c,"Consumer");
//            t.start();
            Thread.startVirtualThread(c);
        }
        for(int i=0;i<n_producers;i++) {
            Producer p = new Producer(n_messages);
//            Thread t = new Thread(p,"Producer");
//            t.start();
            Thread.startVirtualThread(p);
        }
    }

    public static void main(String[] args) {
        if(args.length !=3)
            throw new IllegalArgumentException("usage: VThreadTest n_producers n_consumers n_messages");

        int n_producers = Integer.parseInt(args[0]);
        int n_consumers = Integer.parseInt(args[1]);
        int n_messages = Integer.parseInt(args[2]);

        long start = System.currentTimeMillis();
        VThreadTest m = new VThreadTest(n_producers,n_consumers,n_messages);
        try {
            synchronized (VThreadTest.class) {
                VThreadTest.class.wait();
            }
        } catch (InterruptedException e) {}
        if(m.counter.get()!=0) {
            throw new IllegalStateException("counter is not 0");
        }

        System.out.format("time = %.3f",((System.currentTimeMillis()-start)/1000.0));
    }
}
