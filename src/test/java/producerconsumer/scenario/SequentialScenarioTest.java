package producerconsumer.scenario;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import producerconsumer.event.RecordingListener;
import producerconsumer.event.SimulationListener;
import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;
import producerconsumer.simulation.WorkerReport;

class SequentialScenarioTest {

    private static SimulationResult run(int producers, int consumers, int items, int capacity)
            throws InterruptedException {
        SimulationConfig config = new SimulationConfig(producers, consumers, items, capacity, 1_000, 0);
        return new SequentialScenario().run(config, SimulationListener.NONE);
    }

    /** 10 item, capacità 4: tre giri da 4, 4 e 2 item. */
    @Test
    void consumesEveryItemOnce() throws InterruptedException {
        SimulationResult r = run(1, 1, 10, 4);

        assertFalse(r.isTimedOut());
        assertEquals(10, r.getProduced());
        assertEquals(10, r.getConsumed());
        assertEquals(10, r.getDistinct());
        assertEquals(0, r.getLost());
        assertEquals(0, r.getDuplicated());
        assertTrue(r.getNotTerminated().isEmpty());
    }

    /** Il numero di item è un multiplo della capacità: l'ultimo giro non produce nulla. */
    @Test
    void handlesItemsMultipleOfCapacity() throws InterruptedException {
        SimulationResult r = run(1, 1, 8, 4);

        assertEquals(8, r.getProduced());
        assertEquals(8, r.getConsumed());
        assertEquals(0, r.getLost());
    }

    @Test
    void zeroItems() throws InterruptedException {
        SimulationResult r = run(1, 1, 0, 4);

        assertEquals(0, r.getProduced());
        assertEquals(0, r.getConsumed());
    }

    /** Producer e consumer della configurazione non sono usati: un solo report per tipo. */
    @Test
    void reportsOneProducerAndOneConsumer() throws InterruptedException {
        SimulationResult r = run(5, 3, 10, 4);

        List<WorkerReport> producers = r.getProducers();
        List<WorkerReport> consumers = r.getConsumers();
        assertEquals(1, producers.size());
        assertEquals(1, consumers.size());
        assertEquals(SequentialScenario.PRODUCER_NAME, producers.get(0).getName());
        assertEquals(SequentialScenario.CONSUMER_NAME, consumers.get(0).getName());
        assertEquals(10, producers.get(0).getCount());
        assertEquals(10, consumers.get(0).getCount());
        assertFalse(producers.get(0).wasInterrupted());
        assertFalse(consumers.get(0).wasInterrupted());
    }

    /** 3 item, capacità 2: riempimento con 2, svuotamento, poi 1 item. */
    @Test
    void notifiesEventsInFillAndDrainOrder() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(1, 1, 3, 2, 1_000, 0);
        RecordingListener listener = new RecordingListener();
        new SequentialScenario().run(config, listener);

        List<String> expected = List.of(
                "beforePut producer-1", "onPut producer-1 0",
                "beforePut producer-1", "onPut producer-1 1",
                "beforeTake consumer-1", "onTake consumer-1 0",
                "beforeTake consumer-1", "onTake consumer-1 1",
                "beforePut producer-1", "onPut producer-1 2",
                "beforeTake consumer-1", "onTake consumer-1 2");
        assertEquals(expected, listener.events());
    }

    /** 2 item: 2 put e 2 take, ognuno seguito da una pausa di 30 ms. */
    @Test
    void pausesAfterEachOperation() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(1, 1, 2, 2, 1_000, 30);

        long start = System.nanoTime();
        new SequentialScenario().run(config, SimulationListener.NONE);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMillis >= 120, "quattro pause da 30 ms: " + elapsedMillis + " ms");
    }
}
