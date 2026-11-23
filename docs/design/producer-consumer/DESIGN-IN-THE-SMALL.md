# DESIGN-IN-THE-SMALL — producer-consumer

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato: reviewed
> Documenti: [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [BEHAVIOR](BEHAVIOR.md) · [DESIGN-PATTERNS](DESIGN-PATTERNS.md) · [TEST-STRATEGY](TEST-STRATEGY.md) · [ANALYSIS](ANALYSIS.md) · [ADR](ADR/ADR-0001-stack.md) · [DESIGN-STATUS](DESIGN-STATUS.md)

Per ogni interfaccia chiave: firma, precondizioni, postcondizioni, errori, esempio d'uso.
Le firme sono quelle del codice (`src/main/java/producerconsumer`). Gli esempi rispettano lo stile di
[ADR-0017](ADR/ADR-0017-stile-codice-classico.md).

## 1. Convenzioni

### 1.1 Naming

| Elemento | Convenzione | Esempi |
|---|---|---|
| Package | minuscolo, singolare, per responsabilità | `buffer`, `counter`, `event`, `actor` |
| Classi concrete di dominio | nome sostantivo | `Item`, `Quota`, `Simulation` |
| Implementazioni di `Buffer` | `<Meccanismo>Buffer` | `MonitorBuffer`, `LockBuffer` |
| Scenari | `<Meccanismo>Scenario`, nome mostrato in inglese | `MonitorScenario` → "Monitor" |
| Getter | `getX()`; booleani `isX()` o `wasX()` per eventi passati | `getProduced()`, `isTimedOut()`, `wasInterrupted()` |
| Eventi del listener | `beforeX` prima della chiamata, `onX` dopo l'operazione riuscita | `beforePut`, `onTake` |
| Nomi dei thread | `producer-<id>`, `consumer-<id>`, `terminator`, `runner` (id da 1) | `producer-1` |
| Id degli item | da 0, `-1` riservato alla pillola | `new Item(0, 1)` |
| Costanti | `UPPER_SNAKE_CASE`; limiti `MIN_*`/`MAX_*` | `MAX_PRODUCERS`, `GRACE_MILLIS`, `WIDTH` |
| Test | classe `<Classe>Test`; metodi in `camelCase` descrittivi senza prefisso `test` | `takeWaitsWhileEmpty` |
| Lingua | identificativi e messaggi di eccezione in inglese; Javadoc e commenti in italiano impersonale | — |

### 1.2 Errori

| Situazione | Meccanismo | Dove |
|---|---|---|
| Argomento `null` non ammesso (`Buffer.put`) | `NullPointerException("item must not be null")` | tutti i buffer |
| Parametro fuori intervallo | `IllegalArgumentException("<name> must be between <min> and <max>: <value>")` | `SimulationConfig`, `Parameters.set` |
| Capacità `<= 0`, `raceMillis < 0`, permessi negativi | `IllegalArgumentException` con messaggio inglese | buffer, `Quota` |
| Interruzione del thread durante un'attesa | `InterruptedException` **propagata** fino al confine del thread, mai inghiottita nel buffer | `Buffer`, `Simulation.run`, `Scenario.run` |
| Interruzione al confine del thread (`run()`) | catturata, flag `interrupted = true`, il thread termina; il flag non viene ripristinato con `Thread.currentThread().interrupt()` perché `run()` è il punto più esterno | `Producer`, `Consumer` |
| Run fallito nella TUI | `Runner` cattura `InterruptedException` e `RuntimeException`; la TUI mostra "Run failed: ..." in rosso | `Tui.Runner` |
| Riga troppo larga in un box | `IllegalArgumentException` (errore di programmazione, non di input) | `Box.box` |
| Input utente non valido | nessuna eccezione verso l'alto: messaggio rosso e nuova richiesta | `Tui` |

Errori non controllati diversi da quelli sopra (es. `Error`) non sono gestiti: in `Runner.run` un `Error` termina il thread senza
risultato né fallimento registrato, e la TUI leggerebbe `result == null` con `failure == null` (vedi R-12 in [ANALYSIS](ANALYSIS.md)).

### 1.3 Visibilità tra thread

| Dato | Scritto da | Letto da | Garanzia |
|---|---|---|---|
| `Producer.produced`, `Producer.interrupted` | il producer | `Simulation.buildResult` | `join()` / `isAlive() == false` (happens-before) |
| `Consumer.consumed`, `Consumer.interrupted` | il consumer | `Simulation.buildResult` | idem |
| `Runner.result`, `Runner.failure` | `runner` | thread main | idem |
| `UnsafeBuffer.count` | più thread | più thread | `volatile` (visibilità, non atomicità) |
| `UnsafeBuffer.head`, `tail`, `items[]` | più thread | più thread | nessuna (voluta) |
| Coda di `LiveView` | thread della simulazione | thread main | `ConcurrentLinkedQueue` |
| Stato di `LiveView` (mappa, log, contatori) | thread main | thread main | confinamento al thread |
| `Parameters` | thread main | thread main | confinamento al thread |

## 2. `model.Item`

```java
public final class Item {
    public static final Item POISON;                 // new Item(-1, -1)
    public Item(int id, int producerId);
    public int getId();
    public int getProducerId();
    public boolean isPoison();                       // this == POISON
    public boolean equals(Object o);                 // stesso id e stesso producerId
    public int hashCode();
    public String toString();                        // "Item[id=.., producerId=..]"
}
```

- Precondizioni: nessuna (il costruttore non valida i valori).
- Postcondizioni: immutabile; `isPoison()` è vero solo per l'istanza `POISON`.
- Errori: nessuno.
- Attenzione: `new Item(-1, -1).equals(Item.POISON)` è vero ma `isPoison()` è falso (R-8).

```java
Item item = new Item(generator.next(), 3);
if (item.isPoison()) { /* mai vero per un item prodotto da Producer */ }
```

## 3. `counter`

```java
public final class IdGenerator { public int next(); }

public final class Quota {
    public Quota(int permits);          // IllegalArgumentException se permits < 0
    public boolean tryAcquire();
}
```

| | `IdGenerator.next()` | `Quota.tryAcquire()` |
|---|---|---|
| Precondizioni | nessuna | nessuna |
| Postcondizioni | restituisce 0, 1, 2, ... senza ripetizioni, anche con thread concorrenti | restituisce `true` esattamente `permits` volte in totale, poi sempre `false` |
| Errori | nessuno | nessuno |
| Thread-safety | `AtomicInteger.getAndIncrement()` | `AtomicInteger.getAndDecrement() > 0` |

```java
Quota quota = new Quota(3);
while (quota.tryAcquire()) {
    int id = generator.next();   // eseguito 3 volte in totale, sommando tutti i thread
}
```

## 4. `buffer.Buffer`

```java
public interface Buffer {
    void put(Item item) throws InterruptedException;
    Item take() throws InterruptedException;
    int capacity();
}
```

| Operazione | Precondizioni | Postcondizioni | Errori |
|---|---|---|---|
| `put(item)` | `item != null` | l'item è nel buffer; se il buffer era pieno, il thread ha atteso un posto libero | `NullPointerException` se `item == null`; `InterruptedException` se il thread è interrotto durante l'attesa (nessun item inserito) |
| `take()` | nessuna | restituisce un item (mai `null`); se il buffer era vuoto, il thread ha atteso un item; ordine FIFO | `InterruptedException` se interrotto durante l'attesa |
| `capacity()` | nessuna | valore costante fissato alla costruzione | nessuno |

Costruttori: `UnsafeBuffer(int capacity, long raceMillis)`, `MonitorBuffer(int)`, `LockBuffer(int)`,
`BlockingQueueBuffer(int)`; capacità `<= 0` → `IllegalArgumentException("capacity must be > 0")`;
`raceMillis < 0` → `IllegalArgumentException("raceMillis must be >= 0")`.

Differenze per implementazione:

| Implementazione | Thread-safe | Attesa | Interrupt |
|---|---|---|---|
| `UnsafeBuffer` | **no** (per scelta): FIFO, capacità e unicità garantite solo a thread singolo | polling `yield()` | `pause()` controlla `Thread.interrupted()`; `racePause()` usa `Thread.sleep` |
| `MonitorBuffer` | sì | `wait()` in `while` | `wait()` lancia `InterruptedException` |
| `LockBuffer` | sì | `await()` in `while` | `await()` lancia `InterruptedException` |
| `BlockingQueueBuffer` | sì | `ArrayBlockingQueue.put/take` | idem |

Nell'`UnsafeBuffer` un'interruzione durante `racePause()` lascia l'aggiornamento incompleto (documentato nel Javadoc).

```java
Buffer buffer = new MonitorBuffer(5);
buffer.put(new Item(0, 1));
Item item = buffer.take();     // Item[id=0, producerId=1]
```

## 5. `event.SimulationListener`

```java
public interface SimulationListener {
    SimulationListener NONE;                       // Null Object (classe anonima)
    void beforePut(String thread);
    void onPut(String thread, Item item);
    void beforeTake(String thread);
    void onTake(String thread, Item item);
}
```

- Precondizioni (per chi implementa): thread-safe e breve; non lancia eccezioni (un'eccezione fermerebbe il thread chiamante).
- Postcondizioni: `before*` è chiamato prima del buffer; `on*` dopo un'operazione riuscita; un'operazione interrotta ha solo `before*`; la pillola non genera `on*` né `beforePut`.
- Errori: nessuno dichiarato.

```java
SimulationListener counting = new SimulationListener() {
    private final AtomicInteger puts = new AtomicInteger();
    @Override public void beforePut(String thread) { }
    @Override public void onPut(String thread, Item item) { puts.incrementAndGet(); }
    @Override public void beforeTake(String thread) { }
    @Override public void onTake(String thread, Item item) { }
};
```

## 6. `actor.Producer` e `actor.Consumer`

```java
public Producer(int id, Buffer buffer, IdGenerator generator, Quota quota,
                SimulationListener listener, long delayMillis);
public int getProduced();
public boolean wasInterrupted();

public Consumer(int id, Buffer buffer, SimulationListener listener, long delayMillis);
public List<Item> getConsumed();                 // vista non modificabile
public boolean wasInterrupted();
```

| | `Producer` | `Consumer` |
|---|---|---|
| Ciclo | `tryAcquire` → `Item(next(), id)` → `beforePut` → `put` → `produced++` → `onPut` → `sleep(delay)` | `beforeTake` → `take` → se pillola `return` → `consumed.add` → `onTake` → `sleep(delay)` |
| Termina quando | quota esaurita, oppure interrotto | riceve `POISON`, oppure interrotto |
| Precondizioni dei getter | solo dopo `join()` | solo dopo `join()` |
| Postcondizioni | `getProduced()` = item accettati dal buffer; un `put` interrotto non è contato | `getConsumed()` esclude la pillola; ordine di arrivo |
| Errori | `InterruptedException` catturata in `run()`: `wasInterrupted() == true` | idem |

I costruttori non validano gli argomenti (`null`, `delayMillis < 0`): la validazione è nella `SimulationConfig`
a monte. `Thread.sleep` con ritardo negativo lancerebbe `IllegalArgumentException`, ma `if (delayMillis > 0)` lo evita.

```java
Producer producer = new Producer(1, buffer, generator, new Quota(10), SimulationListener.NONE, 0);
producer.start();
producer.join();
int produced = producer.getProduced();   // 10
```

## 7. `simulation.SimulationConfig`

```java
public SimulationConfig(int producers, int consumers, int items, int capacity,
                        long timeoutMillis, long delayMillis);
```

- Precondizioni: tutti i valori negli intervalli delle costanti `MIN_*`/`MAX_*` (tabella in [ARCHITECTURE](ARCHITECTURE.md) §2.6 bis).
- Postcondizioni: oggetto immutabile con getter (`getProducers()`, `getConsumers()`, `getItems()`, `getCapacity()`, `getTimeoutMillis()`, `getDelayMillis()`).
- Errori: `IllegalArgumentException("<name> must be between <min> and <max>: <value>")` con `name` in {`producers`, `consumers`, `items`, `capacity`, `timeoutMillis`, `delayMillis`}.

```java
SimulationConfig config = new SimulationConfig(3, 2, 40, 5, 30_000, 200);
```

## 8. `simulation.Simulation`

```java
public Simulation(Buffer buffer, SimulationConfig config, SimulationListener listener);
public SimulationResult run() throws InterruptedException;
```

- Precondizioni: `buffer` vuoto e mai usato; un'istanza = un solo `run()`; la capacità di `config` non viene applicata al buffer.
- Postcondizioni: ritorna entro `timeout + GRACE_MILLIS` (salvo ritardi dello scheduler); se tutti i thread sono terminati prima della scadenza `isTimedOut() == false` e `getNotTerminated()` è vuoto; altrimenti `isTimedOut() == true`; thread daemon; le pillole non sono notificate al listener.
- Errori: `InterruptedException` se il thread chiamante è interrotto durante un'attesa (i worker restano in esecuzione: R-5).

```java
Buffer buffer = new LockBuffer(config.getCapacity());
SimulationResult result = new Simulation(buffer, config, SimulationListener.NONE).run();
```

## 9. `simulation.SimulationResult` e `WorkerReport`

```java
public SimulationResult(List<WorkerReport> producers, List<WorkerReport> consumers,
        List<Item> consumedItems, boolean timedOut, List<String> notTerminated, long elapsedMillis);
public int getProduced();   // somma dei conteggi dei producer
public int getConsumed();   // consumedItems.size()
public int getDistinct();   // id distinti tra i consumati
public int getLost();       // prodotti - distinti
public int getDuplicated(); // consumati - distinti
public Map<Item, Integer> getDuplicates();   // item con occorrenze > 1, non modificabile
```

- Precondizioni: `consumedItems` non contiene la pillola.
- Postcondizioni: immutabile (copie difensive); `getLost()` e `getDuplicated()` possono essere negativi solo se gli input sono incoerenti (non succede in un run reale).
- Errori: nessuno dichiarato (argomenti `null` → `NullPointerException` da `List.copyOf`).

`WorkerReport(String name, int count, boolean interrupted)`: value object con `getName()`, `getCount()`, `wasInterrupted()`;
`toString()` aggiunge " (interrupted)".

## 10. `scenario`

```java
public interface Scenario {
    String getName();
    String getDescription();
    SimulationResult run(SimulationConfig config, SimulationListener listener) throws InterruptedException;
}

public abstract class BufferScenario implements Scenario {
    protected BufferScenario(String name, String description);
    protected abstract Buffer createBuffer(SimulationConfig config);
    // getName, getDescription, run: final
}

public final class Scenarios { public static List<Scenario> all(); }
```

- Precondizioni di `run`: `config != null`; `listener != null` (usare `SimulationListener.NONE`).
- Postcondizioni: ogni chiamata a `run` usa un buffer nuovo; lo stesso oggetto scenario è riutilizzabile; `Scenarios.all()` restituisce 5 scenari in ordine fisso.
- Contratto di `createBuffer`: restituisce un'istanza **nuova** e vuota con capacità `config.getCapacity()`.
- Errori: `InterruptedException` propagata; `Scenarios.all().add(...)` → `UnsupportedOperationException` (lista non modificabile).

Esempio di nuovo scenario (E-2):

```java
public final class SemaphoreScenario extends BufferScenario {
    public SemaphoreScenario() {
        super("Semaphore", "Counting semaphores: every item is consumed exactly once.");
    }
    @Override
    protected Buffer createBuffer(SimulationConfig config) {
        return new SemaphoreBuffer(config.getCapacity());
    }
}
```

`SequentialScenario`: costanti `PRODUCER_NAME = "producer-1"`, `CONSUMER_NAME = "consumer-1"`; ignora `getProducers()`, `getConsumers()` e il timeout.
Il thread chiamante può essere interrotto durante `Thread.sleep(delay)`: `InterruptedException` propagata.

## 11. `tui`

| Classe | Firma chiave | Precondizioni | Postcondizioni / errori |
|---|---|---|---|
| `Tui` | `Tui(Reader in, Writer out, List<Scenario> scenarios)`; `void run() throws IOException, InterruptedException` | `scenarios` non vuota | cicla fino a `q` o fine input; ripristina `Theme.RESET`; `IOException` dalla lettura; il thread main è l'unico che scrive |
| `Parameters` | `label/value/min/max(int number)`, `set(int number, long value)`, `isLive()`, `toConfig()` | `number` da 1 a 6 | `IllegalArgumentException` per numero o valore fuori limite (il valore precedente resta); `toConfig()` crea una `SimulationConfig` valida |
| `Box` | `header()`, `box(String title, List<String> body)`, `footer(String)`, `visibleLength`, `truncate`, `wrap`, `seconds`, `repeat` | righe del corpo larghe al massimo `INNER` (56) colonne visibili | ogni riga prodotta è larga esattamente 60 colonne; `IllegalArgumentException` se una riga è troppo larga; `truncate` con `width < 3` non è gestito |
| `LiveView` | `LiveView(int capacity, int items, long delayMillis, long startNanos)`; `drain()`; `lines(long nowNanos, boolean finished)` | `drain` e `lines` dal solo thread di disegno; `capacity >= 1` (divisione) | righe di 3 box (Buffer, Threads, Log) larghe 60; barra limitata a [0, capacità] |
| `ResultView` | `lines(String scenarioName, SimulationResult)`; `verdict(SimulationResult)` | risultato valido | box dei duplicati solo se presenti; verdetto verde solo se persi = duplicati = 0, nessun timeout e nessun thread vivo |
| `Theme` | `accent`, `accentBold`, `text`, `secondary`, `border`, `error`, `success`; costanti `WIDTH`, `DEFAULT_*`, `RESET`, `CLEAR` | — | ogni segmento colorato termina con `RESET` |

Esempio di sessione di test (nessun terminale):

```java
StringWriter out = new StringWriter();
new Tui(new StringReader("1\nb\nq\n"), out, Scenarios.all()).run();
```

La sessione apre lo scenario 1, torna al menu (`b`) ed esce (`q`) senza eseguire alcun run.
`Tui` è package-private: l'esempio vale nei test del package `producerconsumer.tui`.
