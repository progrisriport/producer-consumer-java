package producerconsumer.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import producerconsumer.actor.Consumer;
import producerconsumer.actor.Producer;
import producerconsumer.buffer.Buffer;
import producerconsumer.counter.IdGenerator;
import producerconsumer.counter.Quota;
import producerconsumer.event.SimulationListener;
import producerconsumer.model.Item;

/**
 * Esegue una simulazione producer/consumer su un buffer dato.
 *
 * Sequenza: avvio dei consumer e dei producer, attesa dei producer, inserimento di una
 * poison pill per consumer da parte di un thread dedicato, attesa dei consumer.
 * Ogni attesa del thread chiamante è limitata dal tempo residuo rispetto a un'unica scadenza.
 *
 * Alla scadenza: interruzione dei thread vivi, attesa di un margine fisso, registrazione
 * dei thread ancora vivi. Tutti i thread sono daemon.
 *
 * La classe non è un thread: {@link #run()} è eseguito dal thread chiamante.
 * Un'istanza esegue un solo run; il buffer non va riutilizzato tra run diversi.
 */
public final class Simulation {

    /** Attesa concessa ai thread dopo l'interruzione. */
    static final long GRACE_MILLIS = 1_000;

    private final Buffer buffer;
    private final SimulationConfig config;
    private final SimulationListener listener;

    /**
     * @param buffer   buffer condiviso; la capacità di {@code config} non viene applicata qui
     * @param config   parametri della simulazione
     * @param listener destinatario degli eventi di producer e consumer; le pillole del
     *                 terminator non sono notificate
     */
    public Simulation(Buffer buffer, SimulationConfig config, SimulationListener listener) {
        this.buffer = buffer;
        this.config = config;
        this.listener = listener;
    }

    /**
     * Esegue la simulazione. Ritorna entro timeout + {@link #GRACE_MILLIS}, salvo ritardi dello scheduler.
     *
     * @return esito della simulazione
     * @throws InterruptedException se il thread chiamante viene interrotto durante un'attesa
     */
    public SimulationResult run() throws InterruptedException {
        long start = System.nanoTime();
        long deadline = start + TimeUnit.MILLISECONDS.toNanos(config.getTimeoutMillis());

        IdGenerator generator = new IdGenerator();
        Quota quota = new Quota(config.getItems());

        List<Producer> producers = new ArrayList<>();
        for (int id = 1; id <= config.getProducers(); id++) {
            producers.add(new Producer(id, buffer, generator, quota, listener, config.getDelayMillis()));
        }
        List<Consumer> consumers = new ArrayList<>();
        for (int id = 1; id <= config.getConsumers(); id++) {
            consumers.add(new Consumer(id, buffer, listener, config.getDelayMillis()));
        }
        Thread terminator = new Thread(new Runnable() {
            @Override
            public void run() {
                putPoisonPills();
            }
        }, "terminator");

        List<Thread> all = new ArrayList<>();
        all.addAll(consumers);
        all.addAll(producers);
        all.add(terminator);
        for (Thread thread : all) {
            thread.setDaemon(true);
        }

        for (Consumer consumer : consumers) {
            consumer.start();
        }
        for (Producer producer : producers) {
            producer.start();
        }

        boolean completed = joinAll(producers, deadline);
        if (completed) {
            terminator.start();
            completed = joinAll(List.of(terminator), deadline);
            if (completed) {
                completed = joinAll(consumers, deadline);
            }
        }

        List<String> notTerminated = List.of();
        if (!completed) {
            for (Thread thread : all) {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            }
            joinAll(all, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(GRACE_MILLIS));
            notTerminated = aliveNames(all);
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return buildResult(producers, consumers, !completed, notTerminated, elapsedMillis);
    }

    /** Corpo del thread terminator: una pillola per consumer. */
    private void putPoisonPills() {
        try {
            for (int i = 0; i < config.getConsumers(); i++) {
                buffer.put(Item.POISON);
            }
        } catch (InterruptedException e) {
            // terminazione richiesta dal timeout
        }
    }

    /**
     * Attende la fine dei thread entro la scadenza.
     * Non chiama mai {@code join(0)}, che attenderebbe senza limite.
     *
     * @return true se tutti i thread sono terminati
     */
    private static boolean joinAll(List<? extends Thread> threads, long deadlineNanos)
            throws InterruptedException {
        for (Thread thread : threads) {
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
            if (remainingMillis <= 0) {
                break;
            }
            thread.join(remainingMillis);
        }
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }

    private static List<String> aliveNames(List<Thread> threads) {
        List<String> names = new ArrayList<>();
        for (Thread thread : threads) {
            if (thread.isAlive()) {
                names.add(thread.getName());
            }
        }
        return names;
    }

    /**
     * I contatori dei thread terminati sono visibili grazie al join.
     * Quelli dei thread ancora vivi possono non essere aggiornati.
     */
    private static SimulationResult buildResult(List<Producer> producers, List<Consumer> consumers,
            boolean timedOut, List<String> notTerminated, long elapsedMillis) {
        List<WorkerReport> producerReports = new ArrayList<>();
        for (Producer producer : producers) {
            producerReports.add(new WorkerReport(producer.getName(), producer.getProduced(), producer.wasInterrupted()));
        }
        List<WorkerReport> consumerReports = new ArrayList<>();
        List<Item> consumedItems = new ArrayList<>();
        for (Consumer consumer : consumers) {
            List<Item> items = List.copyOf(consumer.getConsumed());
            consumerReports.add(new WorkerReport(consumer.getName(), items.size(), consumer.wasInterrupted()));
            consumedItems.addAll(items);
        }
        return new SimulationResult(producerReports, consumerReports, consumedItems,
                timedOut, notTerminated, elapsedMillis);
    }
}
