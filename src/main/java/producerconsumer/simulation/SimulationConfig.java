package producerconsumer.simulation;

/**
 * Parametri di una simulazione. Immutabile; i valori sono validati alla costruzione.
 * I limiti sono esposti come costanti per essere riusati dall'interfaccia utente.
 */
public final class SimulationConfig {

    public static final int MIN_PRODUCERS = 1;
    public static final int MAX_PRODUCERS = 50;
    public static final int MIN_CONSUMERS = 1;
    public static final int MAX_CONSUMERS = 50;
    public static final int MIN_ITEMS = 0;
    public static final int MAX_ITEMS = 1_000_000;
    public static final int MIN_CAPACITY = 1;
    public static final int MAX_CAPACITY = 1_000;
    public static final long MIN_TIMEOUT_MILLIS = 100;
    public static final long MAX_TIMEOUT_MILLIS = 60_000;
    public static final long MIN_DELAY_MILLIS = 0;
    public static final long MAX_DELAY_MILLIS = 2_000;

    private final int producers;
    private final int consumers;
    private final int items;
    private final int capacity;
    private final long timeoutMillis;
    private final long delayMillis;

    /**
     * @param producers     numero di producer
     * @param consumers     numero di consumer
     * @param items         numero totale di item da produrre
     * @param capacity      capacità del buffer
     * @param timeoutMillis durata massima della simulazione in millisecondi
     * @param delayMillis   pausa di producer e consumer dopo ogni operazione riuscita,
     *                      in millisecondi; 0 = nessuna pausa
     * @throws IllegalArgumentException se un valore è fuori dai limiti
     */
    public SimulationConfig(int producers, int consumers, int items, int capacity, long timeoutMillis,
            long delayMillis) {
        this.producers = requireInRange("producers", producers, MIN_PRODUCERS, MAX_PRODUCERS);
        this.consumers = requireInRange("consumers", consumers, MIN_CONSUMERS, MAX_CONSUMERS);
        this.items = requireInRange("items", items, MIN_ITEMS, MAX_ITEMS);
        this.capacity = requireInRange("capacity", capacity, MIN_CAPACITY, MAX_CAPACITY);
        this.timeoutMillis = requireInRange("timeoutMillis", timeoutMillis, MIN_TIMEOUT_MILLIS, MAX_TIMEOUT_MILLIS);
        this.delayMillis = requireInRange("delayMillis", delayMillis, MIN_DELAY_MILLIS, MAX_DELAY_MILLIS);
    }

    /**
     * Overload per i parametri {@code int}: delega alla versione {@code long}.
     * Il cast {@code (long) value} seleziona l'overload {@code long}; senza, la chiamata
     * risolverebbe a questo stesso metodo (ricorsione infinita). Il cast {@code (int)} del
     * risultato non perde informazione: il valore è già compreso tra {@code min} e {@code max}.
     *
     * @return {@code value}, se compreso nell'intervallo
     * @throws IllegalArgumentException se {@code value} è fuori da [{@code min}, {@code max}]
     */
    private static int requireInRange(String name, int value, int min, int max) {
        return (int) requireInRange(name, (long) value, min, max);
    }

    /**
     * Verifica che {@code value} sia compreso in [{@code min}, {@code max}] e lo restituisce,
     * per consentire controllo e assegnazione in un'unica istruzione.
     *
     * @param name nome del parametro, riportato nel messaggio d'errore
     * @return {@code value}, se compreso nell'intervallo
     * @throws IllegalArgumentException se {@code value} è fuori da [{@code min}, {@code max}]
     */
    private static long requireInRange(String name, long value, long min, long max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    name + " must be between " + min + " and " + max + ": " + value);
        }
        return value;
    }

    public int getProducers() {
        return producers;
    }

    public int getConsumers() {
        return consumers;
    }

    public int getItems() {
        return items;
    }

    public int getCapacity() {
        return capacity;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public long getDelayMillis() {
        return delayMillis;
    }

    @Override
    public String toString() {
        return "SimulationConfig[producers=" + producers + ", consumers=" + consumers
                + ", items=" + items + ", capacity=" + capacity + ", timeoutMillis=" + timeoutMillis
                + ", delayMillis=" + delayMillis + "]";
    }
}
