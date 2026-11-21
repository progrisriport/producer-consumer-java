package producerconsumer.scenario;

import producerconsumer.buffer.Buffer;
import producerconsumer.buffer.MonitorBuffer;
import producerconsumer.simulation.SimulationConfig;

/** Scenario concorrente su {@link MonitorBuffer}. */
public final class MonitorScenario extends BufferScenario {

    public MonitorScenario() {
        super("Monitor",
                "synchronized + wait/notifyAll: every item is consumed exactly once.");
    }

    @Override
    protected Buffer createBuffer(SimulationConfig config) {
        return new MonitorBuffer(config.getCapacity());
    }
}
