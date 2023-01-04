import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final WaitList waiters = new WaitList();
    private final int SPIN_COUNT=32;

    private static class WaitList {
        private final LinkedList<Thread> list = new LinkedList<>();
        private final AtomicBoolean lock = new AtomicBoolean();
        void add(Thread thread) {
            while(!lock.compareAndSet(false,true));
            list.add(thread);
            lock.set(false);
        }
        Thread peek() {
            while(!lock.compareAndSet(false,true));
            try {
                return list.peek();
            } finally {
                lock.set(false);
            }
        }
        void remove(Thread thread) {
            while(!lock.compareAndSet(false,true));
            list.remove(thread);
            lock.set(false);
        }
    }


    public RingBuffer(int size){
        ring = new AtomicReferenceArray<>(size);
        this.size=size;
    }

    private boolean offer(T t) {
        int _tail = tail.get();
        int _next_tail = next(_tail);
        if(ring.get(_tail)==null && _next_tail!=head){
            if(tail.compareAndSet(_tail,_next_tail)){
                if(!ring.compareAndSet(_tail,null,t)) {
                    throw new IllegalStateException("CAS failed");
                }
                return true;
            }
        }

        return false;
    }
    public void put(T t) {
        for(int i=0;i<SPIN_COUNT;i++) {
            if (offer(t)) {
                LockSupport.unpark(reader);
                return;
            }
        }
        waiters.add(Thread.currentThread());
        while(true) {
            if(offer(t)) {
                waiters.remove(Thread.currentThread());
                LockSupport.unpark(reader);
                return;
            }
            LockSupport.park();
        }
    }
    private T poll() {
        T tmp = ring.getAndSet(head,null);
        if(tmp==null)
            return null;
        head=next(head);
        return tmp;
    }
    public T take() throws InterruptedException {
        for(int i=0;i<SPIN_COUNT;i++) {
            T t = poll();
            if(t!=null) {
                LockSupport.unpark(waiters.peek());
                return t;
            }
        }
        reader = Thread.currentThread();
        try {
            while (true) {
                T t = poll();
                if(t==null) {
                    if(Thread.interrupted())
                        throw new InterruptedException();
                    LockSupport.park();
                } else {
                    LockSupport.unpark(waiters.peek());
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
}
