package producerconsumer.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import producerconsumer.model.Item;
import producerconsumer.simulation.SimulationResult;
import producerconsumer.simulation.WorkerReport;

class ResultViewTest {

    private static List<Item> items(int count) {
        List<Item> items = new ArrayList<>();
        for (int id = 0; id < count; id++) {
            items.add(new Item(id, 1));
        }
        return items;
    }

    private static SimulationResult clean() {
        return new SimulationResult(
                List.of(new WorkerReport("producer-1", 4, false)),
                List.of(new WorkerReport("consumer-1", 4, false)),
                items(4), false, List.of(), 1_234);
    }

    @Test
    void cleanRunHasGreenVerdict() {
        String verdict = ResultView.verdict(clean());

        assertEquals(Theme.success("Every item consumed exactly once."), verdict);
    }

    @Test
    void verdictListsEveryProblemInRed() {
        List<Item> consumed = items(3);
        consumed.add(new Item(0, 1));
        SimulationResult result = new SimulationResult(
                List.of(new WorkerReport("producer-1", 5, false)),
                List.of(new WorkerReport("consumer-1", 4, true)),
                consumed, true, List.of("consumer-1"), 1_000);

        String verdict = ResultView.verdict(result);
        assertEquals(Theme.error("Lost 2, duplicated 1, stopped by timeout, 1 threads still running."), verdict);
    }

    @Test
    void showsTotalsUnderTheScenarioName() {
        List<String> lines = ResultView.lines("Monitor", clean());

        Ansi.assertFullWidth(lines);
        String text = Ansi.strip(lines);
        assertTrue(text.contains("┌─ Monitor ─"));
        assertTrue(text.contains("Produced             4"));
        assertTrue(text.contains("Elapsed          1.2 s"));
        assertTrue(text.contains("producer-1         4"));
        assertFalse(text.contains("Duplicates"), "nessun box dei duplicati se non ce ne sono");
    }

    @Test
    void limitsThreadRowsAndMarksInterrupted() {
        List<WorkerReport> producers = new ArrayList<>();
        for (int id = 1; id <= 20; id++) {
            producers.add(new WorkerReport("producer-" + id, 1, id == 1));
        }
        SimulationResult result = new SimulationResult(producers,
                List.of(new WorkerReport("consumer-1", 20, false)), items(20), false, List.of(), 10);

        List<String> lines = ResultView.lines("X", result);
        Ansi.assertFullWidth(lines);
        String text = Ansi.strip(lines);
        int max = ResultView.MAX_THREAD_ROWS * ResultView.THREAD_COLUMNS;
        assertTrue(text.contains("producer-" + max + " "));
        assertFalse(text.contains("producer-" + (max + 1) + " "));
        assertTrue(text.contains("... and " + (21 - max) + " more"));
        assertTrue(text.contains("1*"));
        assertTrue(text.contains("* interrupted"));
    }

    @Test
    void showsThreadsStillRunning() {
        SimulationResult result = new SimulationResult(
                List.of(new WorkerReport("producer-1", 0, false)),
                List.of(new WorkerReport("consumer-1", 0, false)),
                List.of(), true, List.of("consumer-1"), 10);

        String text = Ansi.strip(ResultView.lines("X", result));
        assertTrue(text.contains("Still running: consumer-1"));
    }

    @Test
    void showsFirstDuplicatesSortedById() {
        List<Item> consumed = new ArrayList<>();
        for (int id = 14; id >= 0; id--) {
            consumed.add(new Item(id, 1));
            consumed.add(new Item(id, 1));
        }
        SimulationResult result = new SimulationResult(
                List.of(new WorkerReport("producer-1", 15, false)),
                List.of(new WorkerReport("consumer-1", 30, false)),
                consumed, false, List.of(), 10);

        List<String> lines = ResultView.lines("X", result);
        Ansi.assertFullWidth(lines);
        String text = Ansi.strip(lines);
        assertTrue(text.contains("┌─ Duplicates ─"));
        assertTrue(text.indexOf("#0 x2") < text.indexOf("#9 x2"));
        assertFalse(text.contains("#10 x2"));
        assertTrue(text.contains("... and 5 more"));
    }
}
