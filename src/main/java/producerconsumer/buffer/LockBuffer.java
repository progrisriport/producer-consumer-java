package producerconsumer.buffer;

import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import producerconsumer.model.Item;

/**
 * Buffer sincronizzato con ReentrantLock e due Condition.
 *
 * Differenza rispetto a MonitorBuffer: producer e consumer attendono su due code
 * separate (notFull e notEmpty), quindi ogni segnale risveglia solo thread del tipo corretto.
 */
public class LockBuffer implements Buffer {

    private final LinkedList<Item> queue;
    private final int capacity;
    private final Lock lock;
    private final Condition notFull;
    private final Condition notEmpty;

    public LockBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new LinkedList<Item>();
        this.capacity = capacity;
        this.lock = new ReentrantLock();
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }

    @Override
    public void put(Item item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("item must not be null");
        }
        lock.lock();
        // try/finally: il lock va rilasciato anche se await lancia InterruptedException
        try {
            while (queue.size() == capacity) {
                notFull.await();
            }
            queue.addLast(item);
            // signal è sufficiente: su notEmpty attendono solo consumer e il put rende disponibile un solo item
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Item take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }
            Item item = queue.removeFirst();
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
