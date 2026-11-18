package producerconsumer.buffer;

import producerconsumer.model.Item;

/**
 * Buffer non sincronizzato, senza mutua esclusione. Non thread-safe per scelta.
 *
 * Array circolare con indici head/tail e contatore count. count++ e count-- non
 * sono atomiche (lettura, modifica, scrittura): con accessi concorrenti il contatore
 * diverge dal contenuto reale. Effetti: item sovrascritti prima della lettura (persi)
 * o letti più volte (duplicati).
 *
 * Attesa su buffer pieno/vuoto in polling: wait() richiede il possesso del monitor.
 * Struttura ad array e non LinkedList: una lista modificata concorrentemente può
 * corrompere i nodi e lanciare eccezioni; l'array resta sempre in uno stato valido.
 */
public class UnsafeBuffer implements Buffer {

    private final Item[] items;
    private int head;
    private int tail;
    // volatile: garantisce la visibilità tra thread (il polling legge sempre l'ultimo valore scritto),
    // non l'atomicità di count++ e count--.
    private volatile int count;

    public UnsafeBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.items = new Item[capacity];
        this.head = 0;
        this.tail = 0;
        this.count = 0;
    }

    @Override
    public void put(Item item) throws InterruptedException {
        if (item == null) {
            throw new NullPointerException("item must not be null");
        }
        while (count >= items.length) {
            pause();
        }
        items[tail] = item;
        tail = (tail + 1) % items.length;
        count++;
    }

    @Override
    public Item take() throws InterruptedException {
        while (true) {
            while (count <= 0) {
                pause();
            }
            Item item = items[head];
            // null solo se la cella non è mai stata scritta (count non coerente): nuovo tentativo
            if (item != null) {
                // la cella non viene svuotata: con count più alto del reale viene riletta (duplicato)
                head = (head + 1) % items.length;
                count--;
                return item;
            }
            pause();
        }
    }

    @Override
    public int capacity() {
        return items.length;
    }

    /**
     * Singola iterazione di attesa attiva. Il controllo dell'interruzione è esplicito:
     * Thread.yield(), a differenza di wait(), non lancia InterruptedException.
     *
     * @throws InterruptedException se il thread è stato interrotto
     */
    private void pause() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        Thread.yield();
    }
}
