package producerconsumer.counter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Id consecutivi a partire da 0, unici anche con chiamate concorrenti.
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class IdGeneratorTest {

    @Test
    void startsFromZero() {
        assertEquals(0, new IdGenerator().next());
    }

    @Test
    void returnsConsecutiveIds() {
        IdGenerator generator = new IdGenerator();
        for (int expected = 0; expected < 100; expected++) {
            assertEquals(expected, generator.next());
        }
    }

    @Test
    void generatorsAreIndependent() {
        IdGenerator first = new IdGenerator();
        IdGenerator second = new IdGenerator();
        first.next();
        first.next();
        assertEquals(0, second.next());
    }

    /**
     * Invariante: con T thread che chiamano next() N volte ciascuno,
     * si ottengono esattamente gli id 0..T*N-1, senza duplicati.
     */
    @Test
    void idsAreUniqueUnderConcurrentAccess() throws InterruptedException {
        int threads = 4;
        int callsPerThread = 10_000;
        IdGenerator generator = new IdGenerator();
        Set<Integer> ids = ConcurrentHashMap.newKeySet();

        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                for (int i = 0; i < callsPerThread; i++) {
                    ids.add(generator.next());
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        int total = threads * callsPerThread;
        assertEquals(total, ids.size());
        for (int id = 0; id < total; id++) {
            assertTrue(ids.contains(id), "manca l'id " + id);
        }
    }
}
