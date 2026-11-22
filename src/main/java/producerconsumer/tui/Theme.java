package producerconsumer.tui;

/**
 * Costanti visive della TUI: colori, larghezza, testi dell'header e valori predefiniti dei
 * parametri. Unico punto in cui sono definite le sequenze ANSI. Non istanziabile.
 *
 * I colori sono in truecolor a 24 bit ({@code ESC[38;2;r;g;bm}) e riguardano solo il testo:
 * lo sfondo resta quello del terminale. Ogni segmento colorato termina con {@link #RESET}.
 */
final class Theme {

    /** Larghezza fissa di ogni riga, bordi compresi. */
    static final int WIDTH = 60;

    static final String APP_NAME = "producer-consumer";
    static final String TAGLINE = "Watch threads share a buffer";

    static final int DEFAULT_PRODUCERS = 3;
    static final int DEFAULT_CONSUMERS = 2;
    static final int DEFAULT_ITEMS = 40;
    static final int DEFAULT_CAPACITY = 5;
    static final long DEFAULT_TIMEOUT_MILLIS = 30_000;
    static final long DEFAULT_DELAY_MILLIS = 200;

    static final String RESET = "\u001b[0m";
    /** Cursore in alto a sinistra e pulizia dello schermo. */
    static final String CLEAR = "\u001b[H\u001b[2J";

    private static final String BOLD = "\u001b[1m";
    private static final String ACCENT = "\u001b[38;2;232;184;75m";
    private static final String TEXT = "\u001b[38;2;212;212;212m";
    private static final String SECONDARY = "\u001b[38;2;122;122;122m";
    private static final String BORDER = "\u001b[38;2;58;58;58m";
    private static final String ERROR = "\u001b[38;2;232;91;75m";
    private static final String SUCCESS = "\u001b[38;2;75;232;122m";

    private Theme() {
    }

    /** Titoli dei box ed elementi che richiedono attenzione. */
    static String accent(String text) {
        return paint(ACCENT, text);
    }

    /** Nome dell'applicazione nell'header. */
    static String accentBold(String text) {
        return paint(BOLD + ACCENT, text);
    }

    /** Testo normale. */
    static String text(String text) {
        return paint(TEXT, text);
    }

    /** Etichette, suggerimenti, scorciatoie. */
    static String secondary(String text) {
        return paint(SECONDARY, text);
    }

    /** Bordi e separatori. */
    static String border(String text) {
        return paint(BORDER, text);
    }

    static String error(String text) {
        return paint(ERROR, text);
    }

    static String success(String text) {
        return paint(SUCCESS, text);
    }

    private static String paint(String code, String text) {
        return code + text + RESET;
    }
}
