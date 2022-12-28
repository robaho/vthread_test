import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;

/**
 * ring buffer designed for multiple writers and a single reader
 *
 * @param <T>
 */
public class RingBuffer<T> {
    private final AtomicReferenceArray<T> ring;
    private int head;
    private final AtomicInteger tail = new AtomicInteger();
    private final int size;

    private volatile Thread reader;

    public RingBuffer(int size){
        ring = new AtomicReferenceArray<>(size);
        this.size=size;
    }

    public boolean offer(T t) {
        int _tail = tail.get();
        if(ring.get(_tail)==null){
            if(tail.compareAndSet(_tail,next(_tail))){
                if(!ring.compareAndSet(_tail,null,t)) {
                    throw new IllegalStateException("CAS failed");
                }
                return true;
            }
        }

        return false;
    }
    public void put(T t) {
        // spin trying to place into the queue
        while(!offer(t)) {
            if(reader!=null) {
                LockSupport.unpark(reader);
            }
//            Thread.yield(); // does not help - works under jdk 20
            LockSupport.parkNanos(1); // allows the program to work correctly
        }
        if(reader!=null) {
            LockSupport.unpark(reader);
        }
    }
    public T poll() {
        T tmp = ring.getAndSet(head,null);
        if(tmp==null)
            return null;
        head=next(head);
        return tmp;
    }
    public T get() throws InterruptedException {
//        spin();
        reader = Thread.currentThread();
        try {
            while (true) {
                T t = poll();
                if(t==null) {
                    if(Thread.interrupted())
                        throw new InterruptedException();
                    LockSupport.park();
                } else {
                    return t;
                }
            }
        } finally {
            reader=null;
        }
    }
    private int next(int index) {
        return (++index)%size;
    }

    private void spin() {
        for(int i=0;i<1000;i++) {
            if (tail.get() != head || ring.get(head) != null) {
                return;
            }
            LockSupport.parkNanos(1);
        }
    }
}
