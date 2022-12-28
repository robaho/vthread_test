import java.util.concurrent.locks.LockSupport;

/**
 * ring buffer using traditional synchronization primitives
 *
 * @param <T>
 */
public class RingBuffer<T> {
    private final Object ring[];
    private int head;
    private int tail;
    private final int size;

    public RingBuffer(int size){
        ring = new Object[size];
        this.size=size;
    }

    /** put an item in the ring buffer, blocking until space is available.
     * @param t the item
     * @throws InterruptedException if interrupted, or the ring buffer is closed
     */
    public synchronized void put(T t) throws InterruptedException {
        while (true) {
            if (ring[tail % size] != null) {
                wait();
            } else {
                ring[tail % size] = t;
                tail++;
                notify();
                return;
            }
        }
    }

    /** returns the next item available from the ring buffer, blocking
     * if not item is ready
     * @return the item
     * @throws InterruptedException if interrupted or the ring buffer is closed
     */
    public T get() throws InterruptedException {
        spin();
        synchronized(this) {
            while (true) {
                T value = (T) ring[head % size];
                if (value != null) {
                    ring[head % size] = null;
                    head++;
                    notify();
                    return value;
                } else {
                    wait();
                }
            }
        }
    }

    private void spin() {
        for(int i=0;i<5000;i++) {
            synchronized(this) {
                if(head!=tail || ring[head%size]!=null)
                    return;
            }
            Thread.yield();
        }
    }
}
