package producerconsumer.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import producerconsumer.event.SimulationListener;
import producerconsumer.model.Item;
import producerconsumer.scenario.Scenario;
import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;
import producerconsumer.simulation.WorkerReport;

/**
 * Sessioni complete con input prefissato. Gli scenari sono finti: restituiscono un esito fisso
 * e registrano il listener ricevuto, così i test non dipendono dai tempi dei buffer reali.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class TuiTest {

    /** Scenario finto: notifica un put e un take, attende 250 ms, restituisce un esito pulito. */
    private static final class FakeScenario implements Scenario {
        volatile SimulationListener received;
        volatile SimulationConfig config;

        @Override
        public String getName() {
            return "Fake";
        }

        @Override
        public String getDescription() {
            return "A scenario used by the tests.";
        }

        @Override
        public SimulationResult run(SimulationConfig config, SimulationListener listener)
                throws InterruptedException {
            this.received = listener;
            this.config = config;
            Item item = new Item(0, 1);
            listener.beforePut("producer-1");
            listener.onPut("producer-1", item);
            listener.beforeTake("consumer-1");
            listener.onTake("consumer-1", item);
            Thread.sleep(250);
            return new SimulationResult(
                    List.of(new WorkerReport("producer-1", 1, false)),
                    List.of(new WorkerReport("consumer-1", 1, false)),
                    List.of(item), false, List.of(), 250);
        }
    }

    /** Scenario finto che fallisce con un'eccezione non controllata. */
    private static final class FailingScenario implements Scenario {
        @Override
        public String getName() {
            return "Failing";
        }

        @Override
        public String getDescription() {
            return "Always fails.";
        }

        @Override
        public SimulationResult run(SimulationConfig config, SimulationListener listener) {
            throw new IllegalStateException("boom");
        }
    }

    private final FakeScenario fake = new FakeScenario();

    private String session(String input) throws IOException, InterruptedException {
        StringWriter out = new StringWriter();
        List<Scenario> scenarios = List.of(fake, new FailingScenario());
        new Tui(new StringReader(input), out, scenarios).run();
        return Ansi.strip(out.toString());
    }

    @Test
    void menuListsScenariosAndQuits() throws Exception {
        String screen = session("q\n");

        assertTrue(screen.contains(Theme.APP_NAME));
        assertTrue(screen.contains(Theme.TAGLINE));
        assertTrue(screen.contains("1  Fake"));
        assertTrue(screen.contains("2  Failing"));
        assertTrue(screen.contains("1-2 open   q quit"));
    }

    @Test
    void endOfInputQuits() throws Exception {
        String screen = session("1\np\n");

        assertTrue(screen.contains("1-6 edit   b back"));
    }

    @Test
    void invalidChoiceShowsError() throws Exception {
        String screen = session("9\nq\n");

        assertTrue(screen.contains("Choose a number from 1 to 2."));
    }

    @Test
    void detailShowsDescriptionAndParameters() throws Exception {
        String screen = session("1\nb\nq\n");

        assertTrue(screen.contains("┌─ Fake ─"));
        assertTrue(screen.contains("A scenario used by the tests."));
        assertTrue(screen.contains("Producers         3"));
        assertTrue(screen.contains("Delay (ms)        200"));
        assertTrue(screen.contains("Enter run   p parameters   b back"));
    }

    @Test
    void invalidValueIsAskedAgain() throws Exception {
        String screen = session("1\np\n1\nabc\n99\n4\nb\nb\nq\n");

        assertTrue(screen.contains("New value for Producers (1-50), empty to cancel:"));
        assertTrue(screen.contains("Enter a whole number from 1 to 50."));
        assertTrue(screen.contains("Producers set to 4."));
        assertTrue(screen.contains("Producers         4"));
    }

    @Test
    void emptyValueCancelsTheEdit() throws Exception {
        String screen = session("1\np\n1\n\nb\nb\nq\n");

        assertFalse(screen.contains("set to"));
        assertTrue(screen.contains("Producers         3"));
    }

    @Test
    void runWithoutDelayShowsOnlyTheResult() throws Exception {
        String screen = session("1\np\n6\n0\nb\n\n\nb\nq\n");

        assertTrue(screen.contains("Delay (ms) set to 0."));
        assertTrue(screen.contains("Running Fake..."));
        assertTrue(screen.contains("Every item consumed exactly once."));
        assertFalse(screen.contains("Done."));
        assertSame(SimulationListener.NONE, fake.received);
        assertEquals(0, fake.config.getDelayMillis());
    }

    @Test
    void liveRunShowsFramesThenResult() throws Exception {
        String screen = session("1\n\n\n\nb\nq\n");

        assertTrue(fake.received instanceof LiveView);
        assertTrue(screen.contains("┌─ Buffer ─"));
        assertTrue(screen.contains("┌─ Threads ─"));
        assertTrue(screen.contains("producer-1   put   #0"));
        assertTrue(screen.contains("Done."));
        assertTrue(screen.contains("Enter results"));
        assertTrue(screen.contains("Every item consumed exactly once."));
    }

    @Test
    void failedRunShowsErrorOnTheDetail() throws Exception {
        String screen = session("2\n\nb\nq\n");

        assertTrue(screen.contains("Run failed: java.lang.IllegalStateException: boom"));
    }

    @Test
    void unknownCommandOnDetailShowsError() throws Exception {
        String screen = session("1\nx\nb\nq\n");

        assertTrue(screen.contains("Unknown command: x"));
    }

    @Test
    void everyDrawnLineFitsTheWidth() throws Exception {
        String screen = session("1\np\n1\nabc\nb\n\n\n\nb\nq\n");

        for (String line : screen.split("\n")) {
            assertTrue(line.length() <= Theme.WIDTH + 2, "riga troppo larga: " + line);
        }
    }
}
