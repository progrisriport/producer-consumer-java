package producerconsumer.scenario;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;
import producerconsumer.simulation.WorkerReport;

class SequentialScenarioTest {

    private static SimulationResult run(int producers, int consumers, int items, int capacity)
            throws InterruptedException {
        SimulationConfig config = new SimulationConfig(producers, consumers, items, capacity, 1_000);
        return new SequentialScenario().run(config);
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
}
