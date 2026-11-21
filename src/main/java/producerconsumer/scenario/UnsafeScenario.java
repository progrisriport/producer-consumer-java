package producerconsumer.scenario;

import producerconsumer.buffer.Buffer;
import producerconsumer.buffer.UnsafeBuffer;
import producerconsumer.simulation.SimulationConfig;

/** Scenario concorrente su {@link UnsafeBuffer}: mostra item persi e duplicati. */
public final class UnsafeScenario extends BufferScenario {

    public UnsafeScenario() {
        super("Unsynchronized",
                "No mutual exclusion: items are lost or duplicated, but nothing crashes.");
    }

    @Override
    protected Buffer createBuffer(SimulationConfig config) {
        return new UnsafeBuffer(config.getCapacity());
    }
}
