package producerconsumer.actor;

import producerconsumer.buffer.Buffer;
import producerconsumer.counter.IdGenerator;
import producerconsumer.counter.Quota;
import producerconsumer.model.Item;

/**
 * Thread che produce item finché la quota condivisa lo consente.
 * Non inserisce la poison pill.
 *
 * {@link #getProduced()} e {@link #wasInterrupted()} vanno letti dopo {@link #join()}:
 * il join garantisce la visibilità dei valori scritti dal thread.
 */
public final class Producer extends Thread {

    private final int id;
    private final Buffer buffer;
    private final IdGenerator generator;
    private final Quota quota;

    /** Item accettati dal buffer. Scritto solo da questo thread. */
    private int produced;

    /** True se il thread è terminato per interruzione. Scritto solo da questo thread. */
    private boolean interrupted;

    /**
     * @param id        identificativo del producer, usato come producerId degli item
     * @param buffer    buffer in cui inserire gli item
     * @param generator generatore di id condiviso
     * @param quota     quota condivisa di item da produrre
     */
    public Producer(int id, Buffer buffer, IdGenerator generator, Quota quota) {
        super("producer-" + id);
        this.id = id;
        this.buffer = buffer;
        this.generator = generator;
        this.quota = quota;
    }

    /**
     * Ciclo: permesso → id → put → conteggio.
     * Il conteggio avviene dopo il put: un put interrotto non conta come prodotto.
     * Il try avvolge l'intero ciclo: l'interruzione termina il thread.
     */
    @Override
    public void run() {
        try {
            while (quota.tryAcquire()) {
                buffer.put(new Item(generator.next(), id));
                produced++;
            }
        } catch (InterruptedException e) {
            interrupted = true;
        }
    }

    /**
     * @return numero di item accettati dal buffer
     */
    public int getProduced() {
        return produced;
    }

    /**
     * @return true se il thread è terminato per interruzione durante un put
     */
    public boolean wasInterrupted() {
        return interrupted;
    }
}
