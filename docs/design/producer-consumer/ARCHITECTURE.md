# ARCHITECTURE — producer-consumer

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato: reviewed
> Documenti: [PRD](PRD.md) · [BEHAVIOR](BEHAVIOR.md) · [DESIGN-PATTERNS](DESIGN-PATTERNS.md) · [DESIGN-IN-THE-SMALL](DESIGN-IN-THE-SMALL.md) · [TEST-STRATEGY](TEST-STRATEGY.md) · [ANALYSIS](ANALYSIS.md) · [ADR](ADR/ADR-0001-stack.md) · [DESIGN-STATUS](DESIGN-STATUS.md)

Documento ricostruito dal codice in `src/main/java/producerconsumer` (reverse-engineering). Le
decisioni D1–D17 di `docs/design.md` sono citate tramite gli ADR. Il comportamento dinamico
(sequence, state, activity) è in [BEHAVIOR](BEHAVIOR.md).

## 1. Vista C4

### 1.1 Context

```mermaid
flowchart LR
    student["Studente<br/>(legge, esegue, modifica il codice)"]
    term["Terminale<br/>UTF-8, truecolor"]
    app["producer-consumer<br/>applicazione Java da terminale"]
    maven["Maven<br/>exec:java, test"]
    jdk["JDK 21<br/>java.util.concurrent"]

    student -->|"digita comandi, legge schermate"| term
    term <-->|"System.in / System.out"| app
    student -->|"mvn compile exec:java / mvn test"| maven
    maven -->|"avvia producerconsumer.tui.App"| app
    app -->|"usa Thread, locks, ArrayBlockingQueue"| jdk
```

### 1.2 Container

Un solo container: un processo JVM, un solo modulo Maven, nessuna dipendenza runtime
([ADR-0001](ADR/ADR-0001-stack.md)). La vista utile è quella dei thread a runtime:

```mermaid
flowchart TB
    subgraph jvm["Processo JVM (producerconsumer.tui.App)"]
        main["Thread main<br/>unico che legge System.in e scrive System.out<br/>Tui, Box, LiveView, ResultView"]
        runner["Thread runner<br/>non daemon<br/>Tui.Runner → Scenario.run → Simulation.run"]
        subgraph workers["Thread della simulazione (daemon)"]
            prod["producer-1 … producer-N"]
            cons["consumer-1 … consumer-M"]
            term["terminator"]
        end
        buf[("Buffer condiviso<br/>una delle 4 implementazioni")]
        q[("ConcurrentLinkedQueue<br/>eventi di LiveView")]
    end
    main -->|"start, join(100)"| runner
    runner -->|"start, join con scadenza"| workers
    prod -->|"put"| buf
    cons -->|"take"| buf
    term -->|"put POISON"| buf
    prod -->|"eventi"| q
    cons -->|"eventi"| q
    main -->|"drain"| q
```

Il thread del run sequenziale è lo stesso `runner`: `SequentialScenario` non avvia altri thread.

### 1.3 Component: package e dipendenze

```mermaid
flowchart TD
    tui["tui<br/>I/O, schermate"]
    scenario["scenario<br/>strategie di sincronizzazione"]
    simulation["simulation<br/>run concorrente, config, risultato"]
    actor["actor<br/>Producer, Consumer"]
    buffer["buffer<br/>Buffer + 4 implementazioni"]
    counter["counter<br/>IdGenerator, Quota"]
    event["event<br/>SimulationListener"]
    model["model<br/>Item, POISON"]

    tui --> scenario
    tui --> simulation
    tui --> event
    tui --> model
    scenario --> simulation
    scenario --> buffer
    scenario --> counter
    scenario --> event
    scenario --> model
    simulation --> actor
    simulation --> buffer
    simulation --> counter
    simulation --> event
    simulation --> model
    actor --> buffer
    actor --> counter
    actor --> event
    actor --> model
    buffer --> model
    event --> model
```

