package producerconsumer.event;

import java.util.ArrayList;
import java.util.List;

import producerconsumer.model.Item;

/**
 * Listener di test: registra ogni evento come stringa "tipo thread [id]", in ordine di arrivo.
 * Thread-safe: i metodi sono sincronizzati sull'istanza.
 */
public class RecordingListener implements SimulationListener {

    private final List<String> events = new ArrayList<>();

    @Override
    public synchronized void beforePut(String thread) {
        events.add("beforePut " + thread);
    }

    @Override
    public synchronized void onPut(String thread, Item item) {
        events.add("onPut " + thread + " " + item.getId());
    }

    @Override
    public synchronized void beforeTake(String thread) {
        events.add("beforeTake " + thread);
    }

    @Override
    public synchronized void onTake(String thread, Item item) {
        events.add("onTake " + thread + " " + item.getId());
    }

    /** @return copia degli eventi registrati finora */
    public synchronized List<String> events() {
        return new ArrayList<>(events);
    }

    /** @return numero di eventi che iniziano con {@code prefix} */
    public synchronized int count(String prefix) {
        int n = 0;
        for (String event : events) {
            if (event.startsWith(prefix)) {
                n++;
            }
        }
        return n;
    }
}
