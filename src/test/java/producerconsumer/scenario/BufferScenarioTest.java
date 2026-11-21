package producerconsumer.scenario;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import producerconsumer.buffer.BlockingQueueBuffer;
import producerconsumer.buffer.Buffer;
import producerconsumer.buffer.LockBuffer;
import producerconsumer.buffer.MonitorBuffer;
import producerconsumer.buffer.UnsafeBuffer;
import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;

/** Scenari basati su buffer: buffer creato, buffer nuovo a ogni run, esito dei run. */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
class BufferScenarioTest {

    private static final SimulationConfig CONFIG = new SimulationConfig(3, 2, 2_000, 4, 5_000);

    private static BufferScenario create(String name) {
        switch (name) {
            case "unsafe":
                return new UnsafeScenario();
            case "monitor":
                return new MonitorScenario();
            case "lock":
                return new LockScenario();
            case "blocking-queue":
                return new BlockingQueueScenario();
            default:
                throw new IllegalArgumentException("unknown scenario: " + name);
        }
    }

    @Test
    void createsTheExpectedBufferType() {
        assertTrue(new UnsafeScenario().createBuffer(CONFIG) instanceof UnsafeBuffer);
        assertTrue(new MonitorScenario().createBuffer(CONFIG) instanceof MonitorBuffer);
        assertTrue(new LockScenario().createBuffer(CONFIG) instanceof LockBuffer);
        assertTrue(new BlockingQueueScenario().createBuffer(CONFIG) instanceof BlockingQueueBuffer);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"unsafe", "monitor", "lock", "blocking-queue"})
    void createsANewBufferWithConfiguredCapacity(String name) {
        BufferScenario scenario = create(name);
        Buffer first = scenario.createBuffer(CONFIG);
        Buffer second = scenario.createBuffer(CONFIG);

        assertNotSame(first, second);
        assertEquals(CONFIG.getCapacity(), first.capacity());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"monitor", "lock", "blocking-queue"})
    void safeScenarioConsumesEveryItemOnce(String name) throws InterruptedException {
        SimulationResult r = create(name).run(CONFIG);

        assertFalse(r.isTimedOut());
        assertEquals(CONFIG.getItems(), r.getProduced());
        assertEquals(CONFIG.getItems(), r.getConsumed());
        assertEquals(0, r.getLost());
        assertEquals(0, r.getDuplicated());
        assertEquals(CONFIG.getProducers(), r.getProducers().size());
        assertEquals(CONFIG.getConsumers(), r.getConsumers().size());
    }

    /** Lo stesso oggetto scenario può essere eseguito più volte. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"monitor", "lock", "blocking-queue"})
    void canRunTwice(String name) throws InterruptedException {
        BufferScenario scenario = create(name);
        scenario.run(CONFIG);
        SimulationResult second = scenario.run(CONFIG);

        assertEquals(CONFIG.getItems(), second.getConsumed());
        assertEquals(0, second.getLost());
    }

    /** Il buffer non sincronizzato può perdere anche le pillole: si verifica solo che il run termini. */
    @Test
    void unsafeScenarioAlwaysReturns() throws InterruptedException {
        SimulationConfig config = new SimulationConfig(2, 2, 2_000, 4, 1_000);
        SimulationResult r = new UnsafeScenario().run(config);

        assertTrue(r.getNotTerminated().isEmpty());
    }
}