Le frecce sono gli `import` reali (verificati sul sorgente); il grafo è aciclico. Ordine topologico:
`model` → (`buffer`, `counter`, `event`) → `actor` → `simulation` → `scenario` → `tui`.
Il diagramma in `docs/GUIDE.md` §4 omette gli archi `scenario → buffer/counter/event/model`,
usati da `SequentialScenario`: divergenza minore (vedi [ANALYSIS](ANALYSIS.md), §5).

`event` è separato da `simulation` perché `actor` (che notifica) e `simulation` (che lo passa agli attori)
non devono formare un ciclo ([ADR-0013](ADR/ADR-0013-osservazione-eventi.md)).

## 2. Class diagram per package

Notazione: `+` pubblico, `-` privato, `~` package-private, `$` statico. Sono mostrati solo i membri rilevanti.

### 2.1 `model`

```mermaid
classDiagram
    class Item {
        <<final>>
        +Item POISON$
        -int id
        -int producerId
        +Item(int id, int producerId)
        +getId() int
        +getProducerId() int
        +isPoison() boolean
        +equals(Object o) boolean
        +hashCode() int
        +toString() String
    }
```

### 2.2 `counter`

```mermaid
classDiagram
    class IdGenerator {
        <<final>>
        -AtomicInteger next
        +next() int
    }
    class Quota {
        <<final>>
        -AtomicInteger remaining
        +Quota(int permits)
        +tryAcquire() boolean
    }
```

### 2.3 `buffer`

```mermaid
classDiagram
    class Buffer {
        <<interface>>
        +put(Item item) void
        +take() Item
        +capacity() int
    }
    class UnsafeBuffer {
        -Item items
        -int head
        -int tail
        -int count
        -long raceMillis
        +UnsafeBuffer(int capacity, long raceMillis)
        -racePause() void
        -pause() void
    }
    class MonitorBuffer {
        -LinkedList~Item~ queue
        -int capacity
        +MonitorBuffer(int capacity)
    }
    class LockBuffer {
        -LinkedList~Item~ queue
        -int capacity
        -Lock lock
        -Condition notFull
        -Condition notEmpty
        +LockBuffer(int capacity)
    }
    class BlockingQueueBuffer {
        -BlockingQueue~Item~ queue
        -int capacity
        +BlockingQueueBuffer(int capacity)
    }
    class Item
    class ArrayBlockingQueue {
        <<JDK>>
    }
    Buffer <|.. UnsafeBuffer
    Buffer <|.. MonitorBuffer
    Buffer <|.. LockBuffer
    Buffer <|.. BlockingQueueBuffer
    BlockingQueueBuffer --> ArrayBlockingQueue : delega put e take
    Buffer ..> Item
```

### 2.4 `event`

```mermaid
classDiagram
    class SimulationListener {
        <<interface>>
        +SimulationListener NONE$
        +beforePut(String thread) void
        +onPut(String thread, Item item) void
        +beforeTake(String thread) void
        +onTake(String thread, Item item) void
    }
    class Item
    SimulationListener ..> Item
```

### 2.5 `actor`

```mermaid
classDiagram
    class Thread {
        <<JDK>>
    }
    class Producer {
        <<final>>
        -int id
        -int produced
        -boolean interrupted
        -long delayMillis
        +Producer(int id, Buffer buffer, IdGenerator generator, Quota quota, SimulationListener listener, long delayMillis)
        +run() void
        +getProduced() int
        +wasInterrupted() boolean
    }
    class Consumer {
        <<final>>
        -List~Item~ consumed
        -boolean interrupted
        -long delayMillis
        +Consumer(int id, Buffer buffer, SimulationListener listener, long delayMillis)
        +run() void
        +getConsumed() List~Item~
        +wasInterrupted() boolean
    }
    class Buffer {
        <<interface>>
    }
    class IdGenerator
    class Quota
    class SimulationListener {
        <<interface>>
    }
    Thread <|-- Producer
    Thread <|-- Consumer
    Producer --> Buffer
    Producer --> IdGenerator
    Producer --> Quota
    Producer --> SimulationListener
    Consumer --> Buffer
    Consumer --> SimulationListener
```

