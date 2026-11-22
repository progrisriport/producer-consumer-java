package producerconsumer.actor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import producerconsumer.buffer.Buffer;
import producerconsumer.event.SimulationListener;
import producerconsumer.model.Item;

/**
 * Thread che consuma item dal buffer finché riceve la poison pill.
 * Registra ogni item consumato (pillola esclusa) in una lista privata. Notifica al listener
 * l'inizio di ogni take e il prelievo di ogni item; dopo ogni item consumato sospende il
 * thread per {@code delayMillis}, fuori dal buffer.
 *
 * {@link #getConsumed()} e {@link #wasInterrupted()} vanno letti dopo {@link #join()}:
 * il join garantisce la visibilità dei valori scritti dal thread.
 */
public final class Consumer extends Thread {

    private final Buffer buffer;
    private final SimulationListener listener;
    private final long delayMillis;

    /** Item consumati, in ordine di arrivo. Scritta solo da questo thread. */
    private final List<Item> consumed = new ArrayList<>();

    /** True se il thread è terminato per interruzione. Scritto solo da questo thread. */
    private boolean interrupted;

    /**
     * @param id     identificativo del consumer, usato nel nome del thread
     * @param buffer      buffer da cui prelevare gli item
     * @param listener    destinatario degli eventi
     * @param delayMillis pausa dopo ogni item consumato, in millisecondi; 0 = nessuna pausa
     */
    public Consumer(int id, Buffer buffer, SimulationListener listener, long delayMillis) {
        super("consumer-" + id);
        this.buffer = buffer;
        this.listener = listener;
        this.delayMillis = delayMillis;
    }

    /**
     * Ciclo: beforeTake → take → se pillola, uscita → altrimenti registrazione → onTake → pausa.
     * La pillola non viene reinserita nel buffer e non genera onTake.
     * Il try avvolge l'intero ciclo: l'interruzione, nel take o nella pausa, termina il thread.
     */
    @Override
    public void run() {
        try {
            while (true) {
                listener.beforeTake(getName());
                Item item = buffer.take();
                if (item.isPoison()) {
                    return;
                }
                consumed.add(item);
                listener.onTake(getName(), item);
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
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
     * @return true se il thread è terminato per interruzione durante un take o una pausa
     */
    public boolean wasInterrupted() {
        return interrupted;
    }
}
