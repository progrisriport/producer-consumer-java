package producerconsumer.buffer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Contratto comune verificato con la pausa interna attiva: senza accessi concorrenti
 * la pausa rallenta le operazioni ma non ne cambia il risultato.
 */
class UnsafeBufferRaceTest extends BufferTest {

    @Override
    protected Buffer createBuffer(int capacity) {
        return new UnsafeBuffer(capacity, 1);
    }

    @Test
    void rejectsNegativeRaceMillis() {
        try {
            new UnsafeBuffer(1, -1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