### 2.6 `simulation`

```mermaid
classDiagram
    class Simulation {
        <<final>>
        ~long GRACE_MILLIS$
        -Buffer buffer
        -SimulationConfig config
        -SimulationListener listener
        +Simulation(Buffer buffer, SimulationConfig config, SimulationListener listener)
        +run() SimulationResult
        -putPoisonPills() void
        -joinAll(List threads, long deadlineNanos) boolean$
        -aliveNames(List threads) List$
        -buildResult() SimulationResult$
    }
    class SimulationConfig {
        <<final>>
        +int MAX_PRODUCERS$
        +long MAX_TIMEOUT_MILLIS$
        -int producers
        -int consumers
        -int items
        -int capacity
        -long timeoutMillis
        -long delayMillis
        +SimulationConfig(int producers, int consumers, int items, int capacity, long timeoutMillis, long delayMillis)
    }
    class SimulationResult {
        <<final>>
        -int consumed
        -int distinct
        -boolean timedOut
        -long elapsedMillis
        +getProduced() int
        +getConsumed() int
        +getDistinct() int
        +getLost() int
        +getDuplicated() int
        +getDuplicates() Map
        +getProducers() List
        +getConsumers() List
        +isTimedOut() boolean
        +getNotTerminated() List
        +getElapsedMillis() long
    }
    class WorkerReport {
        <<final>>
        -String name
        -int count
        -boolean interrupted
        +getName() String
        +getCount() int
        +wasInterrupted() boolean
    }
    class Producer
    class Consumer
    class Buffer {
        <<interface>>
    }
    class SimulationListener {
        <<interface>>
    }
    Simulation --> SimulationConfig
    Simulation --> Buffer
    Simulation --> SimulationListener
    Simulation ..> Producer : crea
    Simulation ..> Consumer : crea
    Simulation ..> SimulationResult : produce
    SimulationResult o-- WorkerReport
```

### 2.6 bis Costanti di `SimulationConfig`

| Costante | Min | Max |
|---|---|---|
| producers | `MIN_PRODUCERS` = 1 | `MAX_PRODUCERS` = 50 |
| consumers | `MIN_CONSUMERS` = 1 | `MAX_CONSUMERS` = 50 |
| items | `MIN_ITEMS` = 0 | `MAX_ITEMS` = 1.000.000 |
| capacity | `MIN_CAPACITY` = 1 | `MAX_CAPACITY` = 1.000 |
| timeoutMillis | `MIN_TIMEOUT_MILLIS` = 100 | `MAX_TIMEOUT_MILLIS` = 60.000 |
| delayMillis | `MIN_DELAY_MILLIS` = 0 | `MAX_DELAY_MILLIS` = 2.000 |

### 2.7 `scenario`

```mermaid
classDiagram
    class Scenario {
        <<interface>>
        +getName() String
        +getDescription() String
        +run(SimulationConfig config, SimulationListener listener) SimulationResult
    }
    class BufferScenario {
        <<abstract>>
        -String name
        -String description
        #BufferScenario(String name, String description)
        #createBuffer(SimulationConfig config) Buffer*
        +getName() String
        +getDescription() String
        +run(SimulationConfig config, SimulationListener listener) SimulationResult
    }
    class SequentialScenario {
        <<final>>
    }
    class UnsafeScenario {
        <<final>>
    }
    class MonitorScenario {
        <<final>>
    }
    class LockScenario {
        <<final>>
    }
    class BlockingQueueScenario {
        <<final>>
    }
    class Scenarios {
        <<final>>
        +all() List~Scenario~$
    }
    class Simulation
    class UnsafeBuffer
    Scenario <|.. BufferScenario
    Scenario <|.. SequentialScenario
    BufferScenario <|-- UnsafeScenario
    BufferScenario <|-- MonitorScenario
    BufferScenario <|-- LockScenario
    BufferScenario <|-- BlockingQueueScenario
    BufferScenario ..> Simulation : delega run
    SequentialScenario ..> UnsafeBuffer : usa (raceMillis 0)
    Scenarios ..> Scenario : elenca
```

