package producerconsumer.scenario;

import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;

/**
 * Scenario eseguibile dal menu: una strategia di sincronizzazione applicata al problema
 * producer/consumer.
 */
public interface Scenario {

    /** @return nome mostrato nel menu */
    String getName();

    /** @return breve spiegazione di ciò che lo scenario mostra */
    String getDescription();

    /**
     * Esegue un run completo. Ogni chiamata usa un buffer nuovo.
     *
     * @param config parametri della simulazione
     * @return esito del run
     * @throws InterruptedException se il thread chiamante viene interrotto durante un'attesa
     */
    SimulationResult run(SimulationConfig config) throws InterruptedException;
}
