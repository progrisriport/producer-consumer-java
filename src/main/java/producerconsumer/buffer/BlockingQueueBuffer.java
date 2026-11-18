package producerconsumer.buffer;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import producerconsumer.model.Item;

/**
 * Buffer basato su ArrayBlockingQueue della libreria standard.
 *
 * La sincronizzazione è interna alla coda (un ReentrantLock e due Condition,
 * come LockBuffer). put e take sono delegati.
 */
public class BlockingQueueBuffer implements Buffer {

    private final BlockingQueue<Item> queue;
    private final int capacity;

    public BlockingQueueBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new ArrayBlockingQueue<Item>(capacity);
        this.capacity = capacity;
    }

    @Override
    public void put(Item item) throws InterruptedException {
        // ArrayBlockingQueue rifiuta già null con NullPointerException
        queue.put(item);
    }

    @Override
    public Item take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
