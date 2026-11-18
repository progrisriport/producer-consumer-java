package producerconsumer.buffer;

import java.util.LinkedList;
import java.util.Objects;

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
        Objects.requireNonNull(item);
        // while e non if: al risveglio la condizione va ricontrollata
        // (un altro thread può aver riempito il buffer prima di noi, o risveglio spurio)
        while (queue.size() == capacity) {
            wait();
        }
        queue.addLast(item);
        // notifyAll e non notify: nella stessa coda di attesa ci sono sia producer che consumer,
        // notify potrebbe svegliare un altro producer e lasciare i consumer addormentati
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