`run`, `getName` e `getDescription` di `BufferScenario` sono `final`. Le sottoclassi creano rispettivamente
`UnsafeBuffer(capacity, delayMillis)`, `MonitorBuffer`, `LockBuffer`, `BlockingQueueBuffer`.

### 2.8 `tui`

```mermaid
classDiagram
    class App {
        <<final>>
        +main(String args) void$
    }
    class Tui {
        <<final>>
        ~long REFRESH_MILLIS$
        -BufferedReader in
        -PrintWriter out
        -List~Scenario~ scenarios
        -Parameters parameters
        ~Tui(Reader in, Writer out, List scenarios)
        ~run() void
        -menu() void
        -detail(Scenario s) void
        -parameters() void
        -editValue(int number) String
        -execute(Scenario s) String
        -result(Scenario s, SimulationResult r) void
        -draw(List body, String message, String shortcuts, boolean prompt) void
        -readCommand() String
    }
    class Runner {
        <<final>>
        -SimulationResult result
        -Exception failure
        +run() void
        ~getResult() SimulationResult
        ~getFailure() Exception
    }
    class Theme {
        <<final>>
        ~int WIDTH$
        ~accent(String t) String$
        ~text(String t) String$
        ~secondary(String t) String$
        ~border(String t) String$
        ~error(String t) String$
        ~success(String t) String$
    }
    class Box {
        <<final>>
        ~int INNER$
        ~header() List$
        ~box(String title, List body) List$
        ~footer(String shortcuts) List$
        ~visibleLength(String s) int$
        ~truncate(String s, int w) String$
        ~wrap(String s, int w) List$
    }
    class Parameters {
        <<final>>
        ~int COUNT$
        ~label(int n) String
        ~value(int n) long
        ~set(int n, long v) void
        ~isLive() boolean
        ~toConfig() SimulationConfig
    }
    class LiveView {
        <<final>>
        ~int WAIT_FACTOR$
        -ConcurrentLinkedQueue~Event~ queue
        -Map threads
        -Deque log
        ~drain() void
        ~lines(long nowNanos, boolean finished) List
    }
    class ResultView {
        <<final>>
        ~lines(String name, SimulationResult r) List$
        ~verdict(SimulationResult r) String$
    }
    class SimulationListener {
        <<interface>>
    }
    class Scenario {
        <<interface>>
    }
    App ..> Tui : crea
    Tui *-- Runner
    Tui --> Parameters
    Tui --> LiveView
    Tui ..> ResultView
    Tui ..> Box
    Tui ..> Theme
    Box ..> Theme
    LiveView ..> Box
    LiveView ..> Theme
    ResultView ..> Box
    ResultView ..> Theme
    LiveView ..|> SimulationListener
    Tui --> Scenario
    Runner --> Scenario
    Parameters ..> Theme : default
```

Tutte le classi di `tui` tranne `App` sono package-private.

### 2.9 Class diagram complessivo

