package producerconsumer.tui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import producerconsumer.event.SimulationListener;
import producerconsumer.scenario.Scenario;
import producerconsumer.simulation.SimulationConfig;
import producerconsumer.simulation.SimulationResult;

/**
 * Ciclo delle schermate: menu → dettaglio dello scenario → esecuzione → risultato → dettaglio.
 * Un metodo per schermata.
 *
 * Input a righe: ogni comando è una riga confermata con Invio. Ogni schermata pulisce il
 * terminale e viene ridisegnata da capo: header, corpo, messaggio di una riga, footer, prompt.
 * La fine dell'input equivale a tornare indietro, fino all'uscita dal menu.
 *
 * Durante l'esecuzione lo scenario gira in un thread dedicato; il thread della TUI è l'unico che
 * scrive sul terminale.
 */
final class Tui {

    /** Intervallo di ridisegno della vista dal vivo, che è anche l'attesa massima di un join. */
    static final long REFRESH_MILLIS = 100;

    private static final String PROMPT = "> ";

    private final BufferedReader in;
    private final PrintWriter out;
    private final List<Scenario> scenarios;
    private final Parameters parameters = new Parameters();

    /**
     * @param in        sorgente dei comandi
     * @param out       destinazione delle schermate
     * @param scenarios scenari del menu, nell'ordine in cui sono mostrati
     */
    Tui(Reader in, Writer out, List<Scenario> scenarios) {
        this.in = new BufferedReader(in);
        this.out = new PrintWriter(out);
        this.scenarios = scenarios;
    }

    /**
     * Mostra il menu fino a "q" o alla fine dell'input.
     *
     * @throws IOException          se la lettura dell'input fallisce
     * @throws InterruptedException se il thread viene interrotto durante l'attesa di un run
     */
    void run() throws IOException, InterruptedException {
        menu();
        out.print(Theme.RESET);
        out.println();
        out.flush();
    }

    private void menu() throws IOException, InterruptedException {
        String message = null;
        while (true) {
            List<String> items = new ArrayList<>();
            for (int i = 0; i < scenarios.size(); i++) {
                items.add(Theme.text((i + 1) + "  " + scenarios.get(i).getName()));
            }
            draw(Box.box("Scenarios", items), message, "1-" + scenarios.size() + " open   q quit", true);
            message = null;

            String command = readCommand();
            if (command == null || command.equals("q")) {
                return;
            }
            int number = parseNumber(command);
            if (number >= 1 && number <= scenarios.size()) {
                detail(scenarios.get(number - 1));
            } else {
                message = Theme.error("Choose a number from 1 to " + scenarios.size() + ".");
            }
        }
    }

    private void detail(Scenario scenario) throws IOException, InterruptedException {
        String message = null;
        while (true) {
            List<String> body = new ArrayList<>();
            List<String> description = new ArrayList<>();
            for (String line : Box.wrap(scenario.getDescription(), Box.INNER)) {
                description.add(Theme.text(line));
            }
            body.addAll(Box.box(scenario.getName(), description));
            List<String> values = new ArrayList<>();
            for (int n = 1; n <= Parameters.COUNT; n++) {
                values.add(Theme.secondary(String.format("%-18s", parameters.label(n)))
                        + Theme.text(String.valueOf(parameters.value(n))));
            }
            if (parameters.isLive()) {
                values.add(Theme.secondary("Delay above 0: the run is shown live."));
            } else {
                values.add(Theme.secondary("Delay 0: only the result is shown."));
            }
            body.addAll(Box.box("Parameters", values));
            draw(body, message, "Enter run   p parameters   b back", true);
            message = null;

            String command = readCommand();
            if (command == null || command.equals("b")) {
                return;
            }
            if (command.isEmpty()) {
                message = execute(scenario);
            } else if (command.equals("p")) {
                parameters();
            } else {
                message = Theme.error("Unknown command: " + Box.truncate(command, 20));
            }
        }
    }

    private void parameters() throws IOException {
        String message = null;
        while (true) {
            draw(parameterList(), message, "1-" + Parameters.COUNT + " edit   b back", true);
            message = null;

            String command = readCommand();
            if (command == null || command.equals("b")) {
                return;
            }
            int number = parseNumber(command);
            if (number >= 1 && number <= Parameters.COUNT) {
                message = editValue(number);
            } else {
                message = Theme.error("Choose a number from 1 to " + Parameters.COUNT + ".");
            }
        }
    }

