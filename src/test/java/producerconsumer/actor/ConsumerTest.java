package producerconsumer.actor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import producerconsumer.buffer.Buffer;
import producerconsumer.event.RecordingListener;
import producerconsumer.event.SimulationListener;
import producerconsumer.model.Item;

/**
 * Il consumer prende item finché riceve la poison pill, registra gli item consumati
 * (pillola esclusa), non inserisce nulla nel buffer e registra l'interruzione.
 *
 * Si usa un buffer finto con una sequenza prefissata di item: quando la sequenza è
 * esaurita, take() si blocca finché il thread viene interrotto.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ConsumerTest {

    /** Buffer finto: restituisce gli item prefissati, poi si blocca. */
    private static class ScriptedBuffer implements Buffer {
        private final Deque<Item> items;
        final CountDownLatch blocked = new CountDownLatch(1);

        ScriptedBuffer(Item... items) {
            this.items = new ArrayDeque<>(List.of(items));
        }

        @Override
        public void put(Item item) {
            throw new UnsupportedOperationException("il consumer non deve chiamare put()");
        }

        @Override
        public synchronized Item take() throws InterruptedException {
            if (!items.isEmpty()) {
                return items.poll();
            }
            blocked.countDown();
            while (true) {
                wait();
            }
        }

        synchronized int remaining() {
            return items.size();
        }

        @Override
        public int capacity() {
            return Integer.MAX_VALUE;
        }
    }

    @Test
    void isAThreadNamedAfterItsId() {
        Consumer consumer = new Consumer(3, new ScriptedBuffer(), SimulationListener.NONE, 0);
        assertInstanceOf(Thread.class, consumer);
        assertEquals("consumer-3", consumer.getName());
    }

    @Test
    void consumesUntilPoisonPill() throws InterruptedException {
        ScriptedBuffer buffer = new ScriptedBuffer(
                new Item(0, 1), new Item(1, 1), new Item(2, 2), Item.POISON, new Item(3, 1));
        Consumer consumer = new Consumer(1, buffer, SimulationListener.NONE, 0);

        consumer.start();
        consumer.join();

        assertEquals(List.of(new Item(0, 1), new Item(1, 1), new Item(2, 2)), consumer.getConsumed());
        assertFalse(consumer.wasInterrupted());
        assertEquals(1, buffer.remaining(), "dopo la pillola il consumer non prende altri item");
    }

    @Test
    void doesNotRecordPoisonPill() throws InterruptedException {
        Consumer consumer = new Consumer(1, new ScriptedBuffer(new Item(0, 1), Item.POISON),
                SimulationListener.NONE, 0);

        consumer.start();
        consumer.join();

        for (Item item : consumer.getConsumed()) {
            assertFalse(item.isPoison());
        }
    }

    @Test
    void terminatesImmediatelyOnPoisonPill() throws InterruptedException {
        Consumer consumer = new Consumer(1, new ScriptedBuffer(Item.POISON), SimulationListener.NONE, 0);

        consumer.start();
        consumer.join();

        assertTrue(consumer.getConsumed().isEmpty());
        assertFalse(consumer.wasInterrupted());
    }

    /** I duplicati vengono registrati così come arrivano: il conteggio è compito della simulazione. */
    @Test
    void recordsDuplicatesAsReceived() throws InterruptedException {
        Consumer consumer = new Consumer(1, new ScriptedBuffer(new Item(5, 2), new Item(5, 2), Item.POISON),
                SimulationListener.NONE, 0);

        consumer.start();
        consumer.join();

        assertEquals(List.of(new Item(5, 2), new Item(5, 2)), consumer.getConsumed());
    }

    @Test
    void consumedListIsReadOnly() throws InterruptedException {
        Consumer consumer = new Consumer(1, new ScriptedBuffer(new Item(0, 1), Item.POISON),
                SimulationListener.NONE, 0);

        consumer.start();
        consumer.join();

        try {
            consumer.getConsumed().add(new Item(9, 9));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    /**
     * Pillola mai arrivata: il consumer resta in attesa su take() finché viene interrotto,
     * poi termina e registra il flag. Gli item già consumati restano registrati.
     */
    @Test
    void stopsAndRecordsInterruptionWhenWaitingOnTake() throws InterruptedException {
        ScriptedBuffer buffer = new ScriptedBuffer(new Item(0, 1), new Item(1, 1));
        Consumer consumer = new Consumer(1, buffer, SimulationListener.NONE, 0);

        consumer.start();
        assertTrue(buffer.blocked.await(2, TimeUnit.SECONDS), "il consumer deve arrivare al terzo take");
        consumer.interrupt();
        consumer.join(2000);

        assertFalse(consumer.isAlive(), "il consumer interrotto deve terminare");
        assertTrue(consumer.wasInterrupted());
        assertEquals(List.of(new Item(0, 1), new Item(1, 1)), consumer.getConsumed());
    }

    /** La pillola genera solo beforeTake: il consumer non sa in anticipo cosa riceverà. */
    @Test
    void notifiesBeforeEachTakeAndAfterEachItem() throws InterruptedException {
        RecordingListener listener = new RecordingListener();
        Consumer consumer = new Consumer(1, new ScriptedBuffer(new Item(0, 1), Item.POISON), listener, 0);

        consumer.start();
        consumer.join();

        List<String> expected = List.of(
                "beforeTake consumer-1", "onTake consumer-1 0", "beforeTake consumer-1");
        assertEquals(expected, listener.events());
    }

    @Test
    void pausesAfterEachItem() throws InterruptedException {
        ScriptedBuffer buffer = new ScriptedBuffer(
                new Item(0, 1), new Item(1, 1), new Item(2, 1), Item.POISON);
        Consumer consumer = new Consumer(1, buffer, SimulationListener.NONE, 50);

        long start = System.nanoTime();
        consumer.start();
        consumer.join();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(3, consumer.getConsumed().size());
        assertTrue(elapsedMillis >= 150, "tre pause da 50 ms: " + elapsedMillis + " ms");
    }

    /** L'interruzione durante la pausa termina il thread; l'item precedente resta registrato. */
    @Test
    void stopsWhenInterruptedDuringPause() throws InterruptedException {
        final CountDownLatch firstTake = new CountDownLatch(1);
        SimulationListener listener = new SimulationListener() {
            @Override
            public void beforePut(String thread) {
            }

            @Override
            public void onPut(String thread, Item item) {
            }

            @Override
            public void beforeTake(String thread) {
            }

            @Override
            public void onTake(String thread, Item item) {
                firstTake.countDown();
            }
        };
        ScriptedBuffer buffer = new ScriptedBuffer(new Item(0, 1), new Item(1, 1), Item.POISON);
        Consumer consumer = new Consumer(1, buffer, listener, 10_000);

        consumer.start();
        assertTrue(firstTake.await(2, TimeUnit.SECONDS), "il consumer deve prelevare il primo item");
        consumer.interrupt();
        consumer.join(2000);

        assertFalse(consumer.isAlive(), "il consumer interrotto deve terminare");
        assertTrue(consumer.wasInterrupted());
        assertEquals(List.of(new Item(0, 1)), consumer.getConsumed());
    }
}
