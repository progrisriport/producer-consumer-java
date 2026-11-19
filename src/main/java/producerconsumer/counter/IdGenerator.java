package producerconsumer.counter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generatore di id unici per gli item, condiviso da tutti i producer.
 * Sicuro con accessi concorrenti: incremento atomico senza lock.
 */
public final class IdGenerator {

    private final AtomicInteger next = new AtomicInteger();

    /**
     * Restituisce il prossimo id: 0, 1, 2, ...
     * Lettura e incremento avvengono in un'unica operazione atomica.
     *
     * @return id mai restituito prima da questo generatore
     */
    public int next() {
        return next.getAndIncrement();
    }
}
