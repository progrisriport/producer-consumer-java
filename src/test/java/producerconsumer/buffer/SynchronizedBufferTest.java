package producerconsumer.buffer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;

import producerconsumer.model.Item;

/**
 * Contratto in più per i buffer corretti: con N producer e M consumer
 * ogni item prodotto viene consumato esattamente una volta (invariante).
 */
abstract class SynchronizedBufferTest extends BufferTest {

    private static final int PRODUCERS = 4;
    private static final int CONSUMERS = 3;
    private static final int ITEMS_PER_PRODUCER = 5000;

    @Test
    void everyItemIsConsumedExactlyOnce() throws InterruptedException {
        Buffer buffer = createBuffer(5);
        ConcurrentLinkedQueue<Item> consumed = new ConcurrentLinkedQueue<Item>();
        List<Thread> threads = new ArrayList<Thread>();

        for (int p = 0; p < PRODUCERS; p++) {
            int producerId = p;
            threads.add(new Thread(() -> {
                try {
                    for (int i = 0; i < ITEMS_PER_PRODUCER; i++) {
                        buffer.put(new Item(i, producerId));
                    }
                } catch (InterruptedException e) {
                    // fine del test
                }
            }));
        }
        for (int c = 0; c < CONSUMERS; c++) {
            threads.add(new Thread(() -> {
                try {
                    Item item = buffer.take();
                    while (!item.isPoison()) {
                        consumed.add(item);
                        item = buffer.take();
                    }
                } catch (InterruptedException e) {
                    // fine del test
                }
            }));
        }

        for (Thread t : threads) {
            t.start();
        }
        for (int p = 0; p < PRODUCERS; p++) {
            threads.get(p).join();
        }
        // una pillola per consumer: ognuno ne prende una e termina
        for (int c = 0; c < CONSUMERS; c++) {
            buffer.put(Item.POISON);
        }
        for (Thread t : threads) {
            t.join();
        }

        Set<Item> distinct = new HashSet<Item>(consumed);
        assertEquals(PRODUCERS * ITEMS_PER_PRODUCER, consumed.size(), "nessun item perso");
        assertEquals(consumed.size(), distinct.size(), "nessun item duplicato");
    }
}