```mermaid
classDiagram
    direction LR
    class Item
    class Buffer {
        <<interface>>
    }
    class UnsafeBuffer
    class MonitorBuffer
    class LockBuffer
    class BlockingQueueBuffer
    class IdGenerator
    class Quota
    class SimulationListener {
        <<interface>>
    }
    class Producer
    class Consumer
    class Simulation
    class SimulationConfig
    class SimulationResult
    class WorkerReport
    class Scenario {
        <<interface>>
    }
    class BufferScenario {
        <<abstract>>
    }
    class SequentialScenario
    class Scenarios
    class Tui
    class LiveView
    class ResultView
    class Parameters

    Buffer <|.. UnsafeBuffer
    Buffer <|.. MonitorBuffer
    Buffer <|.. LockBuffer
    Buffer <|.. BlockingQueueBuffer
    Buffer ..> Item
    Producer --> Buffer
    Producer --> IdGenerator
    Producer --> Quota
    Producer --> SimulationListener
    Consumer --> Buffer
    Consumer --> SimulationListener
    Simulation --> Buffer
    Simulation --> SimulationConfig
    Simulation --> SimulationListener
    Simulation ..> Producer : crea
    Simulation ..> Consumer : crea
    Simulation ..> SimulationResult : produce
    SimulationResult o-- WorkerReport
    Scenario <|.. BufferScenario
    Scenario <|.. SequentialScenario
    BufferScenario ..> Simulation : delega
    BufferScenario ..> Buffer : createBuffer
    SequentialScenario ..> UnsafeBuffer
    SequentialScenario ..> IdGenerator
    SequentialScenario ..> Quota
    Scenarios ..> Scenario
    Tui --> Scenario
    Tui --> Parameters
    Tui --> LiveView
    Tui ..> ResultView
    LiveView ..|> SimulationListener
    Parameters ..> SimulationConfig
```

Le quattro sottoclassi di `BufferScenario` sono omesse per leggibilità (vedi §2.7).

## 3. Moduli: responsabilità, interfaccia pubblica, dipendenze

Ogni responsabilità è una sola frase. Le dipendenze elencano solo classi del progetto.

### 3.1 `model`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `Item` | Rappresentare un elemento scambiato, immutabile | `Item(int,int)`, `getId`, `getProducerId`, `isPoison`, `POISON`, `equals`, `hashCode`, `toString` | nessuna |

### 3.2 `counter`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `IdGenerator` | Fornire id unici in modo atomico | `int next()` | nessuna |
| `Quota` | Concedere un numero finito di permessi in modo atomico | `Quota(int)`, `boolean tryAcquire()` | nessuna |

### 3.3 `buffer`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `Buffer` | Definire il contratto di un buffer limitato | `put`, `take`, `capacity` | `Item` |
| `UnsafeBuffer` | Offrire un buffer senza mutua esclusione, con race riproducibile | `Buffer` + `UnsafeBuffer(int,long)` | `Item` |
| `MonitorBuffer` | Offrire un buffer con monitor intrinseco | `Buffer` + `MonitorBuffer(int)` | `Item` |
| `LockBuffer` | Offrire un buffer con `ReentrantLock` | `Buffer` + `LockBuffer(int)` | `Item` |
| `BlockingQueueBuffer` | Adattare `ArrayBlockingQueue` al contratto `Buffer` | `Buffer` + `BlockingQueueBuffer(int)` | `Item` |

### 3.4 `event`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `SimulationListener` | Notificare le operazioni sul buffer | `beforePut`, `onPut`, `beforeTake`, `onTake`, `NONE` | `Item` |

### 3.5 `actor`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `Producer` | Produrre item finché la quota lo consente | `Producer(...)`, `run`, `getProduced`, `wasInterrupted` | `Buffer`, `IdGenerator`, `Quota`, `SimulationListener`, `Item` |
| `Consumer` | Consumare item fino alla pillola | `Consumer(...)`, `run`, `getConsumed`, `wasInterrupted` | `Buffer`, `SimulationListener`, `Item` |

### 3.6 `simulation`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `Simulation` | Coordinare un run concorrente entro la scadenza | `Simulation(Buffer,SimulationConfig,SimulationListener)`, `SimulationResult run()` | `Producer`, `Consumer`, `Buffer`, `IdGenerator`, `Quota`, `SimulationConfig`, `SimulationResult`, `WorkerReport`, `SimulationListener`, `Item` |
| `SimulationConfig` | Validare i parametri di un run | costruttore a 6 argomenti, getter, costanti `MIN_*`/`MAX_*` | nessuna |
| `SimulationResult` | Calcolare le metriche di un run | getter (`getProduced`, `getLost`, `getDuplicated`, …) | `WorkerReport`, `Item` |
| `WorkerReport` | Riportare l'esito di un thread | `getName`, `getCount`, `wasInterrupted` | nessuna |

