package producerconsumer.counter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * tryAcquire() concede esattamente N permessi, poi restituisce sempre false,
 * anche con chiamate concorrenti.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class QuotaTest {

    @Test
    void grantsExactlyTheInitialPermits() {
        Quota quota = new Quota(3);
        assertTrue(quota.tryAcquire());
        assertTrue(quota.tryAcquire());
        assertTrue(quota.tryAcquire());
        assertFalse(quota.tryAcquire());
    }

    @Test
    void staysExhaustedAfterTheLastPermit() {
        Quota quota = new Quota(1);
        assertTrue(quota.tryAcquire());
        for (int i = 0; i < 100; i++) {
            assertFalse(quota.tryAcquire(), "nessun permesso dopo l'esaurimento");
        }
    }

    @Test
    void zeroPermitsGrantsNothing() {
        assertFalse(new Quota(0).tryAcquire());
    }

    @Test
    void rejectsNegativePermits() {
        try {
            new Quota(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Invariante: con più thread che chiedono permessi finché ne ricevono,
     * il numero totale di true è esattamente la quota iniziale.
     */
    @Test
    void grantsExactlyTheInitialPermitsUnderConcurrentAccess() throws InterruptedException {
        int permits = 10_000;
        int threads = 4;
        Quota quota = new Quota(permits);
        AtomicInteger granted = new AtomicInteger();

        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (quota.tryAcquire()) {
                        granted.incrementAndGet();
                    }
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertEquals(permits, granted.get());
        assertFalse(quota.tryAcquire());
    }
}
