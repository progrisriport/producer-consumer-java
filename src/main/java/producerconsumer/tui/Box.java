package producerconsumer.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Costruzione delle righe di testo della TUI: header, box con titolo nel bordo superiore,
 * footer e funzioni di formattazione. Ogni riga prodotta occupa esattamente
 * {@link Theme#WIDTH} colonne visibili. Non istanziabile.
 *
 * Le righe possono contenere sequenze ANSI: la larghezza visibile esclude le sequenze.
 */
final class Box {

    /** Colonne disponibili per il contenuto di un box: larghezza meno "│ " e " │". */
    static final int INNER = Theme.WIDTH - 4;

    private Box() {
    }

    /** @return header: nome dell'applicazione nel bordo superiore, tagline nel corpo */
    static List<String> header() {
        List<String> body = new ArrayList<>();
        body.add(Theme.secondary(Theme.TAGLINE));
        return frame(Theme.accentBold(Theme.APP_NAME), Theme.APP_NAME.length(), body);
    }

    /**
     * @param title titolo nel bordo superiore, testo semplice
     * @param body  righe del contenuto, ciascuna larga al massimo {@link #INNER} colonne visibili
     * @return righe del box
     * @throws IllegalArgumentException se una riga del contenuto è troppo larga
     */
    static List<String> box(String title, List<String> body) {
        String plainTitle = truncate(title, Theme.WIDTH - 6);
        return frame(Theme.accent(plainTitle), plainTitle.length(), body);
    }

    /**
     * @param shortcuts scorciatoie, testo semplice
     * @return separatore e riga delle scorciatoie
     */
    static List<String> footer(String shortcuts) {
        List<String> lines = new ArrayList<>();
        lines.add(Theme.border(repeat("─", Theme.WIDTH)));
        lines.add(Theme.secondary(truncate(shortcuts, Theme.WIDTH)));
        return lines;
    }

    private static List<String> frame(String title, int titleLength, List<String> body) {
        List<String> lines = new ArrayList<>();
        int dashes = Theme.WIDTH - 5 - titleLength;
        lines.add(Theme.border("┌─ ") + title + Theme.border(" " + repeat("─", dashes) + "┐"));
        for (String line : body) {
            int length = visibleLength(line);
            if (length > INNER) {
                throw new IllegalArgumentException("line too long (" + length + " > " + INNER + "): " + line);
            }
            lines.add(Theme.border("│ ") + line + repeat(" ", INNER - length) + Theme.border(" │"));
        }
        lines.add(Theme.border("└" + repeat("─", Theme.WIDTH - 2) + "┘"));
        return lines;
    }

    /**
     * Lunghezza visibile: le sequenze ANSI ({@code ESC [ ... lettera}) non occupano colonne.
     *
     * @param line riga, anche con sequenze ANSI
     * @return numero di colonne occupate
     */
    static int visibleLength(String line) {
        int length = 0;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\u001b' && i + 1 < line.length() && line.charAt(i + 1) == '[') {
                i += 2;
                while (i < line.length() && !Character.isLetter(line.charAt(i))) {
                    i++;
                }
                i++;
            } else {
                length++;
                i++;
            }
        }
        return length;
    }

    /**
     * @param plain testo senza sequenze ANSI
     * @param width larghezza massima
     * @return il testo, oppure i primi caratteri seguiti da "..." se più lungo di {@code width}
     */
    static String truncate(String plain, int width) {
        if (plain.length() <= width) {
            return plain;
        }
        return plain.substring(0, width - 3) + "...";
    }

    /**
     * A capo per parole. Una parola più lunga di {@code width} viene troncata.
     *
     * @param plain testo senza sequenze ANSI
     * @param width larghezza massima di una riga
     * @return righe, nessuna più lunga di {@code width}
     */
    static List<String> wrap(String plain, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : plain.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String fitted = truncate(word, width);
            if (current.length() == 0) {
                current.append(fitted);
            } else if (current.length() + 1 + fitted.length() <= width) {
                current.append(' ').append(fitted);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(fitted);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    /**
     * @param millis durata in millisecondi
     * @return durata in secondi con un decimale, ad esempio "4.1 s"
     */
    static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1f s", millis / 1000.0);
    }

    static String repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(text);
        }
        return builder.toString();
    }
}
