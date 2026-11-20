package producerconsumer.counter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Numero totale di item da produrre, condiviso da tutti i producer.
 * Ogni producer chiede un permesso prima di produrre un item.
 */
public final class Quota {

    /** Permessi residui. Può scendere sotto zero: ogni valore {@code <= 0} indica quota esaurita. */
    private final AtomicInteger remaining;

    /**
     * @param permits numero di permessi concessi in totale
     * @throws IllegalArgumentException se permits è negativo
     */
    public Quota(int permits) {
        if (permits < 0) {
            throw new IllegalArgumentException("permits must be >= 0: " + permits);
        }
        this.remaining = new AtomicInteger(permits);
    }

    /**
     * Prende un permesso, se disponibile.
     * Controllo e decremento sono un'unica operazione atomica: due thread non possono
     * ottenere lo stesso permesso (nessun check-then-act).
     *
     * @return true se il permesso è stato concesso, false se la quota è esaurita
     */
    public boolean tryAcquire() {
        return remaining.getAndDecrement() > 0;
    }
}
