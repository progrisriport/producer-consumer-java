package producerconsumer.scenario;

import java.util.ArrayList;
import java.util.List;

/** Catalogo degli scenari disponibili. Non istanziabile. */
public final class Scenarios {

    private Scenarios() {
    }

    /** @return gli scenari nell'ordine del menu, in una lista non modificabile */
    public static List<Scenario> all() {
        List<Scenario> list = new ArrayList<>();
        list.add(new SequentialScenario());
        list.add(new UnsafeScenario());
        list.add(new MonitorScenario());
        list.add(new LockScenario());
        list.add(new BlockingQueueScenario());
        return List.copyOf(list);
    }
}
