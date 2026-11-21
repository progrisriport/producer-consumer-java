package producerconsumer.scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import producerconsumer.buffer.UnsafeBuffer;
import producerconsumer.counter.IdGenerator;
import producerconsumer.counter.Quota;
import producerconsumer.model.Item;
import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;
import producerconsumer.simulation.WorkerReport;

/**
 * Scenario senza concorrenza: un solo thread, il chiamante, produce e consuma.
 *
 * Il thread alterna due fasi finché la quota non è esaurita: riempie il buffer, poi lo svuota.
 * Con un solo thread un put su buffer pieno resterebbe in attesa per sempre, quindi il numero
 * di item presenti nel buffer è tenuto localmente e non supera mai la capacità.
 *
 * Usa {@link UnsafeBuffer}: senza accessi concorrenti il buffer non perde né duplica item.
 * Il numero di producer e consumer della configurazione non viene usato.
 */
public final class SequentialScenario implements Scenario {

    static final int PRODUCER_ID = 1;
    static final String PRODUCER_NAME = "producer-1";
    static final String CONSUMER_NAME = "consumer-1";

    @Override
    public String getName() {
        return "Sequential";
    }

    @Override
    public String getDescription() {
        return "One thread fills and empties the buffer: no concurrency, no errors.";
    }

    @Override
    public SimulationResult run(SimulationConfig config) throws InterruptedException {
        long start = System.nanoTime();

        UnsafeBuffer buffer = new UnsafeBuffer(config.getCapacity());
        IdGenerator generator = new IdGenerator();
        Quota quota = new Quota(config.getItems());
        List<Item> consumed = new ArrayList<>();
        int produced = 0;

        boolean exhausted = false;
        while (!exhausted) {
            int inBuffer = 0;
            while (!exhausted && inBuffer < buffer.capacity()) {
                if (quota.tryAcquire()) {
                    buffer.put(new Item(generator.next(), PRODUCER_ID));
                    produced++;
                    inBuffer++;
                } else {
                    exhausted = true;
                }
            }
            for (int i = 0; i < inBuffer; i++) {
                consumed.add(buffer.take());
            }
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        List<WorkerReport> producers = List.of(new WorkerReport(PRODUCER_NAME, produced, false));
        List<WorkerReport> consumers = List.of(new WorkerReport(CONSUMER_NAME, consumed.size(), false));
        return new SimulationResult(producers, consumers, consumed, false, List.of(), elapsedMillis);
    }
}
