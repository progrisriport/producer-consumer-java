package producerconsumer.tui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import producerconsumer.event.SimulationListener;
import producerconsumer.model.Item;

/**
 * Vista dal vivo di un run: riceve gli eventi dai thread della simulazione e produce le righe
 * dei box buffer, thread e log.
 *
 * I metodi del listener inseriscono gli eventi in una coda thread-safe, con l'istante di
 * arrivo, e non fanno altro. {@link #drain()} e {@link #lines(long, boolean)} sono chiamati da
 * un solo thread, quello che disegna: lo stato derivato dagli eventi non è condiviso.
 *
 * Stato di un thread, ricavato dal suo ultimo evento:
 * <ul>
 *   <li>dopo before: operazione in corso, poi in attesa oltre {@link #WAIT_FACTOR} volte il
 *       ritardo (buffer pieno per i producer, vuoto per i consumer);</li>
 *   <li>dopo on: item appena inserito o prelevato per metà del ritardo, poi in pausa.</li>
 * </ul>
 */
final class LiveView implements SimulationListener {

    /** Soglia dell'attesa in multipli del ritardo; nel buffer non sincronizzato un'operazione dura due ritardi. */
    static final int WAIT_FACTOR = 3;
    static final int MAX_THREAD_ROWS = 10;
    static final int LOG_SIZE = 6;
    static final int BAR_WIDTH = 20;

    private static final int BEFORE_PUT = 0;
    private static final int ON_PUT = 1;
    private static final int BEFORE_TAKE = 2;
    private static final int ON_TAKE = 3;

    /** Evento ricevuto dal listener. Immutabile. */
    private static final class Event {
        final int type;
        final String thread;
        final Item item;
        final long nanos;

        Event(int type, String thread, Item item, long nanos) {
            this.type = type;
            this.thread = thread;
            this.item = item;
            this.nanos = nanos;
        }
    }

    /** Ultimo evento e conteggio di un thread. */
    private static final class ThreadState {
        int lastType;
        Item lastItem;
        long lastNanos;
        int count;
    }

    private final ConcurrentLinkedQueue<Event> queue = new ConcurrentLinkedQueue<>();

    private final int capacity;
    private final int items;
    private final long delayMillis;
    private final long startNanos;

