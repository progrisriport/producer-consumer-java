package producerconsumer.buffer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import producerconsumer.model.Item;

/**
 * Contratto comune a tutti i buffer. Ogni implementazione ha una sottoclasse
 * che dice solo come crearla; i test vengono ereditati.
 *
 * I test "si blocca" non dipendono dall'ordine dei thread (D3): controllano solo che
 * dopo un'attesa il thread sia ANCORA vivo, cosa vera in qualunque interleaving.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
abstract class BufferTest {

    protected abstract Buffer createBuffer(int capacity);

    @Test
    void returnsItemsInFifoOrder() throws InterruptedException {
        Buffer buffer = createBuffer(3);
        buffer.put(new Item(1, 0));
        buffer.put(new Item(2, 0));
        buffer.put(new Item(3, 0));
        assertEquals(new Item(1, 0), buffer.take());
        assertEquals(new Item(2, 0), buffer.take());
        assertEquals(new Item(3, 0), buffer.take());
    }

    @Test
    void exposesCapacity() {
        assertEquals(4, createBuffer(4).capacity());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> createBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> createBuffer(-1));
    }

    @Test
    void rejectsNullItem() {
        Buffer buffer = createBuffer(1);
        assertThrows(NullPointerException.class, () -> buffer.put(null));
    }

    @Test
    void reusesSlotsAfterWrapAround() throws InterruptedException {
        Buffer buffer = createBuffer(2);
        for (int i = 0; i < 10; i++) {
            buffer.put(new Item(i, 0));
            assertEquals(new Item(i, 0), buffer.take());
        }
    }

    @Test
    void takeWaitsWhileEmpty() throws InterruptedException {
        Buffer buffer = createBuffer(1);
        Thread consumer = new Thread(() -> {
            try {
                buffer.take();
            } catch (InterruptedException e) {
                // fine del test
            }
        });
        consumer.start();
        consumer.join(200);
        assertTrue(consumer.isAlive(), "take deve attendere su buffer vuoto");

        buffer.put(new Item(1, 0));
        consumer.join(2000);
        assertFalse(consumer.isAlive(), "take deve ripartire dopo un put");
    }

    @Test
    void putWaitsWhileFull() throws InterruptedException {
        Buffer buffer = createBuffer(1);
        buffer.put(new Item(1, 0));
        Thread producer = new Thread(() -> {
            try {
                buffer.put(new Item(2, 0));
            } catch (InterruptedException e) {
                // fine del test
            }
        });
        producer.start();
        producer.join(200);
        assertTrue(producer.isAlive(), "put deve attendere su buffer pieno");

        buffer.take();
        producer.join(2000);
        assertFalse(producer.isAlive(), "put deve ripartire dopo un take");
    }

    @Test
    void interruptStopsWaitingTake() throws InterruptedException {
        Buffer buffer = createBuffer(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread consumer = new Thread(() -> {
            try {
                buffer.take();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        consumer.start();
        consumer.join(200);
        consumer.interrupt();
        consumer.join(2000);
        assertFalse(consumer.isAlive());
        assertTrue(interrupted.get(), "take deve lanciare InterruptedException");
    }

    @Test
    void interruptStopsWaitingPut() throws InterruptedException {
        Buffer buffer = createBuffer(1);
        buffer.put(new Item(1, 0));
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread producer = new Thread(() -> {
            try {
                buffer.put(new Item(2, 0));
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });
        producer.start();
        producer.join(200);
        producer.interrupt();
        producer.join(2000);
        assertFalse(producer.isAlive());
        assertTrue(interrupted.get(), "put deve lanciare InterruptedException");
    }
}
