package producerconsumer.scenario;

import producerconsumer.buffer.Buffer;
import producerconsumer.buffer.LockBuffer;
import producerconsumer.simulation.SimulationConfig;

/** Scenario concorrente su {@link LockBuffer}. */
public final class LockScenario extends BufferScenario {

    public LockScenario() {
        super("Lock and Condition",
                "ReentrantLock + two Conditions: every item is consumed exactly once.");
    }

    @Override
    protected Buffer createBuffer(SimulationConfig config) {
        return new LockBuffer(config.getCapacity());
    }
}