    /**
     * Chiede il nuovo valore finché è valido. Una riga vuota annulla la modifica.
     *
     * @return messaggio per l'elenco dei parametri, null se la modifica è annullata
     */
    private String editValue(int number) throws IOException {
        String range = parameters.min(number) + "-" + parameters.max(number);
        String message = Theme.secondary("New value for " + parameters.label(number) + " (" + range
                + "), empty to cancel:");
        while (true) {
            draw(parameterList(), message, "Enter confirm", true);

            String line = readCommand();
            if (line == null || line.isEmpty()) {
                return null;
            }
            long value;
            try {
                value = Long.parseLong(line);
            } catch (NumberFormatException e) {
                message = Theme.error("Enter a whole number from " + parameters.min(number) + " to "
                        + parameters.max(number) + ".");
                continue;
            }
            try {
                parameters.set(number, value);
                return Theme.success(parameters.label(number) + " set to " + value + ".");
            } catch (IllegalArgumentException e) {
                message = Theme.error("Enter a whole number from " + parameters.min(number) + " to "
                        + parameters.max(number) + ".");
            }
        }
    }

    private List<String> parameterList() {
        List<String> lines = new ArrayList<>();
        for (int n = 1; n <= Parameters.COUNT; n++) {
            String range = "(" + parameters.min(n) + "-" + parameters.max(n) + ")";
            lines.add(Theme.text(String.format("%d  %-18s%9d", n, parameters.label(n), parameters.value(n)))
                    + "   " + Theme.secondary(range));
        }
        return Box.box("Parameters", lines);
    }

    /**
     * Esegue lo scenario in un thread dedicato. Con ritardo positivo ridisegna la vista dal vivo
     * a ogni {@link #REFRESH_MILLIS}, poi la lascia ferma sull'ultimo stato fino a Invio.
     *
     * @return messaggio d'errore per il dettaglio se il run fallisce, altrimenti null
     */
    private String execute(Scenario scenario) throws IOException, InterruptedException {
        SimulationConfig config = parameters.toConfig();
        boolean live = parameters.isLive();
        LiveView view = new LiveView(config.getCapacity(), config.getItems(), config.getDelayMillis(),
                System.nanoTime());
        SimulationListener listener = SimulationListener.NONE;
        if (live) {
            listener = view;
        }
        String running = Theme.secondary("Running " + scenario.getName() + "...");

        Runner runner = new Runner(scenario, config, listener);
        runner.start();
        if (!live) {
            draw(new ArrayList<String>(), running, "Ctrl+C quit", false);
        }
        while (runner.isAlive()) {
            if (live) {
                view.drain();
                draw(view.lines(System.nanoTime(), false), running, "Ctrl+C quit", false);
            }
            runner.join(REFRESH_MILLIS);
        }

        SimulationResult result = runner.getResult();
        if (result == null) {
            return Theme.error(Box.truncate("Run failed: " + runner.getFailure(), Theme.WIDTH));
        }
        if (live) {
            view.drain();
            String outcome = Theme.success("Done.");
            if (result.isTimedOut()) {
                outcome = Theme.error("Stopped by timeout.");
            }
            draw(view.lines(System.nanoTime(), !result.isTimedOut()), outcome, "Enter results", true);
            readCommand();
        }
        result(scenario, result);
        return null;
    }

    private void result(Scenario scenario, SimulationResult result) throws IOException {
        draw(ResultView.lines(scenario.getName(), result), ResultView.verdict(result), "Enter back", true);
        readCommand();
    }

    /**
     * Ridisegna lo schermo: header, corpo, riga del messaggio (vuota se assente), footer e,
     * se richiesto, il prompt.
     */
    private void draw(List<String> body, String message, String shortcuts, boolean prompt) {
        out.print(Theme.CLEAR);
        for (String line : Box.header()) {
            out.println(line);
        }
        out.println();
        for (String line : body) {
            out.println(line);
        }
        out.println();
        if (message != null) {
            out.println(message);
        } else {
            out.println();
        }
        for (String line : Box.footer(shortcuts)) {
            out.println(line);
        }
        if (prompt) {
            out.print(Theme.accent(PROMPT));
        }
        out.flush();
    }

    /** @return riga letta, senza spazi ai lati e in minuscolo; null alla fine dell'input */
    private String readCommand() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        return line.trim().toLowerCase(Locale.ROOT);
    }

    /** @return il numero, oppure -1 se il testo non è un numero intero */
    private static int parseNumber(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Thread che esegue un run. Risultato ed eccezione vanno letti quando il thread non è più
     * vivo: la terminazione osservata con {@code isAlive()} o {@code join} rende visibili i valori.
     */
    private static final class Runner extends Thread {

        private final Scenario scenario;
        private final SimulationConfig config;
        private final SimulationListener listener;
        private SimulationResult result;
        private Exception failure;

        Runner(Scenario scenario, SimulationConfig config, SimulationListener listener) {
            super("runner");
            this.scenario = scenario;
            this.config = config;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                result = scenario.run(config, listener);
            } catch (InterruptedException e) {
                failure = e;
            } catch (RuntimeException e) {
                failure = e;
            }
        }

        /** @return esito del run, null se il run è fallito */
        SimulationResult getResult() {
            return result;
        }

        /** @return eccezione del run, null se il run è riuscito */
        Exception getFailure() {
            return failure;
        }
    }
}
