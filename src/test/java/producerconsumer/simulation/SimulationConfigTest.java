package producerconsumer.simulation;

import static org.junit.jupiter.api.Assertions.*;
import static producerconsumer.simulation.SimulationConfig.*;

import org.junit.jupiter.api.Test;

class SimulationConfigTest {

    @Test
    void exposesValues() {
        SimulationConfig config = new SimulationConfig(3, 2, 300, 5, 10_000, 200);
        assertEquals(3, config.getProducers());
        assertEquals(2, config.getConsumers());
        assertEquals(300, config.getItems());
        assertEquals(5, config.getCapacity());
        assertEquals(10_000, config.getTimeoutMillis());
        assertEquals(200, config.getDelayMillis());
    }

    @Test
    void acceptsLowerBounds() {
        new SimulationConfig(
                MIN_PRODUCERS, MIN_CONSUMERS, MIN_ITEMS, MIN_CAPACITY, MIN_TIMEOUT_MILLIS, MIN_DELAY_MILLIS);
    }

    @Test
    void acceptsUpperBounds() {
        new SimulationConfig(
                MAX_PRODUCERS, MAX_CONSUMERS, MAX_ITEMS, MAX_CAPACITY, MAX_TIMEOUT_MILLIS, MAX_DELAY_MILLIS);
    }

    @Test
    void rejectsProducersOutOfRange() {
        try {
            new SimulationConfig(MIN_PRODUCERS - 1, 1, 10, 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new SimulationConfig(MAX_PRODUCERS + 1, 1, 10, 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void rejectsConsumersOutOfRange() {
        try {
            new SimulationConfig(1, MIN_CONSUMERS - 1, 10, 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new SimulationConfig(1, MAX_CONSUMERS + 1, 10, 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void rejectsItemsOutOfRange() {
        try {
            new SimulationConfig(1, 1, MIN_ITEMS - 1, 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new SimulationConfig(1, 1, MAX_ITEMS + 1, 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void rejectsCapacityOutOfRange() {
        try {
            new SimulationConfig(1, 1, 10, MIN_CAPACITY - 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new SimulationConfig(1, 1, 10, MAX_CAPACITY + 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void rejectsTimeoutOutOfRange() {
        try {
            new SimulationConfig(1, 1, 10, 1, MIN_TIMEOUT_MILLIS - 1, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new SimulationConfig(1, 1, 10, 1, MAX_TIMEOUT_MILLIS + 1, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void rejectsDelayOutOfRange() {
        try {
            new SimulationConfig(1, 1, 10, 1, 1_000, MIN_DELAY_MILLIS - 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new SimulationConfig(1, 1, 10, 1, 1_000, MAX_DELAY_MILLIS + 1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void errorMessageNamesTheParameterAndTheRange() {
        try {
            new SimulationConfig(0, 1, 10, 1, 1_000, 0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("producers"));
            assertTrue(e.getMessage().contains(MIN_PRODUCERS + " and " + MAX_PRODUCERS));
        }
    }
}
