package producerconsumer.simulation;

/**
 * Esito di un singolo thread (producer o consumer) al termine della simulazione.
 * Immutabile.
 */
public final class WorkerReport {

    private final String name;
    private final int count;
    private final boolean interrupted;

    /**
     * @param name        nome del thread
     * @param count       item prodotti (producer) o consumati (consumer)
     * @param interrupted true se il thread è terminato per interruzione
     */
    public WorkerReport(String name, int count, boolean interrupted) {
        this.name = name;
        this.count = count;
        this.interrupted = interrupted;
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }

    public boolean wasInterrupted() {
        return interrupted;
    }

    @Override
    public String toString() {
        if (interrupted) {
            return name + ": " + count + " (interrupted)";
        }
        return name + ": " + count;
    }
}
