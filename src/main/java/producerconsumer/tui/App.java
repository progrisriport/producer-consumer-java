package producerconsumer.tui;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import producerconsumer.scenario.Scenarios;

/**
 * Punto di ingresso: collega la TUI al terminale. Input e output sono in UTF-8 per i
 * caratteri dei bordi.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        InputStreamReader in = new InputStreamReader(System.in, StandardCharsets.UTF_8);
        OutputStreamWriter out = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
        new Tui(in, out, Scenarios.all()).run();
    }
}
