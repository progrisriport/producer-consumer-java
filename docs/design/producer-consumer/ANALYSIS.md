# ANALYSIS — producer-consumer

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.1 · Stato: reviewed
> Documenti: [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [BEHAVIOR](BEHAVIOR.md) · [DESIGN-PATTERNS](DESIGN-PATTERNS.md) · [DESIGN-IN-THE-SMALL](DESIGN-IN-THE-SMALL.md) · [TEST-STRATEGY](TEST-STRATEGY.md) · [ADR](ADR/ADR-0001-stack.md) · [DESIGN-STATUS](DESIGN-STATUS.md)

Limiti noti, dipendenze, debito tecnico, valutazione dei requisiti trasversali e divergenze tra codice e
`docs/design.md`, riferiti al codice del commit corrente. In caso di divergenza fa fede il codice.

## Fonti

- Codice: 30 file in `src/main`, 26 in `src/test`, 8 package.
- Motivazioni: `docs/design.md` (D1–D17), `README.md`, `docs/GUIDE.md`.
- Suite eseguita il 2026-11-23 con `mvn test`: 178 test, 0 falliti, 0 errori, 0 ignorati.

## 1. Limiti noti

Comportamenti del codice attuale che hanno conseguenze visibili o che richiedono attenzione a chi lo
modifica. La colonna "Comportamento" descrive ciò che il codice fa oggi.

| ID | Limite | Prob. | Impatto | Comportamento |
|---|---|---|---|---|
| R-1 | Scenario 2: con `UnsafeBuffer` le poison pill possono essere sovrascritte come qualsiasi item; il consumer che non riceve la pillola resta in polling | alta | media | il run termina per timeout (30 s di default, [ADR-0003](ADR/ADR-0003-timeout-scadenza-unica.md)); il consumer, interrotto, esce da `pause()` e risulta `interrupted` |
| R-2 | Attesa in polling con `Thread.yield()` in `UnsafeBuffer`: ogni thread in attesa occupa un core | alta | bassa | scelta di D7 (`wait()` richiede il monitor); durata limitata dal timeout |
| R-3 | `Tui.Runner` non è daemon e la TUI non legge input durante un run: non esiste un comando di annullo | media | media | un run dura al massimo timeout + `GRACE_MILLIS` (salvo R-11); l'unico arresto anticipato è Ctrl+C, che termina la JVM |
| R-4 | Un thread che ignora `interrupt()` resta vivo dopo il margine | bassa | bassa | tutti i thread della simulazione sono daemon; i nomi sono riportati in `SimulationResult.getNotTerminated()`; i quattro buffer del progetto rispondono all'interruzione |
| R-5 | Se il thread chiamante di `Simulation.run` viene interrotto, `InterruptedException` esce da `joinAll` senza interrompere producer e consumer | bassa | bassa | i worker sono daemon; nell'app il chiamante è `Tui.Runner`, che nessuno interrompe |
| R-6 | Dopo un timeout gli item rimasti nel buffer sono conteggiati come persi (`persi = prodotti − distinti`) | media | bassa | il verdetto riporta anche "stopped by timeout"; `SimulationResult` non distingue le due cause |
| R-7 | Dopo un timeout i contatori dei thread ancora vivi sono letti senza happens-before e possono non essere aggiornati | bassa | bassa | documentato nel Javadoc di `Simulation.buildResult` |
| R-8 | `Item.isPoison()` confronta per identità, `equals` per valore: `new Item(-1, -1)` è uguale a `POISON` ma non è la pillola | bassa | media | gli id di `IdGenerator` partono da 0 e i `producerId` da 1: nessun item reale ha quei valori; il costruttore non valida gli argomenti |
| R-9 | Output per terminale UTF-8 e truecolor, larghezza fissa 60 colonne | bassa | bassa | su terminali senza truecolor i colori sono approssimati o ignorati; `Box.seconds` usa `Locale.ROOT`, gli altri `String.format` formattano solo interi e stringhe |
| R-10 | Test con soglie temporali: `join(200)` + `isAlive()`, `join(2000)` + `assertFalse(isAlive())` in `BufferTest`; `timeoutStopsBlockedThreads` con limite `< 200 + GRACE_MILLIS` ms; `@Timeout` di 5 s su `BufferTest` e sottoclassi, che in `SynchronizedBufferTest` trasferiscono 20.000 item | media su macchine lente o cariche | media | sulla macchina di sviluppo la suite passa sempre |
| R-11 | `SequentialScenario` non applica il timeout | media | media | la durata è circa `2 × items × delay`: con i limiti massimi (1.000.000 item, 2.000 ms) non è limitata in pratica |
| R-12 | `Tui.Runner` cattura `InterruptedException` e `RuntimeException`, non `Error` | bassa | bassa | un `Error` termina il runner con `result` e `failure` a `null`; la TUI mostra "Run failed: null" e lo stack trace va su stderr |

## 2. Dipendenze

| Dipendenza | Versione | Stato |
|---|---|---|
| JDK | 21 LTS (`maven.compiler.release` = 21) | supportata |
| JUnit Jupiter | 5.11.4 (solo test) | linea 5; la linea corrente è JUnit 6, https://docs.junit.org/6.0.0/release-notes/ |
| maven-compiler-plugin | 3.13.0 | versione fissata nel `pom.xml` |
| maven-surefire-plugin | 3.5.2 | versione fissata nel `pom.xml` |
| exec-maven-plugin | 3.5.0 | versione fissata nel `pom.xml` |
| Terminale | UTF-8, truecolor | dipende dall'ambiente (R-9) |

Nessuna dipendenza a runtime oltre al JDK.

## 3. Debito tecnico

| ID | Descrizione |
|---|---|
| DT-1 | La versione del JDK è vincolata solo da `release=21`: nessun Maven wrapper né `maven-enforcer-plugin` |
| DT-2 | `SmokeTest` contiene solo `assertTrue(true)`; `App.main` non ha test |
| DT-3 | Validazione degli intervalli duplicata: `Parameters.set` ripete i controlli di `SimulationConfig` con gli stessi limiti |
| DT-4 | Il vincolo di stile classico è applicato in tutto il codice ma non è scritto in `design.md` ([ADR-0017](ADR/ADR-0017-stile-codice-classico.md)) |
| DT-5 | Test su JUnit 5.11.4 mentre la linea corrente è JUnit 6 |
| DT-6 | `design.md` D15 descrive ancora l'interfaccia `Scenario` come "da definire" (V-1) |
| DT-7 | `Tui` (325 righe) contiene tutte le schermate e la navigazione |
| DT-8 | Il controllo di `null` e della capacità è ripetuto nei costruttori e nei `put` dei 4 buffer |
| DT-9 | `Parameters.isLive()` legge il ritardo con l'indice fisso `values[5]` |

## 4. Valutazione dei requisiti trasversali

| Requisito | Esito | Motivazione |
|---|---|---|
| Mantenibilità | pass | responsabilità separate per classe, contratti documentati; lacune DT-3, DT-7, DT-9 |
| Riproducibilità | pass con riserva | build Maven con plugin a versione fissata e nessuna dipendenza runtime; riserve DT-1, R-10 |
| Modularità | pass | grafo dei package aciclico; `event` è un package separato per evitare un ciclo tra `actor` e `simulation` (D14); `Simulation` e `Tui` hanno molte dipendenze, tutte verso package inferiori |
| Estensibilità | pass | un nuovo buffer non tocca altri moduli; un nuovo scenario richiede una riga in `Scenarios`; un nuovo parametro richiede modifiche in 3 classi (E-6 in [ARCHITECTURE](ARCHITECTURE.md) §6) |

## 5. Divergenze codice / `design.md`

| ID | Divergenza |
|---|---|
| V-1 | D15 descrive l'interfaccia `Scenario` come "da definire"; l'interfaccia esiste in `scenario.Scenario` |
| V-2 | D17: pulizia dello schermo con `ESC[2J`; `Theme.CLEAR` è `ESC[H` + `ESC[2J` (cursore in alto a sinistra, poi pulizia) |
| V-3 | `design.md` non nomina `WorkerReport` né `Tui.Runner` |
| V-4 | Sezione "Test dei buffer": l'unsafe è verificato "asserendo solo nessuna eccezione"; i test asseriscono `getNotTerminated().isEmpty()` (`SimulationTest.unsafeBufferAlwaysReturns`, `BufferScenarioTest.unsafeScenarioAlwaysReturns`) |
| V-5 | `UnsafeBufferRaceTest` (contratto base con `raceMillis = 1`) non è citato in `design.md` |
| V-6 | D4 non documenta l'asimmetria tra `isPoison()` (identità) ed `equals` (valore) (R-8) |
| V-7 | `docs/GUIDE.md` §4: il diagramma dei package omette gli archi `scenario → buffer/counter/event/model` usati da `SequentialScenario` |
| V-8 | Il vincolo di stile classico non è scritto in `design.md` (DT-4) |
| V-9 | Indice delle decisioni, D10 "IdGenerator: AtomicInteger"; titolo della sezione "contatore atomico" |
| V-10 | `SmokeTest` non è citato in `design.md` (DT-2) |

## 6. Convenzioni di questa documentazione

- I nomi dei pattern (GoF e affini) sono assegnati in questa documentazione: `design.md` motiva le
  scelte senza usare le etichette del catalogo.
- Gli ADR riportano le alternative scartate registrate in `design.md`; quando `design.md` non ne
  registra, l'ADR lo indica. ADR-0017 non ha una D di origine.
- I diagrammi di `ArrayBlockingQueue` ([BEHAVIOR](BEHAVIOR.md) §6.7–6.8) descrivono l'implementazione
  OpenJDK della libreria standard, non codice del progetto.
