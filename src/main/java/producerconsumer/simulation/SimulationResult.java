package producerconsumer.simulation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import producerconsumer.model.Item;

/**
 * Esito di una simulazione. Immutabile.
 *
 * Distinti e duplicati sono calcolati alla costruzione a partire da tutti gli item consumati:
 * <ul>
 *   <li>persi = prodotti − distinti</li>
 *   <li>duplicati = consumati − distinti</li>
 * </ul>
 * Gli item persi sono solo contati: i producer non registrano gli item prodotti.
 */
public final class SimulationResult {

    private final List<WorkerReport> producers;
    private final List<WorkerReport> consumers;
    private final int consumed;
    private final int distinct;
    private final Map<Item, Integer> duplicates;
    private final boolean timedOut;
    private final List<String> notTerminated;
    private final long elapsedMillis;

    /**
     * @param producers     esito di ogni producer
     * @param consumers     esito di ogni consumer
     * @param consumedItems tutti gli item consumati, da tutti i consumer, pillole escluse
     * @param timedOut      true se la simulazione è stata fermata dal timeout
     * @param notTerminated nomi dei thread ancora vivi al termine
     * @param elapsedMillis durata della simulazione in millisecondi
     */
    public SimulationResult(List<WorkerReport> producers, List<WorkerReport> consumers,
            List<Item> consumedItems, boolean timedOut, List<String> notTerminated,
            long elapsedMillis) {
        this.producers = List.copyOf(producers);
        this.consumers = List.copyOf(consumers);
        this.consumed = consumedItems.size();
        this.timedOut = timedOut;
        this.notTerminated = List.copyOf(notTerminated);
        this.elapsedMillis = elapsedMillis;

        Map<Item, Integer> occurrences = new HashMap<>();
        Set<Integer> ids = new HashSet<>();
        for (Item item : consumedItems) {
            if (occurrences.containsKey(item)) {
                occurrences.put(item, occurrences.get(item) + 1);
            } else {
                occurrences.put(item, 1);
            }
            ids.add(item.getId());
        }
        this.distinct = ids.size();

        Map<Item, Integer> repeated = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : occurrences.entrySet()) {
            if (entry.getValue() > 1) {
                repeated.put(entry.getKey(), entry.getValue());
            }
        }
        this.duplicates = Collections.unmodifiableMap(repeated);
    }

    /** @return item accettati dal buffer, sommati su tutti i producer */
    public int getProduced() {
        int total = 0;
        for (WorkerReport report : producers) {
            total += report.getCount();
        }
        return total;
    }

    /** @return item consumati, duplicati compresi */
    public int getConsumed() {
        return consumed;
    }

    /** @return id distinti tra gli item consumati */
    public int getDistinct() {
        return distinct;
    }

    /** @return prodotti − distinti */
    public int getLost() {
        return getProduced() - distinct;
    }

    /** @return consumati − distinti */
    public int getDuplicated() {
        return consumed - distinct;
    }

    /** @return item consumati più di una volta, con il numero di occorrenze */
    public Map<Item, Integer> getDuplicates() {
        return duplicates;
    }

    public List<WorkerReport> getProducers() {
        return producers;
    }

    public List<WorkerReport> getConsumers() {
        return consumers;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public List<String> getNotTerminated() {
        return notTerminated;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }
}
