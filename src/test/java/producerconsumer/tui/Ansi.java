package producerconsumer.tui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/** Supporto ai test della TUI: testo senza sequenze ANSI e controllo della larghezza. */
final class Ansi {

    private Ansi() {
    }

    /** @return {@code text} senza sequenze ANSI */
    static String strip(String text) {
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u001b' && i + 1 < text.length() && text.charAt(i + 1) == '[') {
                i += 2;
                while (i < text.length() && !Character.isLetter(text.charAt(i))) {
                    i++;
                }
                i++;
            } else {
                plain.append(c);
                i++;
            }
        }
        return plain.toString();
    }

    /** @return tutte le righe unite, senza sequenze ANSI */
    static String strip(List<String> lines) {
        StringBuilder plain = new StringBuilder();
        for (String line : lines) {
            plain.append(strip(line)).append('\n');
        }
        return plain.toString();
    }

    /** Verifica che ogni riga occupi esattamente {@link Theme#WIDTH} colonne visibili. */
    static void assertFullWidth(List<String> lines) {
        for (String line : lines) {
            assertEquals(Theme.WIDTH, strip(line).length(), "larghezza della riga: " + strip(line));
        }
    }
}
