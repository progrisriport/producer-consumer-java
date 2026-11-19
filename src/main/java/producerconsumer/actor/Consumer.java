package producerconsumer.actor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import producerconsumer.buffer.Buffer;
import producerconsumer.model.Item;

/**
 * Thread che consuma item dal buffer finché riceve la poison pill.
 * Registra ogni item consumato (pillola esclusa) in una lista privata.
 *
 * {@link #getConsumed()} e {@link #wasInterrupted()} vanno letti dopo {@link #join()}:
 * il join garantisce la visibilità dei valori scritti dal thread.
 */
public final class Consumer extends Thread {

    private final Buffer buffer;

    /** Item consumati, in ordine di arrivo. Scritta solo da questo thread. */
    private final List<Item> consumed = new ArrayList<>();

    /** True se il thread è terminato per interruzione. Scritto solo da questo thread. */
    private boolean interrupted;

    /**
     * @param id     identificativo del consumer, usato nel nome del thread
     * @param buffer buffer da cui prelevare gli item
     */
    public Consumer(int id, Buffer buffer) {
        super("consumer-" + id);
        this.buffer = buffer;
    }

    /**
     * Ciclo: take → se pillola, uscita → altrimenti registrazione.
     * La pillola non viene reinserita nel buffer.
     * Il try avvolge l'intero ciclo: l'interruzione termina il thread.
     */
    @Override
    public void run() {
        try {
            while (true) {
                Item item = buffer.take();
                if (item.isPoison()) {
                    return;
                }
                consumed.add(item);
            }
        } catch (InterruptedException e) {
            interrupted = true;
        }
    }

    /**
     * @return vista non modificabile degli item consumati, in ordine di arrivo
     */
    public List<Item> getConsumed() {
        return Collections.unmodifiableList(consumed);
    }

    /**
     * @return true se il thread è terminato per interruzione durante un take
     */
    public boolean wasInterrupted() {
        return interrupted;
    }
}
