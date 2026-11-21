package producerconsumer.simulation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import producerconsumer.buffer.BlockingQueueBuffer;
import producerconsumer.buffer.Buffer;
import producerconsumer.buffer.LockBuffer;
import producerconsumer.buffer.MonitorBuffer;
import producerconsumer.buffer.UnsafeBuffer;
import producerconsumer.model.Item;

/**
 * Invarianti della simulazione: con buffer corretti ogni item prodotto è consumato una volta;
 * con buffer che bloccano la simulazione termina comunque entro timeout + margine.
 */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
class SimulationTest {

    private static Buffer createBuffer(String name, int capacity) {
        switch (name) {
            case "monitor":
                return new MonitorBuffer(capacity);
            case "lock":
                return new LockBuffer(capacity);
            case "blocking-queue":
                return new BlockingQueueBuffer(capacity);
            default:
                throw new IllegalArgumentException("unknown buffer: " + name);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"monitor", "lock", "blocking-queue"})
    void safeBufferConsumesEveryItemOnce(String name) throws InterruptedException {
        SimulationConfig config = new SimulationConfig(4, 3, 10_000, 5, 5_000);
        SimulationResult r = new Simulation(createBuffer(name, config.getCapacity()), config).run();

        assertFalse(r.isTimedOut());
        assertEquals(10_000, r.getProduced());
        assertEquals(10_000, r.getConsumed());
        assertEquals(10_000, r.getDistinct());
        assertEquals(0, r.getLost());
        assertEquals(0, r.getDuplicated());
        assertEquals(4, r.getProducers().size());
        assertEquals(3, r.getConsumers().size());
        assertTrue(r.getNotTerminated().isEmpty());
        assertTrue(r.getDeadlocked().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"monitor", "lock", "blocking-queue"})
    void worksWithMoreConsumersThanProducers(String name) throws InterruptedException {
        SimulationConfig config = new SimulationConfig(1, 5, 1_000, 2, 5_000);
        SimulationResult r = new Simulation(createBuffer(name, config.getCapacity()), config).run();

        assertFalse(r.isTimedOut());
        assertEquals(1_000, r.getConsumed());
        assertEquals(0, r.getLost());
    }

    @Test
    void zeroItemsTerminatesCleanly() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(2, 2, 0, 1, 5_000);
        SimulationResult r = new Simulation(new MonitorBuffer(1), config).run();

        assertFalse(r.isTimedOut());
        assertEquals(0, r.getProduced());
        assertEquals(0, r.getConsumed());
    }

    /** Il buffer non sincronizzato può perdere anche le pillole: si verifica solo che il run termini. */
    @Test
    void unsafeBufferAlwaysReturns() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(2, 2, 10_000, 5, 1_000);
        SimulationResult r = new Simulation(new UnsafeBuffer(5), config).run();

        assertTrue(r.getNotTerminated().isEmpty(), "i thread del buffer non sincronizzato sono interrompibili");
    }

    /** Buffer finto: put e take attendono per sempre, in modo interrompibile. */
    private static class StuckBuffer implements Buffer {
        @Override
        public synchronized void put(Item item) throws InterruptedException {
            while (true) {
                wait();
            }
        }

        @Override
        public synchronized Item take() throws InterruptedException {
            while (true) {
                wait();
            }
        }

        @Override
        public int capacity() {
            return 1;
        }
    }

    @Test
    void timeoutStopsBlockedThreads() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(2, 2, 10, 1, 200);
        long start = System.nanoTime();
        SimulationResult r = new Simulation(new StuckBuffer(), config).run();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(r.isTimedOut());
        assertTrue(r.getNotTerminated().isEmpty(), "thread interrompibili: tutti terminati");
        assertTrue(r.getDeadlocked().isEmpty());
        for (WorkerReport report : r.getProducers()) {
            assertTrue(report.wasInterrupted(), report.getName());
        }
        for (WorkerReport report : r.getConsumers()) {
            assertTrue(report.wasInterrupted(), report.getName());
        }
        assertTrue(elapsed < 200 + Simulation.GRACE_MILLIS, "durata: " + elapsed + " ms");
    }

    /**
     * Buffer finto: put non fa nulla; take porta i due consumer in deadlock sui monitor
     * (consumer-1 prende A poi B, consumer-2 prende B poi A). I thread BLOCKED non rispondono
     * all'interruzione: restano vivi (daemon) dopo il test.
     */
    private static class DeadlockBuffer implements Buffer {
        private final Object lockA = new Object();
        private final Object lockB = new Object();
        private final CountDownLatch bothHoldFirstLock = new CountDownLatch(2);

        @Override
        public void put(Item item) {
        }

        @Override
        public Item take() throws InterruptedException {
            boolean first = Thread.currentThread().getName().equals("consumer-1");
            Object outer;
            Object inner;
            if (first) {
                outer = lockA;
                inner = lockB;
            } else {
                outer = lockB;
                inner = lockA;
            }
            synchronized (outer) {
                bothHoldFirstLock.countDown();
                bothHoldFirstLock.await();
                synchronized (inner) {
                    return Item.POISON;
                }
            }
        }

        @Override
        public int capacity() {
            return 1;
        }
    }

    @Test
    void detectsMonitorDeadlock() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(1, 2, 0, 1, 300);
        SimulationResult r = new Simulation(new DeadlockBuffer(), config).run();

        assertTrue(r.isTimedOut());
        assertTrue(r.getDeadlocked().containsAll(List.of("consumer-1", "consumer-2")));
        assertTrue(r.getNotTerminated().containsAll(List.of("consumer-1", "consumer-2")));
    }
}
