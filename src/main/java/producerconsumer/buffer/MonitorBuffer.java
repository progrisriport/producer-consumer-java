package producerconsumer.buffer;

import java.util.LinkedList;

import producerconsumer.model.Item;

/**
 * Buffer sincronizzato con il monitor dell'oggetto: synchronized + wait/notifyAll.
 *
 * Un solo lock (this) e una sola coda di attesa per producer e consumer insieme.
 */
public class MonitorBuffer implements Buffer {

    private final LinkedList<Item> queue;
    private final int capacity;

    public MonitorBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new LinkedList<Item>();
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(Item item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("item must not be null");
        }
        // while e non if: al risveglio la condizione va ricontrollata
        // (il buffer può essere stato riempito da un altro thread, o il risveglio può essere spurio)
        while (queue.size() == capacity) {
            wait();
        }
        queue.addLast(item);
        // notifyAll e non notify: producer e consumer condividono la stessa coda di attesa,
        // notify può risvegliare un producer e lasciare in attesa tutti i consumer
        notifyAll();
    }

    @Override
    public synchronized Item take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        Item item = queue.removeFirst();
        notifyAll();
        return item;
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
