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
 * count, head e tail sono aggiornati in tre passi espliciti: lettura in una variabile locale,
 * pausa di raceMillis, scrittura. La pausa allarga la finestra in cui un altro thread legge
 * il valore vecchio; con raceMillis = 0 non c'è pausa e l'aggiornamento equivale a count++.
 * Un'interruzione durante la pausa lascia l'aggiornamento incompleto.
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
    private final long raceMillis;

    /**
     * @param capacity   numero massimo di item
     * @param raceMillis pausa tra lettura e scrittura di count, head e tail, in millisecondi;
     *                   0 = nessuna pausa
     * @throws IllegalArgumentException se capacity <= 0 o raceMillis < 0
     */
    public UnsafeBuffer(int capacity, long raceMillis) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        if (raceMillis < 0) {
            throw new IllegalArgumentException("raceMillis must be >= 0");
        }
        this.raceMillis = raceMillis;
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
        int oldTail = tail;
        racePause();
        tail = (oldTail + 1) % items.length;
        int oldCount = count;
        racePause();
        count = oldCount + 1;
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
                int oldHead = head;
                racePause();
                head = (oldHead + 1) % items.length;
                int oldCount = count;
                racePause();
                count = oldCount - 1;
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
     * Pausa tra lettura e scrittura di un campo condiviso. Con raceMillis = 0 non sospende
     * il thread: Thread.sleep(0) potrebbe comunque cedere il processore.
     *
     * @throws InterruptedException se il thread viene interrotto durante la pausa
     */
    private void racePause() throws InterruptedException {
        if (raceMillis > 0) {
            Thread.sleep(raceMillis);
        }
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
