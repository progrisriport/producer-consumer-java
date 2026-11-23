# ANALYSIS — producer-consumer

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato: reviewed
> Documenti: [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [BEHAVIOR](BEHAVIOR.md) · [DESIGN-PATTERNS](DESIGN-PATTERNS.md) · [DESIGN-IN-THE-SMALL](DESIGN-IN-THE-SMALL.md) · [TEST-STRATEGY](TEST-STRATEGY.md) · [ADR](ADR/ADR-0001-stack.md) · [DESIGN-STATUS](DESIGN-STATUS.md)

Rischi, dipendenze, debito tecnico, valutazione dei requisiti trasversali e divergenze tra codice e
`docs/design.md`. In caso di divergenza fa fede il codice.

## Discovery

Progetto non greenfield: 30 file in `src/main`, 26 in `src/test`, 8 package. Fonti delle motivazioni:
`docs/design.md` (D1–D17), `README.md`, `docs/GUIDE.md`.

## 1. Rischi (probabilità × impatto)

| ID | Rischio | Prob. | Impatto | Mitigazione |
|---|---|---|---|---|
| R-1 | Scenario 2: le poison pill possono andare perse con `UnsafeBuffer`; un consumer attende fino al timeout (30 s di default) | alta | media | timeout ([ADR-0003](ADR/ADR-0003-timeout-scadenza-unica.md)); comportamento atteso |
| R-2 | Polling con `yield()` in `UnsafeBuffer`: CPU al 100% per ogni thread in attesa | alta | bassa | voluto (D7), limitato dal timeout |
| R-3 | `Tui.Runner` non daemon e non interrompibile: un run lungo si ferma solo con Ctrl+C; un `IOException` della TUI non termina la JVM finché il runner è vivo | media | media | proposta: `setDaemon(true)` e comando di annullo |
| R-4 | Un thread che ignora `interrupt()` resta vivo | bassa | bassa | thread daemon, riportato in `notTerminated` |
| R-5 | `Simulation.run` interrotta dal chiamante esce da `joinAll` senza interrompere i worker | bassa | bassa | proposta: `finally` con interrupt dei thread vivi |
| R-6 | Dopo un timeout gli item rimasti nel buffer risultano "persi" | media | bassa | il verdetto riporta anche "stopped by timeout" |
| R-7 | Dopo un timeout i contatori dei thread ancora vivi, senza happens-before, possono essere stantii | bassa | bassa | documentato nel Javadoc di `Simulation.buildResult` |
| R-8 | `Item.isPoison()` confronta per identità, `equals` per valore: `new Item(-1, -1)` è uguale a `POISON` ma non è la pillola | bassa | media | gli id partono da 0; proposta: validare il costruttore |
| R-9 | Terminale UTF-8/truecolor, larghezza fissa 60; `String.format` con locale predefinito | bassa | bassa | proposta: `Locale.ROOT` ovunque |
| R-10 | Test sensibili ai tempi: `join(200)` + `assertFalse(isAlive())` entro 2 s; `timeoutStopsBlockedThreads` con limite `< 1200 ms`; `@Timeout` di 5 s con 20.000 item | media su macchine lente | media | allargare i limiti o usare latch |
| R-11 | `SequentialScenario` ignora il timeout: con item × ritardo alti (fino a 1.000.000 × 2 s) la durata è illimitata | media | media | proposta: limitare item × ritardo nella TUI |
| R-12 | `Runner` cattura solo `InterruptedException` e `RuntimeException`: un `Error` lascia `result` e `failure` a `null` e la TUI mostra "Run failed: null" | bassa | bassa | catturare `Throwable` |

## 2. Dipendenze

| Dipendenza | Versione | Stato |
|---|---|---|
| JDK | 21 LTS (`maven.compiler.release`) | stabile |
| JUnit Jupiter | 5.11.4 | linea precedente; JUnit 6 (baseline Java 17) è la corrente, https://docs.junit.org/6.0.0/release-notes/ |
| maven-compiler-plugin | 3.13.0 | pinnato |
| maven-surefire-plugin | 3.5.2 | pinnato |
| exec-maven-plugin | 3.5.0 | pinnato |
| Terminale | UTF-8, truecolor | variabile (R-9) |

## 3. Debito tecnico

| ID | Descrizione |
|---|---|
| DT-1 | JDK non pinnato oltre `release=21`: nessun Maven wrapper né enforcer |
| DT-2 | `SmokeTest` è `assertTrue(true)`; nessun test su `App.main` |
| DT-3 | Validazione degli intervalli duplicata in `Parameters.set` e `SimulationConfig` |
| DT-4 | Vincolo di stile classico assente da `design.md` ([ADR-0017](ADR/ADR-0017-stile-codice-classico.md)) |
| DT-5 | JUnit 5 anziché 6 |
| DT-6 | Testo obsoleto in `design.md` D15 ("Scenario, da definire") |
| DT-7 | `Tui` (325 righe) concentra tutta la navigazione |
| DT-8 | Validazione di `null` e capacità ripetuta nei 4 buffer |
| DT-9 | `Parameters.isLive()` usa l'indice fisso `values[5]` |

## 4. Valutazione dei requisiti trasversali

| Requisito | Esito | Note |
|---|---|---|
| Mantenibilità | pass | lacune DT-3, DT-7, DT-9 |
| Riproducibilità | pass con riserva | DT-1, R-10 |
| Modularità | pass | grafo dei package aciclico; `event` separato per evitare un ciclo; `Simulation` e `Tui` hanno molte dipendenze, tutte verso package inferiori |
| Estensibilità | pass | nuovo buffer senza toccare altri moduli; nuovo scenario con una riga in `Scenarios`; un nuovo parametro richiede modifiche in 3 classi (E-6 in [ARCHITECTURE](ARCHITECTURE.md)) |

## 5. Domande aperte non bloccanti

1. Java 21 o 17? Il sorgente è probabilmente compatibile con 17, non verificato.
2. Comando di annullo di un run dalla TUI?
3. Test statistico del difetto dell'`UnsafeBuffer`?
4. Distinguere "persi" da "rimasti nel buffer" dopo un timeout?
5. Aggiungere `SemaphoreBuffer` come scenario 6?

## 6. Divergenze codice / `design.md`

| ID | Divergenza |
|---|---|
| V-1 | Brief della sessione di design: Java 17; `pom.xml`, README, GUIDE e `design.md`: Java 21. Fa fede `pom.xml` |
| V-2 | D15 descrive l'interfaccia `Scenario` come "da definire"; esiste (testo obsoleto) |
| V-3 | D17: pulizia dello schermo con `ESC[2J`; `Theme.CLEAR` è `ESC[H` + `ESC[2J` |
| V-4 | `design.md` non nomina `WorkerReport` né `Tui.Runner` |
| V-5 | "Test dei buffer": l'unsafe verrebbe verificato "asserendo solo nessuna eccezione"; i test asseriscono `getNotTerminated().isEmpty()` |
| V-6 | Esiste `UnsafeBufferRaceTest` (contratto con `raceMillis = 1`), non citato in `design.md` |
| V-7 | D4: `isPoison()` confronta per identità; l'asimmetria con `equals` (R-8) non è documentata |
| V-8 | `docs/GUIDE.md` §4: il diagramma omette gli archi `scenario → buffer/counter/event/model` usati da `SequentialScenario` |
| V-9 | Il vincolo di stile classico non è scritto in `design.md` (DT-4) |
| V-10 | Indice D10 "IdGenerator: AtomicInteger" vs titolo della sezione "contatore atomico" (trascurabile) |
| V-11 | `SmokeTest` segnaposto non citato in `design.md` |

## 7. Assunzioni della revisione

- Modalità reverse-engineering: nessuno stack alternativo proposto.
- Dove `design.md` non cita un'alternativa scartata, l'ADR lo dichiara.
- Le etichette GoF dei pattern sono assegnate in questa revisione, non da `design.md`.
- ADR-0017 non ha una D di origine.
- Suite non rieseguita: il conteggio di 178 esecuzioni è quello di `README.md`.
- `BEHAVIOR.md` è un documento aggiuntivo rispetto al set standard.
- I diagrammi di `ArrayBlockingQueue` ([BEHAVIOR](BEHAVIOR.md) §6.7–6.8) descrivono l'implementazione OpenJDK, non codice del progetto.
