package producerconsumer.scenario;

import producerconsumer.buffer.Buffer;
import producerconsumer.simulation.Simulation;
import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;

/**
 * Scenario concorrente basato su un buffer: le sottoclassi indicano quale buffer creare,
 * l'esecuzione è delegata a {@link Simulation}.
 */
public abstract class BufferScenario implements Scenario {

    private final String name;
    private final String description;

    /**
     * @param name        nome mostrato nel menu
     * @param description breve spiegazione di ciò che lo scenario mostra
     */
    protected BufferScenario(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Crea il buffer per un run. Chiamato una volta per run: deve restituire sempre
     * un'istanza nuova.
     *
     * @param config parametri della simulazione
     * @return buffer vuoto
     */
    protected abstract Buffer createBuffer(SimulationConfig config);

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final String getDescription() {
        return description;
    }

    @Override
    public final SimulationResult run(SimulationConfig config) throws InterruptedException {
        Buffer buffer = createBuffer(config);
        Simulation simulation = new Simulation(buffer, config);
        return simulation.run();
    }
}
