package producerconsumer.tui;

import producerconsumer.simulation.SimulationConfig;

/**
 * Valori correnti dei parametri, condivisi tra tutti gli scenari. Ogni parametro ha un numero
 * (da 1 a {@link #COUNT}), un'etichetta e i limiti di {@link SimulationConfig}.
 * Usata da un solo thread, quello della TUI.
 */
final class Parameters {

    static final int COUNT = 6;

    private static final String[] LABELS = {
        "Producers", "Consumers", "Items", "Buffer capacity", "Timeout (ms)", "Delay (ms)"
    };
    private static final long[] MIN = {
        SimulationConfig.MIN_PRODUCERS, SimulationConfig.MIN_CONSUMERS, SimulationConfig.MIN_ITEMS,
        SimulationConfig.MIN_CAPACITY, SimulationConfig.MIN_TIMEOUT_MILLIS, SimulationConfig.MIN_DELAY_MILLIS
    };
    private static final long[] MAX = {
        SimulationConfig.MAX_PRODUCERS, SimulationConfig.MAX_CONSUMERS, SimulationConfig.MAX_ITEMS,
        SimulationConfig.MAX_CAPACITY, SimulationConfig.MAX_TIMEOUT_MILLIS, SimulationConfig.MAX_DELAY_MILLIS
    };

    private final long[] values = {
        Theme.DEFAULT_PRODUCERS, Theme.DEFAULT_CONSUMERS, Theme.DEFAULT_ITEMS,
        Theme.DEFAULT_CAPACITY, Theme.DEFAULT_TIMEOUT_MILLIS, Theme.DEFAULT_DELAY_MILLIS
    };

    String label(int number) {
        return LABELS[index(number)];
    }

    long value(int number) {
        return values[index(number)];
    }

    long min(int number) {
        return MIN[index(number)];
    }

    long max(int number) {
        return MAX[index(number)];
    }

    /**
     * @param number numero del parametro, da 1 a {@link #COUNT}
     * @param value  nuovo valore
     * @throws IllegalArgumentException se il numero o il valore sono fuori dai limiti
     */
    void set(int number, long value) {
        int i = index(number);
        if (value < MIN[i] || value > MAX[i]) {
            throw new IllegalArgumentException(
                    LABELS[i] + " must be between " + MIN[i] + " and " + MAX[i] + ": " + value);
        }
        values[i] = value;
    }

    /** @return true se il ritardo è positivo: l'esecuzione mostra la vista dal vivo */
    boolean isLive() {
        return values[5] > 0;
    }

    /**
     * I cast a {@code int} non perdono informazione: i limiti dei primi quattro parametri
     * sono valori {@code int}.
     *
     * @return configurazione con i valori correnti
     */
    SimulationConfig toConfig() {
        return new SimulationConfig((int) values[0], (int) values[1], (int) values[2], (int) values[3],
                values[4], values[5]);
    }

    private static int index(int number) {
        if (number < 1 || number > COUNT) {
            throw new IllegalArgumentException("parameter number must be between 1 and " + COUNT + ": " + number);
        }
        return number - 1;
    }
}