### 3.7 `scenario`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `Scenario` | Definire il contratto di uno scenario eseguibile | `getName`, `getDescription`, `run` | `SimulationConfig`, `SimulationResult`, `SimulationListener` |
| `BufferScenario` | Eseguire una `Simulation` su un buffer scelto dalla sottoclasse | `Scenario` (con `run` `final`), `createBuffer` protetto | `Buffer`, `Simulation`, `SimulationConfig`, `SimulationResult`, `SimulationListener` |
| `UnsafeScenario` | Scegliere `UnsafeBuffer` | `UnsafeScenario()` | `BufferScenario`, `UnsafeBuffer` |
| `MonitorScenario` | Scegliere `MonitorBuffer` | `MonitorScenario()` | `BufferScenario`, `MonitorBuffer` |
| `LockScenario` | Scegliere `LockBuffer` | `LockScenario()` | `BufferScenario`, `LockBuffer` |
| `BlockingQueueScenario` | Scegliere `BlockingQueueBuffer` | `BlockingQueueScenario()` | `BufferScenario`, `BlockingQueueBuffer` |
| `SequentialScenario` | Eseguire produzione e consumo alternati su un solo thread | `Scenario` | `UnsafeBuffer`, `IdGenerator`, `Quota`, `Item`, `SimulationConfig`, `SimulationResult`, `WorkerReport`, `SimulationListener` |
| `Scenarios` | Elencare gli scenari nell'ordine del menu | `static List<Scenario> all()` | tutte le classi scenario |

### 3.8 `tui`

| Classe | Responsabilità singola | Interfaccia pubblica | Dipendenze |
|---|---|---|---|
| `App` | Collegare la TUI al terminale | `main` | `Tui`, `Scenarios` |
| `Tui` | Gestire la navigazione tra le schermate | package-private: `Tui(Reader,Writer,List<Scenario>)`, `run` | `Scenario`, `Parameters`, `LiveView`, `ResultView`, `Box`, `Theme`, `SimulationConfig`, `SimulationResult`, `SimulationListener` |
| `Tui.Runner` | Eseguire uno scenario fuori dal thread della TUI | `run`, `getResult`, `getFailure` (privata) | `Scenario`, `SimulationConfig`, `SimulationListener` |
| `Theme` | Definire colori, larghezza e testi fissi | costanti, `accent`, `text`, `secondary`, `border`, `error`, `success` | nessuna |
| `Box` | Comporre righe con bordi e larghezza fissa | `header`, `box`, `footer`, `visibleLength`, `truncate`, `wrap`, `seconds`, `repeat` | `Theme` |
| `Parameters` | Conservare i valori correnti dei parametri | `label`, `value`, `min`, `max`, `set`, `isLive`, `toConfig` | `SimulationConfig`, `Theme` |
| `LiveView` | Derivare la vista dal vivo dagli eventi | `SimulationListener`, `drain`, `lines` | `SimulationListener`, `Item`, `Box`, `Theme` |
| `ResultView` | Comporre le righe del risultato | `lines`, `verdict` | `SimulationResult`, `WorkerReport`, `Item`, `Box`, `Theme` |

Nota sulla modularità: `Simulation` e `Tui` hanno molte dipendenze, ma tutte sono rivolte verso
package più bassi nell'ordine topologico; nessuna è circolare.

## 4. Data flow

