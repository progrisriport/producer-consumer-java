package producerconsumer.scenario;

import producerconsumer.buffer.Buffer;
import producerconsumer.buffer.BlockingQueueBuffer;
import producerconsumer.simulation.SimulationConfig;

/** Scenario concorrente su {@link BlockingQueueBuffer}. */
public final class BlockingQueueScenario extends BufferScenario {

    public BlockingQueueScenario() {
        super("Blocking queue",
                "ArrayBlockingQueue from the standard library: every item is consumed exactly once.");
    }

    @Override
    protected Buffer createBuffer(SimulationConfig config) {
        return new BlockingQueueBuffer(config.getCapacity());
    }
}
