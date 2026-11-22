package producerconsumer.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import producerconsumer.model.Item;

/**
 * Gli eventi sono registrati con l'istante reale: gli stati che dipendono dal tempo sono
 * verificati passando a {@code lines} un istante abbastanza lontano.
 */
class LiveViewTest {

    private static final long DELAY = 100;

    private static LiveView view() {
        return new LiveView(5, 40, DELAY, System.nanoTime());
    }

    private static long later(long millis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    }

    @Test
    void eventsAreAppliedOnlyOnDrain() {
        LiveView view = view();
        view.beforePut("producer-1");
        view.onPut("producer-1", new Item(0, 1));

        String before = Ansi.strip(view.lines(System.nanoTime(), false));
        assertTrue(before.contains("No operations yet."));

        view.drain();
        String after = Ansi.strip(view.lines(System.nanoTime(), false));
        assertTrue(after.contains("producer-1   put   #0"));
        assertTrue(after.contains("1 / 5"));
    }

    @Test
    void countsPutsAndTakes() {
        LiveView view = view();
        view.onPut("producer-1", new Item(0, 1));
        view.onPut("producer-1", new Item(1, 1));
        view.onTake("consumer-1", new Item(0, 1));
        view.drain();

        String text = Ansi.strip(view.lines(System.nanoTime(), false));
        assertTrue(text.contains("1 / 5"), "nel buffer: put - take");
        assertTrue(text.contains("Produced       2 / 40"));
        assertTrue(text.contains("Consumed       1"));
    }

    @Test
    void logKeepsTheLastEvents() {
        LiveView view = view();
        for (int id = 0; id < LiveView.LOG_SIZE + 3; id++) {
            view.onPut("producer-1", new Item(id, 1));
        }
        view.drain();

        String text = Ansi.strip(view.lines(System.nanoTime(), false));
        assertFalse(text.contains("#2\n") || text.contains("#2 "), "gli eventi più vecchi escono dal log");
        assertTrue(text.contains("put   #3"));
        assertTrue(text.contains("put   #8"));
    }

    @Test
    void showsOperationInProgressThenWaiting() {
        LiveView view = view();
        view.beforePut("producer-1");
        view.beforeTake("consumer-1");
        view.drain();

        String now = Ansi.strip(view.lines(System.nanoTime(), false));
        assertTrue(now.contains("putting"));
        assertTrue(now.contains("taking"));

        String later = Ansi.strip(view.lines(later(LiveView.WAIT_FACTOR * DELAY + 50), false));
        assertTrue(later.contains("waiting: buffer full"));
        assertTrue(later.contains("waiting: buffer empty"));
    }

    @Test
    void showsLastItemThenResting() {
        LiveView view = view();
        view.onTake("consumer-1", new Item(7, 1));
        view.drain();

        assertTrue(Ansi.strip(view.lines(System.nanoTime(), false)).contains("took #7"));
        assertTrue(Ansi.strip(view.lines(later(DELAY), false)).contains("resting"));
    }

    @Test
    void finishedRunShowsEveryThreadAsDone() {
        LiveView view = view();
        view.beforeTake("consumer-1");
        view.drain();

        String text = Ansi.strip(view.lines(later(10_000), true));
        assertTrue(text.contains("done"));
        assertFalse(text.contains("waiting"));
    }

    @Test
    void listsProducersBeforeConsumersInNumericOrder() {
        LiveView view = view();
        view.beforeTake("consumer-2");
        view.beforeTake("consumer-1");
        view.beforePut("producer-10");
        view.beforePut("producer-2");
        view.drain();

        String text = Ansi.strip(view.lines(System.nanoTime(), false));
        int p2 = text.indexOf("producer-2 ");
        int p10 = text.indexOf("producer-10");
        int c1 = text.indexOf("consumer-1");
        int c2 = text.indexOf("consumer-2");
        assertTrue(p2 < p10 && p10 < c1 && c1 < c2, text);
    }

    @Test
    void limitsThreadRows() {
        LiveView view = view();
        for (int id = 1; id <= LiveView.MAX_THREAD_ROWS + 4; id++) {
            view.beforePut("producer-" + id);
        }
        view.drain();

        String text = Ansi.strip(view.lines(System.nanoTime(), false));
        assertTrue(text.contains("... and 4 more"));
        assertFalse(text.contains("producer-" + (LiveView.MAX_THREAD_ROWS + 1) + " "));
    }

    @Test
    void barIsClampedWhenTakesExceedPuts() {
        LiveView view = view();
        view.onTake("consumer-1", new Item(0, 1));
        view.drain();

        List<String> lines = view.lines(System.nanoTime(), false);
        Ansi.assertFullWidth(lines);
        assertTrue(Ansi.strip(lines).contains("░░░░░  -1 / 5"));
    }

    @Test
    void largeCapacityBarIsScaled() {
        LiveView view = new LiveView(1_000, 40, DELAY, System.nanoTime());
        for (int id = 0; id < 500; id++) {
            view.onPut("producer-1", new Item(id, 1));
        }
        view.drain();

        String text = Ansi.strip(view.lines(System.nanoTime(), false));
        assertTrue(text.contains(Box.repeat("█", 10) + Box.repeat("░", 10) + "  500 / 1000"));
    }

    @Test
    void everyLineHasFullWidth() {
        LiveView view = view();
        view.beforePut("producer-1");
        view.onPut("producer-1", new Item(123_456, 1));
        view.drain();

        Ansi.assertFullWidth(view.lines(System.nanoTime(), false));
    }
}