1. `App.main` crea `Tui` con `Scenarios.all()` e i flussi `System.in`/`System.out` in UTF-8.
2. L'utente sceglie uno scenario e i parametri; `Parameters.toConfig()` crea una `SimulationConfig` validata.
3. `Tui.execute` crea un `LiveView` e un `Runner`, lo avvia e ridisegna ogni 100 ms (`join(100)`).
4. `Scenario.run` crea un buffer nuovo e una `Simulation` ([BEHAVIOR](BEHAVIOR.md), §1).
5. `Simulation` crea `IdGenerator` e `Quota`; ogni `Producer` ottiene un permesso, un id, inserisce l'`Item`;
   ogni `Consumer` preleva e registra l'`Item` nella lista locale. Entrambi emettono eventi verso il listener.
6. Terminati i producer, il `terminator` inserisce una pillola per consumer; i consumer escono.
7. `Simulation` costruisce `SimulationResult` (distinti, persi, duplicati) dai `WorkerReport` e dagli item consumati.
8. `Tui` legge il risultato dopo il `join` e mostra `ResultView.lines` e `ResultView.verdict`.

Direzione dei dati: eventi `thread simulazione → LiveView.queue → thread main`; esito `Runner.result → thread main`
(visibilità garantita da `join`/`isAlive`); item `Producer → Buffer → Consumer`.

## 5. Confini e contratti tra moduli

| Confine | Contratto | Chi lo verifica |
|---|---|---|
| `Buffer` | `put` attende se pieno, `take` attende se vuoto e non restituisce `null`; `put(null)` → `NullPointerException`; interrompibile con `InterruptedException`; capacità `> 0` | `BufferTest` (astratto, 5 sottoclassi) |
| `SimulationListener` | chiamato dai thread della simulazione, anche in contemporanea: thread-safe e breve; la pillola non genera `on*` | `ProducerTest`, `ConsumerTest`, `SimulationTest`, `LiveViewTest` |
| `Scenario.run` | un buffer nuovo a ogni chiamata; ritorna entro `timeout + 1 s` (non il sequenziale) | `BufferScenarioTest`, `SequentialScenarioTest` |
| `Simulation.run` | un solo run per istanza; non attende mai senza limite; thread daemon | `SimulationTest` |
| Letture dopo `join` | `getProduced`, `getConsumed`, `wasInterrupted` letti solo a thread terminato | `ProducerTest`, `ConsumerTest`, Javadoc |
| `core` senza I/O | nessun `System.out` fuori da `tui` | revisione del sorgente |
| `Box` | ogni riga prodotta ha esattamente 60 colonne visibili; righe più larghe → `IllegalArgumentException` | `BoxTest`, `TuiTest` |

## 6. Punti di estensione espliciti

Tutti gli interventi rispettano lo stile di [ADR-0017](ADR/ADR-0017-stile-codice-classico.md) (niente lambda, stream, ternario).

| ID | Estensione | Cosa si aggiunge | Cosa si modifica | Moduli esistenti toccati |
|---|---|---|---|---|
| E-1 | Nuovo buffer (es. `SemaphoreBuffer`) | classe in `buffer` che implementa `Buffer`; test che estende `BufferTest` (o `SynchronizedBufferTest` se corretto) | nulla | nessuno |
| E-2 | Nuovo scenario su buffer | sottoclasse di `BufferScenario` con `createBuffer` | una riga in `Scenarios.all()`; `ScenariosTest` (ordine e numero) | `Scenarios` (registro: modifica documentata, per mantenere ordine e catalogo in un solo punto) |
| E-3 | Nuovo osservatore (log su file, metriche) | classe che implementa `SimulationListener` | nulla: passata a `Scenario.run` | nessuno |
| E-4 | Nuovo tema (256 colori) | nuove costanti | solo `Theme` | solo `Theme` |
| E-5 | Nuova metrica nel risultato | campo e getter in `SimulationResult`; riga in `ResultView` | `SimulationResultTest`, `ResultViewTest` | `SimulationResult`, `ResultView` |
| E-6 | Nuovo parametro | costante in `SimulationConfig`; voce in `Parameters` (`LABELS`, `MIN`, `MAX`, `values`, `COUNT`, `toConfig`) e uso nel nucleo | `Parameters.isLive` usa l'indice fisso `values[5]` (ritardo): va ricontrollato (DT-9) | `SimulationConfig`, `Parameters`, `Simulation` |