    private final Map<String, ThreadState> threads = new TreeMap<>(new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
            return compareThreadNames(a, b);
        }
    });
    private final Deque<String> log = new ArrayDeque<>();
    private int puts;
    private int takes;

    /**
     * @param capacity    capacità del buffer
     * @param items       item totali da produrre
     * @param delayMillis ritardo dopo ogni operazione, in millisecondi
     * @param startNanos  istante di inizio del run, da {@link System#nanoTime()}
     */
    LiveView(int capacity, int items, long delayMillis, long startNanos) {
        this.capacity = capacity;
        this.items = items;
        this.delayMillis = delayMillis;
        this.startNanos = startNanos;
    }

    @Override
    public void beforePut(String thread) {
        queue.offer(new Event(BEFORE_PUT, thread, null, System.nanoTime()));
    }

    @Override
    public void onPut(String thread, Item item) {
        queue.offer(new Event(ON_PUT, thread, item, System.nanoTime()));
    }

    @Override
    public void beforeTake(String thread) {
        queue.offer(new Event(BEFORE_TAKE, thread, null, System.nanoTime()));
    }

    @Override
    public void onTake(String thread, Item item) {
        queue.offer(new Event(ON_TAKE, thread, item, System.nanoTime()));
    }

    /** Applica allo stato della vista tutti gli eventi arrivati finora. */
    void drain() {
        Event event = queue.poll();
        while (event != null) {
            apply(event);
            event = queue.poll();
        }
    }

    private void apply(Event event) {
        ThreadState state = threads.get(event.thread);
        if (state == null) {
            state = new ThreadState();
            threads.put(event.thread, state);
        }
        state.lastType = event.type;
        state.lastNanos = event.nanos;
        if (event.item != null) {
            state.lastItem = event.item;
        }
        switch (event.type) {
            case ON_PUT:
                puts++;
                state.count++;
                addLog(String.format("%-12s put   #%d", event.thread, event.item.getId()));
                break;
            case ON_TAKE:
                takes++;
                state.count++;
                addLog(String.format("%-12s took  #%d", event.thread, event.item.getId()));
                break;
            default:
                break;
        }
    }

    private void addLog(String line) {
        log.addLast(line);
        if (log.size() > LOG_SIZE) {
            log.removeFirst();
        }
    }

    /**
     * @param nowNanos istante corrente, da {@link System#nanoTime()}
     * @param finished true se il run è terminato normalmente: ogni thread è mostrato come "done"
     * @return righe dei box buffer, thread e log
     */
    List<String> lines(long nowNanos, boolean finished) {
        List<String> lines = new ArrayList<>();
        lines.addAll(Box.box("Buffer", bufferLines(nowNanos)));
        lines.addAll(Box.box("Threads", threadLines(nowNanos, finished)));
        lines.addAll(Box.box("Log", logLines()));
        return lines;
    }

    private List<String> bufferLines(long nowNanos) {
        List<String> body = new ArrayList<>();
        int inBuffer = puts - takes;
        body.add(bar(inBuffer) + "  " + Theme.text(inBuffer + " / " + capacity));
        body.add(Theme.secondary("Produced ") + Theme.text(String.format("%7d / %d", puts, items))
                + Theme.secondary("   Consumed ") + Theme.text(String.format("%7d", takes)));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(nowNanos - startNanos);
        body.add(Theme.secondary("Elapsed  ") + Theme.text(Box.seconds(elapsedMillis)));
        return body;
    }

    /**
     * Barra di riempimento: una cella per posto fino a {@link #BAR_WIDTH} posti, oltre scalata.
     * Il valore è limitato a [0, capacità]: nel buffer non sincronizzato put − take può uscirne.
     */
    private String bar(int inBuffer) {
        int cells = capacity;
        if (cells > BAR_WIDTH) {
            cells = BAR_WIDTH;
        }
        int clamped = inBuffer;
        if (clamped < 0) {
            clamped = 0;
        }
        if (clamped > capacity) {
            clamped = capacity;
        }
        int filled = clamped * cells / capacity;
        if (clamped > 0 && filled == 0) {
            filled = 1;
        }
        return Theme.text(Box.repeat("█", filled)) + Theme.border(Box.repeat("░", cells - filled));
    }

    private List<String> threadLines(long nowNanos, boolean finished) {
        List<String> body = new ArrayList<>();
        body.add(Theme.secondary(String.format("%-12s%-22s%8s", "Thread", "State", "Items")));
        int shown = 0;
        for (Map.Entry<String, ThreadState> entry : threads.entrySet()) {
            if (shown == MAX_THREAD_ROWS) {
                break;
            }
            ThreadState state = entry.getValue();
            String name = Theme.text(String.format("%-12s", entry.getKey()));
            String count = Theme.text(String.format("%8d", state.count));
            String stateText;
            if (finished) {
                stateText = Theme.text(String.format("%-22s", "done"));
            } else {
                stateText = describe(state, nowNanos);
            }
            body.add(name + stateText + count);
            shown++;
        }
        if (threads.size() > MAX_THREAD_ROWS) {
            body.add(Theme.secondary("... and " + (threads.size() - MAX_THREAD_ROWS) + " more"));
        }
        return body;
    }

    /** @return stato del thread, colorato e largo 22 colonne */
    private String describe(ThreadState state, long nowNanos) {
        long sinceMillis = TimeUnit.NANOSECONDS.toMillis(nowNanos - state.lastNanos);
        switch (state.lastType) {
            case BEFORE_PUT:
                if (sinceMillis > WAIT_FACTOR * delayMillis) {
                    return Theme.accent(String.format("%-22s", "waiting: buffer full"));
                }
                return Theme.text(String.format("%-22s", "putting"));
            case BEFORE_TAKE:
                if (sinceMillis > WAIT_FACTOR * delayMillis) {
                    return Theme.accent(String.format("%-22s", "waiting: buffer empty"));
                }
                return Theme.text(String.format("%-22s", "taking"));
            case ON_PUT:
                if (sinceMillis <= delayMillis / 2) {
                    return Theme.text(String.format("%-22s", "put #" + state.lastItem.getId()));
                }
                return Theme.secondary(String.format("%-22s", "resting"));
            default:
                if (sinceMillis <= delayMillis / 2) {
                    return Theme.text(String.format("%-22s", "took #" + state.lastItem.getId()));
                }
                return Theme.secondary(String.format("%-22s", "resting"));
        }
    }

    private List<String> logLines() {
        List<String> body = new ArrayList<>();
        for (String line : log) {
            body.add(Theme.text(line));
        }
        if (body.isEmpty()) {
            body.add(Theme.secondary("No operations yet."));
        }
        return body;
    }

    /**
     * Ordine delle righe: producer, poi consumer, poi altri nomi; a parità di tipo per numero.
     */
    static int compareThreadNames(String a, String b) {
        int kindA = kind(a);
        int kindB = kind(b);
        if (kindA != kindB) {
            return Integer.compare(kindA, kindB);
        }
        int numberA = number(a);
        int numberB = number(b);
        if (numberA != numberB) {
            return Integer.compare(numberA, numberB);
        }
        return a.compareTo(b);
    }

    private static int kind(String name) {
        if (name.startsWith("producer-")) {
            return 0;
        }
        if (name.startsWith("consumer-")) {
            return 1;
        }
        return 2;
    }

    /** @return numero dopo l'ultimo trattino, 0 se assente */
    private static int number(String name) {
        int dash = name.lastIndexOf('-');
        if (dash < 0) {
            return 0;
        }
        try {
            return Integer.parseInt(name.substring(dash + 1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
