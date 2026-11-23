# DESIGN-PATTERNS — producer-consumer

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato: reviewed
> Documenti: [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [BEHAVIOR](BEHAVIOR.md) · [DESIGN-IN-THE-SMALL](DESIGN-IN-THE-SMALL.md) · [TEST-STRATEGY](TEST-STRATEGY.md) · [ANALYSIS](ANALYSIS.md) · [ADR](ADR/ADR-0001-stack.md) · [DESIGN-STATUS](DESIGN-STATUS.md)

I nomi dei pattern sono assegnati in questa documentazione: `docs/design.md` motiva le scelte
ma non le etichetta con i nomi del catalogo GoF. Le alternative scartate sono quelle registrate in
design.md (indicate con la D); dove manca una motivazione esplicita è segnalato.
Tutte le implementazioni rispettano lo stile di [ADR-0017](ADR/ADR-0017-stile-codice-classico.md):
dove il pattern userebbe una lambda si usa una classe anonima.

## 1. Tabella dei pattern

| Modulo | Pattern | Perché | Alternative scartate | Costo |
|---|---|---|---|---|
| `buffer.Buffer` e le quattro implementazioni | **Strategy**: la politica di sincronizzazione è intercambiabile dietro `put`/`take`/`capacity`; i contesti (`Simulation`, `Producer`, `Consumer`) non la conoscono ([ADR-0006](ADR/ADR-0006-interfaccia-buffer.md)) | Cinque scenari confrontabili a parità di logica; aggiungere una strategia non tocca i contesti | `if`/`switch` sul tipo nel producer e nel consumer (non documentata in design.md, ovvia alternativa); interfaccia con `size()`/`isEmpty()` (D5: check-then-act) | Interfaccia minima: nessuna operazione di ispezione, i test devono usare thread per osservare le attese |
| `buffer.BlockingQueueBuffer` | **Adapter**: adatta `BlockingQueue` al contratto `Buffer` ([ADR-0009](ADR/ADR-0009-blocking-queue-buffer.md)) | Riusa la soluzione della libreria con comportamento uniforme (null, capacità, interrupt) | `LinkedBlockingQueue` (D9: illimitata di default) | Un livello di indirezione; `capacity` duplicata nel campo perché `BlockingQueue` non la espone |
| `buffer.MonitorBuffer` | **Monitor Object** + **Guarded Suspension** (`while` + `wait`/`notifyAll`) ([ADR-0008](ADR/ADR-0008-monitor-e-lock-buffer.md)) | Mostra il meccanismo base del corso; il `while` copre risvegli spuri e thread già arrivati (D6) | `notify()` (D8: può svegliare la categoria sbagliata); `if` al posto di `while` (D6) | Una sola coda di attesa: `notifyAll()` risveglia anche thread inutili |
| `buffer.LockBuffer` | **Guarded Suspension** con lock esplicito e due `Condition` | Code di attesa separate: `signal()` basta (D8) | Un'unica `Condition` (equivalente al monitor); `lockInterruptibly()` (D8: non necessario) | Disciplina manuale di `try`/`finally` per `unlock()` |
| `buffer.UnsafeBuffer` | **Anti-pattern didattico**: read-modify-write non atomico esposto di proposito, con `raceMillis` che allarga la finestra ([ADR-0007](ADR/ADR-0007-unsafe-buffer.md)) | Rende osservabili persi e duplicati senza eccezioni | `LinkedList` (D7: nodi corrompibili); `wait()` (D7); pausa dentro il buffer sicuro (D14) | Il difetto non è testabile in modo deterministico; polling con `yield()` consuma CPU |
| `scenario.BufferScenario` | **Template Method**: `run` è `final` e fissa i passi (crea buffer, crea `Simulation`, esegue); **Factory Method**: `createBuffer` è il passo variabile, restituisce un'istanza nuova a ogni run ([ADR-0015](ADR/ADR-0015-scenari.md)) | L'avvio di `Simulation` è scritto una volta; `final` impedisce a una sottoclasse di riusare un buffer (D16) | Sottoclassi anonime (D16: meno facili da trovare e testare); un `switch` nel catalogo (non documentata) | Una classe per scenario (quattro sottoclassi quasi vuote); ereditarietà invece di composizione |
| `scenario.Scenario`, `scenario.SequentialScenario` | **Strategy** a livello di scenario: il menu esegue `run(config, listener)` senza conoscere il tipo; `SequentialScenario` è una strategia che non usa `Simulation` | Il menu è uniforme e non richiede modifiche per un nuovo scenario (D16) | Passare per `Simulation` con 1 producer e 1 consumer (D16: tre thread comunque) | Il sequenziale duplica un frammento di logica (fasi di riempimento e svuotamento) che non è in `Simulation` |
| `scenario.Scenarios` | **Registro statico** (static factory): `all()` restituisce la lista non modificabile nell'ordine del menu | Elenco e ordine in un solo punto, verificabili con i test (D16) | Catalogo nella TUI (D16) | Aggiungere uno scenario richiede una riga qui (modifica documentata, E-2) |
| `event.SimulationListener` | **Observer**: producer e consumer notificano ogni operazione; **Null Object**: `SimulationListener.NONE` (istanza anonima) evita `null` e controlli `if (listener != null)` ([ADR-0013](ADR/ADR-0013-osservazione-eventi.md)) | Il nucleo resta senza I/O; nessuna ramificazione nei thread (D14) | Listener che stampa; campionamento di `Thread.getState()`; eventi dai buffer (D14) | Contratto implicito: i metodi sono chiamati in contemporanea e devono essere brevi |
| `tui.LiveView` | **Observer** concreto + **hand-off tramite coda thread-safe** (`ConcurrentLinkedQueue`): i thread della simulazione producono eventi, un solo thread li consuma e deriva lo stato | Un solo thread scrive sul terminale e legge lo stato; nessun lock sulla vista (D14) | Vista cella per cella del buffer (D14: mostrerebbe una coda ideale) | Stato "in attesa" dedotto da soglia temporale (`WAIT_FACTOR`): è un'euristica |
| `model.Item.POISON` + `simulation.Simulation.putPoisonPills` + `actor.Consumer` | **Poison pill** (sentinella di fine flusso) ([ADR-0005](ADR/ADR-0005-item-e-poison-pill.md), [ADR-0011](ADR/ADR-0011-terminazione-poison-pill.md)) | Una pillola per consumer, inserita dopo la fine dei producer: nessun consumer termina con item in arrivo (D12) | `null` come sentinella (D4: rifiutato dalle code bloccanti, privo di significato) | Con `UnsafeBuffer` la pillola può andare persa → timeout; confronto per identità (R-8) |
| `model.Item`, `SimulationConfig`, `SimulationResult`, `WorkerReport` | **Immutable Value Object** (campi `final`, copie difensive con `List.copyOf`) | Condivisibili tra thread senza sincronizzazione (D4); risultato non alterabile dalla TUI | `record` (D4: forma classica scelta) | Boilerplate: `equals`, `hashCode`, getter scritti a mano |
| `counter.IdGenerator`, `counter.Quota` | **Contatore atomico lock-free** (`AtomicInteger`, `getAndIncrement`/`getAndDecrement`) con operazione composta in un solo metodo (`tryAcquire`) | Evita check-then-act; nessun monitor da scegliere (D10, D11) | `int` con `counter++`; `synchronized` sul controllo (D11) | La quota scende sotto zero dopo l'esaurimento (ogni valore `<= 0` = esaurita) |
| `actor.Producer`, `actor.Consumer` | **Thread con ciclo di vita proprio** (`extends Thread`) con **registro locale** letto dopo `join()` (happens-before) ([ADR-0010](ADR/ADR-0010-producer-quota.md), [ADR-0012](ADR/ADR-0012-consumer-registro-locale.md)) | Modello a processi del corso; nessuna sincronizzazione che alteri l'interleaving misurato (D11, D13) | Lista condivisa e sincronizzata (D13); `Runnable` + executor (non documentata) | Chiamare `run()` invece di `start()` non dà errori (limite noto, D11) |
| `simulation.Simulation` | **Coordinatore fork-join con scadenza unica** (deadline) e **watchdog**: start, `join` con tempo residuo, interrupt, margine ([ADR-0003](ADR/ADR-0003-timeout-scadenza-unica.md), [ADR-0014](ADR/ADR-0014-simulation-config-result.md)) | Il thread chiamante non attende mai senza limite (D15) | Timeout per singola `join` (i tempi si sommano); pillole dal thread chiamante (D15) | Un thread che ignora `interrupt()` resta vivo (daemon, segnalato); `run()` interrotto non pulisce (R-5) |
| `tui.Tui`, `tui.Box`, `tui.LiveView`, `tui.ResultView` | **Separazione controller / view-model**: `Tui` è un controller a macchina a stati (un metodo per schermata), `Box`/`LiveView`/`ResultView` sono costruttori puri di righe, senza I/O ([ADR-0016](ADR/ADR-0016-tui.md)) | Le trasformazioni dati → testo sono testabili senza terminale (D17) | Una classe per schermata (D17: stato condiviso da passare) | `Tui` è la classe più lunga (325 righe) e concentra la navigazione |
| `tui.Tui.Runner` | **Worker thread con attesa a polling**: il thread main fa `join(100)` in ciclo ed è anche la cadenza di ridisegno | Nessun thread di disegno da fermare (D17) | Thread di aggiornamento separato (D17) | `Runner` non è daemon e non è interrompibile (R-3) |
| `tui.Tui` (costruttore con `Reader`/`Writer`) | **Dependency Injection** del terminale | I test sostituiscono `System.in`/`System.out` (D17) | Uso diretto di `System.in`/`System.out` | Nessuno rilevante |
| `tui.Theme`, `tui.Box`, `tui.ResultView`, `scenario.Scenarios` | **Utility class non istanziabile** (costruttore privato, metodi statici) | Un solo punto per le sequenze ANSI e per le costanti (D17) | Istanze con stato | Metodi statici: non sostituibili nei test (non necessario) |

## 2. Pattern principali: dove sono

| Pattern | Dove | Come si riconosce nel codice |
|---|---|---|
| Strategy sui buffer | `Buffer` + 4 implementazioni; `Simulation` riceve un `Buffer` nel costruttore | `new Simulation(buffer, config, listener)`: lo stesso codice con quattro buffer |
| Template Method | `BufferScenario.run` (`final`) | `createBuffer` è l'unico passo lasciato alle sottoclassi |
| Factory Method | `BufferScenario.createBuffer` | ogni sottoclasse restituisce un buffer nuovo (`UnsafeBuffer(capacity, delayMillis)`, `MonitorBuffer(capacity)`, ...) |
| Observer | `SimulationListener`, implementato da `LiveView` | 4 notifiche (`beforePut`, `onPut`, `beforeTake`, `onTake`) |
| Null Object | `SimulationListener.NONE` | istanza anonima con quattro metodi vuoti; usata in misure e test |
| Poison pill | `Item.POISON`, `Simulation.putPoisonPills`, `Consumer.run` | `if (item.isPoison()) return;` |

## 3. Pattern deliberatamente non usati

- **Generics su `Buffer<T>`**: un solo tipo di elemento (D5).
- **Executor / `ExecutorService`**: producer e consumer sono thread con ciclo di vita proprio, come nel modello del corso (D11).
- **Builder** per `SimulationConfig`: costruttore a sei argomenti validato; i parametri della TUI sono comunque modificati uno alla volta in `Parameters` (non documentata in design.md come scelta esplicita).
- **Lambda, method reference, stream, `record`**: vincolo di stile ([ADR-0017](ADR/ADR-0017-stile-codice-classico.md)).

## 4. Pattern di estensione

| Estensione | Pattern coinvolto | Riferimento |
|---|---|---|
| `SemaphoreBuffer` | nuova Strategy su `Buffer` | [ARCHITECTURE](ARCHITECTURE.md) §6.1 |
| `SemaphoreScenario` | nuova sottoclasse (Factory Method) | [ARCHITECTURE](ARCHITECTURE.md) §6 (E-2) |
| Listener su file | nuovo Observer | E-3 |
