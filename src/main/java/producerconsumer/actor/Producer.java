package producerconsumer.actor;

import producerconsumer.buffer.Buffer;
import producerconsumer.counter.IdGenerator;
import producerconsumer.counter.Quota;
import producerconsumer.event.SimulationListener;
import producerconsumer.model.Item;

/**
 * Thread che produce item finché la quota condivisa lo consente.
 * Non inserisce la poison pill. Notifica al listener l'inizio e l'esito di ogni put e,
 * dopo ogni put riuscito, sospende il thread per {@code delayMillis}, fuori dal buffer.
 *
 * {@link #getProduced()} e {@link #wasInterrupted()} vanno letti dopo {@link #join()}:
 * il join garantisce la visibilità dei valori scritti dal thread.
 */
public final class Producer extends Thread {

    private final int id;
    private final Buffer buffer;
    private final IdGenerator generator;
    private final Quota quota;
    private final SimulationListener listener;
    private final long delayMillis;

    /** Item accettati dal buffer. Scritto solo da questo thread. */
    private int produced;

    /** True se il thread è terminato per interruzione. Scritto solo da questo thread. */
    private boolean interrupted;

    /**
     * @param id        identificativo del producer, usato come producerId degli item
     * @param buffer    buffer in cui inserire gli item
     * @param generator generatore di id condiviso
     * @param quota       quota condivisa di item da produrre
     * @param listener    destinatario degli eventi
     * @param delayMillis pausa dopo ogni put riuscito, in millisecondi; 0 = nessuna pausa
     */
    public Producer(int id, Buffer buffer, IdGenerator generator, Quota quota,
            SimulationListener listener, long delayMillis) {
        super("producer-" + id);
        this.id = id;
        this.buffer = buffer;
        this.generator = generator;
        this.quota = quota;
        this.listener = listener;
        this.delayMillis = delayMillis;
    }

    /**
     * Ciclo: permesso → id → beforePut → put → conteggio → onPut → pausa.
     * Il conteggio avviene dopo il put: un put interrotto non conta come prodotto.
     * Il try avvolge l'intero ciclo: l'interruzione, nel put o nella pausa, termina il thread.
     */
    @Override
    public void run() {
        try {
            while (quota.tryAcquire()) {
                Item item = new Item(generator.next(), id);
                listener.beforePut(getName());
                buffer.put(item);
                produced++;
                listener.onPut(getName(), item);
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
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
     * @return true se il thread è terminato per interruzione durante un put o una pausa
     */
    public boolean wasInterrupted() {
        return interrupted;
    }
}
