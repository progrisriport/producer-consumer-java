package producerconsumer.actor;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import producerconsumer.buffer.Buffer;
import producerconsumer.counter.IdGenerator;
import producerconsumer.counter.Quota;
import producerconsumer.model.Item;

/**
 * Il producer consuma la quota condivisa, inserisce item con id dal generatore,
 * non inserisce poison pill, conta solo i put riusciti e registra l'interruzione.
 *
 * Si usano buffer finti: registrano i put, così i test non dipendono da una
 * implementazione reale e non serve un metodo size().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProducerTest {

    /** Buffer finto: accetta tutti i put e li registra. */
    private static class RecordingBuffer implements Buffer {
        final List<Item> items = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void put(Item item) {
            items.add(item);
        }

        @Override
        public Item take() {
            throw new UnsupportedOperationException("il producer non deve chiamare take()");
        }

        @Override
        public int capacity() {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Buffer finto: accetta i primi {@code accepted} put, poi si blocca finché il thread
     * viene interrotto e lancia InterruptedException come un buffer reale.
     */
    private static class InterruptibleBlockingBuffer implements Buffer {
        final List<Item> items = Collections.synchronizedList(new ArrayList<>());
        private final int accepted;
        final CountDownLatch blocked = new CountDownLatch(1);

        InterruptibleBlockingBuffer(int accepted) {
            this.accepted = accepted;
        }

        @Override
        public void put(Item item) throws InterruptedException {
            if (items.size() < accepted) {
                items.add(item);
                return;
            }
            blocked.countDown();
            new CountDownLatch(1).await();
        }

        @Override
        public Item take() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int capacity() {
            return accepted;
        }
    }

    @Test
    void isAThreadNamedAfterItsId() {
        Producer producer = new Producer(2, new RecordingBuffer(), new IdGenerator(), new Quota(0));
        assertInstanceOf(Thread.class, producer);
        assertEquals("producer-2", producer.getName());
    }

    @Test
    void putsOneItemPerPermit() throws InterruptedException {
        RecordingBuffer buffer = new RecordingBuffer();
        Producer producer = new Producer(3, buffer, new IdGenerator(), new Quota(5));

        producer.start();
        producer.join();

        assertEquals(5, producer.getProduced());
        assertFalse(producer.wasInterrupted());
        List<Item> expected = List.of(
                new Item(0, 3), new Item(1, 3), new Item(2, 3), new Item(3, 3), new Item(4, 3));
        assertEquals(expected, buffer.items);
    }

    @Test
    void doesNotPutPoisonPill() throws InterruptedException {
        RecordingBuffer buffer = new RecordingBuffer();
        Producer producer = new Producer(1, buffer, new IdGenerator(), new Quota(10));

        producer.start();
        producer.join();

        assertTrue(buffer.items.stream().noneMatch(Item::isPoison), "il producer non inserisce la pillola");
    }

    @Test
    void terminatesImmediatelyWithZeroQuota() throws InterruptedException {
        RecordingBuffer buffer = new RecordingBuffer();
        Producer producer = new Producer(1, buffer, new IdGenerator(), new Quota(0));

        producer.start();
        producer.join();

        assertEquals(0, producer.getProduced());
        assertTrue(buffer.items.isEmpty());
        assertFalse(producer.wasInterrupted());
    }

    /**
     * Invariante: la ripartizione tra producer non è prevedibile, il totale sì.
     * Somma dei produced == quota == item nel buffer; id tutti distinti;
     * ogni producer ha inserito esattamente getProduced() item con il proprio id.
     */
    @Test
    void producersShareTheQuota() throws InterruptedException {
        int permits = 3_000;
        RecordingBuffer buffer = new RecordingBuffer();
        IdGenerator generator = new IdGenerator();
        Quota quota = new Quota(permits);

        List<Producer> producers = new ArrayList<>();
        for (int id = 1; id <= 3; id++) {
            producers.add(new Producer(id, buffer, generator, quota));
        }
        for (Producer producer : producers) {
            producer.start();
        }
        for (Producer producer : producers) {
            producer.join();
        }

        int totalProduced = producers.stream().mapToInt(Producer::getProduced).sum();
        assertEquals(permits, totalProduced);
        assertEquals(permits, buffer.items.size());

        Set<Integer> ids = new HashSet<>();
        for (Item item : buffer.items) {
            assertTrue(ids.add(item.getId()), "id duplicato: " + item.getId());
        }

        for (int id = 1; id <= 3; id++) {
            int producerId = id;
            long inBuffer = buffer.items.stream().filter(i -> i.getProducerId() == producerId).count();
            assertEquals(producers.get(id - 1).getProduced(), inBuffer);
        }
    }

    /**
     * L'interruzione durante un put bloccato termina il thread, registra il flag
     * e non conta l'item il cui put non è riuscito.
     */
    @Test
    void stopsAndRecordsInterruptionWhenBlockedOnPut() throws InterruptedException {
        InterruptibleBlockingBuffer buffer = new InterruptibleBlockingBuffer(2);
        Producer producer = new Producer(1, buffer, new IdGenerator(), new Quota(10));

        producer.start();
        assertTrue(buffer.blocked.await(2, TimeUnit.SECONDS), "il producer deve arrivare al terzo put");
        producer.interrupt();
        producer.join(2000);

        assertFalse(producer.isAlive(), "il producer interrotto deve terminare");
        assertTrue(producer.wasInterrupted());
        assertEquals(2, producer.getProduced(), "il put interrotto non conta come prodotto");
        assertEquals(2, buffer.items.size());
    }
}