Il TUI non richiede modifiche per E-1, E-2 ed E-3: elenca gli scenari da `Scenarios.all()`.

### 6.1 Esempio E-1: `SemaphoreBuffer`

Schema di progetto (non implementato nel repository):

```mermaid
classDiagram
    class Buffer {
        <<interface>>
    }
    class SemaphoreBuffer {
        -Item items
        -int head
        -int tail
        -Semaphore freeSlots
        -Semaphore usedSlots
        -Semaphore mutex
        +SemaphoreBuffer(int capacity)
    }
    class SemaphoreScenario {
        <<final>>
    }
    class BufferScenario {
        <<abstract>>
    }
    Buffer <|.. SemaphoreBuffer
    BufferScenario <|-- SemaphoreScenario
    SemaphoreScenario ..> SemaphoreBuffer : createBuffer
```

- `freeSlots = new Semaphore(capacity)`, `usedSlots = new Semaphore(0)`, `mutex = new Semaphore(1)`.
- `put`: controllo `null` e `acquire()` su `freeSlots` (interrompibile) → `mutex.acquire()` → scrittura in `tail` → `mutex.release()` → `usedSlots.release()`.
- `take`: speculare con `usedSlots`/`freeSlots`.
- Differenza didattica rispetto a `LockBuffer`: l'attesa è codificata nei contatori dei semafori, non in un `while` su una condizione.
- Passi: classe `SemaphoreBuffer`; `SemaphoreBufferTest extends SynchronizedBufferTest` (una sola riga di `createBuffer`); `SemaphoreScenario`; riga in `Scenarios.all()`; aggiornare `ScenariosTest`, `README.md`, `docs/GUIDE.md`, `docs/design.md`.
- Se `acquire()` lancia `InterruptedException` il permesso non è stato ottenuto: per quel permesso non va chiamato `release()`.

## 7. Tracciabilità FR/NFR → moduli

| Requisito | Moduli |
|---|---|
| FR-1 | `Scenarios`, `Tui` |
| FR-2 | `SequentialScenario` |
| FR-3 | `UnsafeBuffer`, `UnsafeScenario` |
| FR-4 | `MonitorBuffer`, `MonitorScenario` |
| FR-5 | `LockBuffer`, `LockScenario` |
| FR-6 | `BlockingQueueBuffer`, `BlockingQueueScenario` |
| FR-7 | `Parameters`, `SimulationConfig`, `Tui` |
| FR-8 | `Simulation`, `Producer`, `Consumer`, `Quota` |
| FR-9 | `Simulation`, `Consumer`, `Item` |
| FR-10 | `Simulation`, `Tui` |
| FR-11 | `SimulationResult`, `WorkerReport` |
| FR-12 | `ResultView`, `Tui` |
| FR-13 | `LiveView`, `SimulationListener`, `Tui` |
| FR-14 | `Producer`, `Consumer`, `SequentialScenario`, `UnsafeBuffer`, `UnsafeScenario` |
| FR-15, FR-16 | `Tui` |
| FR-17 | `SimulationListener` |
| NFR-1, NFR-10, NFR-11 | tutti i moduli (§3) |
| NFR-2, NFR-6 | `BufferTest`, `SynchronizedBufferTest`, `SimulationTest`, `pom.xml` |
| NFR-3 | package `model`, `counter`, `buffer`, `event`, `actor`, `simulation`, `scenario`, `tui` (§1.3) |
| NFR-4 | `Buffer`, `BufferScenario`, `Scenarios` (§6) |
| NFR-5 | `simulation`, `scenario`, `actor`, `buffer` (nessun I/O); `tui` (unico I/O) |
| NFR-7 | `pom.xml` |
| NFR-8, NFR-12 | `Theme`, `Box`, `Tui`, `App`, `ResultView`, `LiveView` |
| NFR-9 | `Simulation` |
