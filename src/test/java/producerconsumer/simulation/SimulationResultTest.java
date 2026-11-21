package producerconsumer.simulation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import producerconsumer.model.Item;

class SimulationResultTest {

    private static SimulationResult result(List<WorkerReport> producers, List<Item> consumed) {
        List<WorkerReport> consumers = List.of(new WorkerReport("consumer-1", consumed.size(), false));
        return new SimulationResult(producers, consumers, consumed, false, List.of(), 10);
    }

    @Test
    void allItemsConsumedOnce() {
        SimulationResult r = result(
                List.of(new WorkerReport("producer-1", 3, false)),
                List.of(new Item(0, 1), new Item(1, 1), new Item(2, 1)));

        assertEquals(3, r.getProduced());
        assertEquals(3, r.getConsumed());
        assertEquals(3, r.getDistinct());
        assertEquals(0, r.getLost());
        assertEquals(0, r.getDuplicated());
        assertTrue(r.getDuplicates().isEmpty());
    }

    /** 5 prodotti; consumati 0, 1, 1, 1, 3: distinti 3, persi 2 (id 2 e 4), duplicati 2. */
    @Test
    void countsLostAndDuplicated() {
        SimulationResult r = result(
                List.of(new WorkerReport("producer-1", 3, false), new WorkerReport("producer-2", 2, false)),
                List.of(new Item(0, 1), new Item(1, 2), new Item(1, 2), new Item(1, 2), new Item(3, 1)));

        assertEquals(5, r.getProduced());
        assertEquals(5, r.getConsumed());
        assertEquals(3, r.getDistinct());
        assertEquals(2, r.getLost());
        assertEquals(2, r.getDuplicated());
        assertEquals(Map.of(new Item(1, 2), 3), r.getDuplicates());
    }

    @Test
    void emptyRun() {
        SimulationResult r = result(List.of(new WorkerReport("producer-1", 0, false)), List.of());

        assertEquals(0, r.getProduced());
        assertEquals(0, r.getConsumed());
        assertEquals(0, r.getLost());
        assertEquals(0, r.getDuplicated());
    }

    @Test
    void isNotAffectedByLaterChangesToInputLists() {
        List<WorkerReport> producers = new ArrayList<>(List.of(new WorkerReport("producer-1", 1, false)));
        List<Item> consumed = new ArrayList<>(List.of(new Item(0, 1)));
        SimulationResult r = result(producers, consumed);

        producers.add(new WorkerReport("producer-2", 5, false));
        consumed.add(new Item(0, 1));

        assertEquals(1, r.getProduced());
        assertEquals(1, r.getConsumed());
        assertEquals(1, r.getProducers().size());
    }

    @Test
    void exposedCollectionsAreReadOnly() {
        SimulationResult r = result(List.of(new WorkerReport("producer-1", 1, false)), List.of(new Item(0, 1)));

        try {
            r.getProducers().clear();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
        try {
            r.getDuplicates().clear();
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
        try {
            r.getNotTerminated().add("x");
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
