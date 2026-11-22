package producerconsumer.tui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import producerconsumer.simulation.SimulationConfig;

class ParametersTest {

    @Test
    void startsFromDefaults() {
        SimulationConfig config = new Parameters().toConfig();

        assertEquals(Theme.DEFAULT_PRODUCERS, config.getProducers());
        assertEquals(Theme.DEFAULT_CONSUMERS, config.getConsumers());
        assertEquals(Theme.DEFAULT_ITEMS, config.getItems());
        assertEquals(Theme.DEFAULT_CAPACITY, config.getCapacity());
        assertEquals(Theme.DEFAULT_TIMEOUT_MILLIS, config.getTimeoutMillis());
        assertEquals(Theme.DEFAULT_DELAY_MILLIS, config.getDelayMillis());
    }

    @Test
    void limitsComeFromSimulationConfig() {
        Parameters parameters = new Parameters();

        assertEquals(SimulationConfig.MIN_PRODUCERS, parameters.min(1));
        assertEquals(SimulationConfig.MAX_CONSUMERS, parameters.max(2));
        assertEquals(SimulationConfig.MAX_ITEMS, parameters.max(3));
        assertEquals(SimulationConfig.MIN_CAPACITY, parameters.min(4));
        assertEquals(SimulationConfig.MAX_TIMEOUT_MILLIS, parameters.max(5));
        assertEquals(SimulationConfig.MAX_DELAY_MILLIS, parameters.max(6));
    }

    @Test
    void setChangesTheConfig() {
        Parameters parameters = new Parameters();
        parameters.set(1, 7);
        parameters.set(6, 0);

        SimulationConfig config = parameters.toConfig();
        assertEquals(7, config.getProducers());
        assertEquals(0, config.getDelayMillis());
        assertEquals(7, parameters.value(1));
    }

    @Test
    void liveOnlyWithPositiveDelay() {
        Parameters parameters = new Parameters();
        assertTrue(parameters.isLive());

        parameters.set(6, 0);
        assertFalse(parameters.isLive());
    }

    @Test
    void rejectsValueOutOfRangeAndKeepsTheOldOne() {
        Parameters parameters = new Parameters();
        try {
            parameters.set(1, SimulationConfig.MAX_PRODUCERS + 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Producers"));
        }
        assertEquals(Theme.DEFAULT_PRODUCERS, parameters.value(1));
    }

    @Test
    void rejectsUnknownParameterNumber() {
        Parameters parameters = new Parameters();
        try {
            parameters.label(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            parameters.set(Parameters.COUNT + 1, 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
