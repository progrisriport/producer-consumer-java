package producerconsumer.scenario;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ScenariosTest {

    @Test
    void listsScenariosInMenuOrder() {
        List<Scenario> all = Scenarios.all();

        assertEquals(5, all.size());
        assertTrue(all.get(0) instanceof SequentialScenario);
        assertTrue(all.get(1) instanceof UnsafeScenario);
        assertTrue(all.get(2) instanceof MonitorScenario);
        assertTrue(all.get(3) instanceof LockScenario);
        assertTrue(all.get(4) instanceof BlockingQueueScenario);
    }

    @Test
    void namesAreUniqueAndTextsAreNotBlank() {
        Set<String> names = new HashSet<>();
        for (Scenario scenario : Scenarios.all()) {
            assertFalse(scenario.getName().isBlank());
            assertFalse(scenario.getDescription().isBlank(), scenario.getName());
            assertTrue(names.add(scenario.getName()), "duplicate name: " + scenario.getName());
        }
    }

    @Test
    void listIsUnmodifiable() {
        List<Scenario> all = Scenarios.all();
        try {
            all.add(new SequentialScenario());
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
