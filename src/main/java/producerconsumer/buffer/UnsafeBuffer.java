package producerconsumer.buffer;

import java.util.Objects;

import producerconsumer.model.Item;

/**
 * Buffer volutamente NON sincronizzato: mostra cosa succede senza mutua esclusione.
 *
 * Array circolare con indici head/tail e un contatore count. Le operazioni
 * count++ e count-- non sono atomiche (leggi, modifica, scrivi): due thread
 * possono sovrapporsi e il contatore smette di corrispondere al contenuto reale.
 * Risultato: item sovrascritti prima di essere letti (persi) o letti due volte (duplicati).
 *
 * Su buffer pieno o vuoto attende in polling (non può usare wait senza un lock).
 * Usa un array e non una LinkedList: una lista modificata da più thread insieme
 * può corrompersi e lanciare eccezioni, l'array no. Così lo scenario sbaglia
 * i conti ma non crasha.
 */
public class UnsafeBuffer implements Buffer {

    private final Item[] items;
    private int head;
    private int tail;
    // volatile: garantisce la visibilità tra thread (il polling vede sempre il valore aggiornato),
    // NON l'atomicità di count++ e count--. È proprio questo il difetto da mostrare.
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
        Objects.requireNonNull(item);
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
            // null solo se la cella non è mai stata scritta: il contatore mente, si riprova
            if (item != null) {
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
     * Un giro di attesa attiva. Controlla l'interruzione a mano, perché
     * Thread.yield() a differenza di wait() non lancia InterruptedException.
     */
    private void pause() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        Thread.yield();
    }
}
