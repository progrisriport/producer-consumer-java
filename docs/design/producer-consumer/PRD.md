# PRD — producer-consumer

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato: reviewed
> Documenti: [ARCHITECTURE](ARCHITECTURE.md) · [BEHAVIOR](BEHAVIOR.md) · [DESIGN-PATTERNS](DESIGN-PATTERNS.md) · [DESIGN-IN-THE-SMALL](DESIGN-IN-THE-SMALL.md) · [TEST-STRATEGY](TEST-STRATEGY.md) · [ANALYSIS](ANALYSIS.md) · [ADR](ADR/ADR-0001-stack.md) · [DESIGN-STATUS](DESIGN-STATUS.md)

PRD ricostruito dal codice esistente (reverse-engineering, tutte le funzionalità sono implementate).
Fonti: sorgente in `src/main/java/producerconsumer`, test in `src/test/java`, `docs/design.md` (D1–D17),
`README.md`, `docs/GUIDE.md`. Dove il codice diverge da design.md fa fede il codice; le divergenze sono
elencate in [ANALYSIS](ANALYSIS.md), §6.

## 1. Problema

Lo studente di programmazione concorrente conosce il problema producer-consumer in teoria, ma
raramente vede cosa accade quando la sincronizzazione manca o è sbagliata: gli errori dipendono dallo
scheduler e non si manifestano in modo evidente. Serve uno strumento che:

1. esegua il problema con strategie di sincronizzazione diverse, comprese quelle sbagliate;
2. mostri l'esito in modo **numerico** (prodotti, consumati, persi, duplicati);
3. renda osservabile l'esecuzione (chi fa cosa, chi attende);
4. sia scritto in modo tale che lo studente possa leggerlo, giudicarlo e modificarlo da solo.

## 2. Obiettivi e non-obiettivi

### Obiettivi

- O-1: cinque scenari eseguibili da un menu a terminale (Sequential, Unsynchronized, Monitor, Lock and Condition, Blocking queue).
- O-2: dimostrare race condition (persi/duplicati) senza crash e sincronizzazione corretta (prodotti == consumati).
- O-3: parametri modificabili (producer, consumer, item, capacità, timeout, ritardo).
- O-4: l'applicazione torna sempre al menu, anche se un thread resta bloccato.
- O-5: codice leggibile come materiale di studio: classi piccole, stile classico, Javadoc impersonale ([ADR-0017](ADR/ADR-0017-stile-codice-classico.md)).
- O-6: suite di test affidabile, non "a volte verde" ([ADR-0004](ADR/ADR-0004-test-deterministici.md)).

### Non-obiettivi

- Benchmark delle prestazioni, GUI, salvataggio dei risultati su file (fuori scope dichiarato in design.md).
- Dimostrare in modo deterministico il difetto dell'`UnsafeBuffer` (non deterministico per natura).
- Interfaccia a tasto singolo o larghezza adattiva del terminale ([ADR-0016](ADR/ADR-0016-tui.md)).
- Cancellazione di un run in corso dalla TUI (solo Ctrl+C, vedi R-3).

## 3. Utenti e attori

| Attore | Descrizione | Interazione |
|---|---|---|
| Studente-utente | Esegue gli scenari, osserva i risultati | Menu, parametri, vista dal vivo, risultato |
| Studente-sviluppatore | Prende in mano il codice, lo capisce, lo giudica, lo modifica | Sorgente, test, questa documentazione, punti di estensione |
| Scheduler della JVM | Attore non umano che determina gli interleaving | Rende i risultati dello scenario 2 variabili tra esecuzioni |

L'utente primario è unico: uno studente che svolge entrambi i ruoli.

## 4. User stories

- US-1: come studente voglio scegliere uno scenario da un menu, per eseguirlo senza ricordare comandi.
- US-2: come studente voglio leggere la descrizione dello scenario prima di eseguirlo, per sapere cosa aspettarmi.
- US-3: come studente voglio modificare i parametri, per provare più producer che consumer (e viceversa), buffer piccoli o grandi.
- US-4: come studente voglio vedere i thread lavorare dal vivo (buffer, stato dei thread, log), per capire dove e perché si attende.
- US-5: come studente voglio un risultato numerico con verdetto, per giudicare a colpo d'occhio se la sincronizzazione è corretta.
- US-6: come studente voglio rieseguire lo stesso scenario, per vedere che lo scenario non sincronizzato varia a ogni run.
- US-7: come studente voglio che un run bloccato termini da solo, per non dover chiudere l'applicazione.
- US-8: come studente-sviluppatore voglio aggiungere un nuovo buffer (es. basato su semafori) senza modificare i moduli esistenti, per confrontarlo con gli altri.
- US-9: come studente-sviluppatore voglio capire ogni classe senza leggere le altre, per modificarla in autonomia.
- US-10: come studente-sviluppatore voglio eseguire la suite di test e ottenere sempre lo stesso esito, per fidarmi delle mie modifiche.

## 5. Requisiti funzionali

Colonna "Moduli": classi principali in [ARCHITECTURE](ARCHITECTURE.md), §3; matrice completa in §7.

| ID | Requisito | Origine | Moduli |
|---|---|---|---|
| FR-1 | Il menu elenca i cinque scenari nell'ordine Sequential, Unsynchronized, Monitor, Lock and Condition, Blocking queue; ognuno si apre da un numero | D16, D17 | `Scenarios`, `Tui` |
| FR-2 | Lo scenario Sequential esegue produzione e consumo alternati su un solo thread: nessun errore, riporta `producer-1` e `consumer-1`, ignora numero di producer/consumer e timeout | D16 | `SequentialScenario` |
| FR-3 | Lo scenario Unsynchronized usa un buffer senza mutua esclusione e produce item persi e duplicati senza lanciare eccezioni | D7 | `UnsafeBuffer`, `UnsafeScenario` |
| FR-4 | Lo scenario Monitor usa `synchronized` + `wait`/`notifyAll`: ogni item è consumato esattamente una volta | D8 | `MonitorBuffer`, `MonitorScenario` |
| FR-5 | Lo scenario Lock and Condition usa `ReentrantLock` e due `Condition`: ogni item è consumato esattamente una volta | D8 | `LockBuffer`, `LockScenario` |
| FR-6 | Lo scenario Blocking queue usa `ArrayBlockingQueue`: ogni item è consumato esattamente una volta | D9 | `BlockingQueueBuffer`, `BlockingQueueScenario` |
| FR-7 | I parametri (producer, consumer, item, capacità, timeout, ritardo) sono modificabili con limiti visibili e condivisi tra gli scenari | D15, D17 | `Parameters`, `SimulationConfig`, `Tui` |
| FR-8 | Un run funziona con qualsiasi numero di producer e consumer (anche più consumer che producer); il totale degli item prodotti è fisso | D11, D12 | `Simulation`, `Producer`, `Consumer`, `Quota` |
| FR-9 | I consumer terminano tramite una poison pill per consumer inserita dal coordinatore dopo la fine dei producer | D12 | `Simulation`, `Consumer`, `Item` |
| FR-10 | Un timeout interrompe i thread ancora vivi; il run ritorna entro timeout + 1 s e l'applicazione torna al menu | D2, D15 | `Simulation`, `Tui` |
| FR-11 | Il risultato riporta prodotti, consumati, distinti, persi, duplicati, durata, dettaglio per thread (con marcatura di interruzione), item duplicati con occorrenze, esito del timeout, thread non terminati | D1, D15 | `SimulationResult`, `WorkerReport` |
| FR-12 | La schermata del risultato mostra totali, thread (max 8 righe), duplicati (primi 10) e un verdetto verde/rosso | D17 | `ResultView`, `Tui` |
| FR-13 | Con ritardo > 0 la TUI mostra la vista dal vivo (buffer con barra, thread con stato e conteggio, log degli ultimi 6 eventi); con ritardo 0 solo il risultato | D14 | `LiveView`, `SimulationListener`, `Tui` |
| FR-14 | Il ritardo rallenta ogni operazione fuori dal buffer; nello scenario non sincronizzato allarga la finestra della race (`raceMillis`) | D14 | `Producer`, `Consumer`, `SequentialScenario`, `UnsafeBuffer`, `UnsafeScenario` |
| FR-15 | Un input non valido produce un messaggio breve in rosso e una nuova richiesta | D17 | `Tui` |
| FR-16 | Dopo il risultato si torna al dettaglio dello scenario, per rieseguirlo | D17 | `Tui` |
| FR-17 | Un listener nullo (`NONE`) permette run senza osservatori (misure, test) | D14 | `SimulationListener` |

## 6. Requisiti non-funzionali

| ID | Requisito | Verifica | Moduli |
|---|---|---|---|
| NFR-1 Mantenibilità | Ogni classe ha una sola responsabilità dichiarata; errori specificati (eccezioni documentate); naming coerente (`get*`, `was*`, `before*`/`on*`); Javadoc impersonale in italiano | revisione; [ARCHITECTURE](ARCHITECTURE.md) §3 | tutti |
| NFR-2 Riproducibilità | Versioni di plugin e di JUnit pinnate nel `pom.xml`; test senza dipendenze dall'ordine dei thread; il JDK è vincolato solo da `release=21` (lacuna DT-1) | `pom.xml`, [TEST-STRATEGY](TEST-STRATEGY.md) | `pom.xml`, `BufferTest`, `SynchronizedBufferTest`, `SimulationTest` |
| NFR-3 Modularità | Otto package con grafo di dipendenze aciclico; interfacce `Buffer`, `Scenario`, `SimulationListener` come confini | [ARCHITECTURE](ARCHITECTURE.md) §1.3 | `buffer`, `scenario`, `event`, `simulation`, `actor`, `tui`, `counter`, `model` |
| NFR-4 Estensibilità | Nuovo buffer e nuovo scenario senza modificare i moduli esistenti, salvo la riga in `Scenarios.all()` | [ARCHITECTURE](ARCHITECTURE.md) §6 | `Buffer`, `BufferScenario`, `Scenarios` |
| NFR-5 Nucleo senza I/O | Nessun output fuori dal package `tui`; un solo thread scrive sul terminale | revisione del sorgente | `simulation`, `scenario`, `actor`, `buffer`, `tui` |
| NFR-6 Test affidabili | Ogni test concorrente ha `@Timeout`; si asseriscono solo invarianti | [ADR-0004](ADR/ADR-0004-test-deterministici.md) | `BufferTest`, `SynchronizedBufferTest`, `SimulationTest` |
| NFR-7 Nessuna dipendenza runtime | Unica dipendenza: JUnit 5 in scope `test` | `pom.xml` | `pom.xml` |
| NFR-8 Portabilità del terminale | Larghezza fissa di 60 colonne, UTF-8, truecolor 24 bit, nessuno sfondo, input a righe | [ADR-0016](ADR/ADR-0016-tui.md) | `Theme`, `Box`, `Tui`, `App` |
| NFR-9 Terminazione | Ogni run concorrente termina entro timeout + `GRACE_MILLIS` (1 s), salvo thread che ignorano `interrupt()` (segnalati) | `SimulationTest.timeoutStopsBlockedThreads` | `Simulation` |
| NFR-10 Stile del codice | Solo `if`/`for`/`while`/`do`/`switch`/for-each; niente lambda, method reference, stream, ternario, `record` | [ADR-0017](ADR/ADR-0017-stile-codice-classico.md) | tutti |
| NFR-11 Lingua | Identificatori in inglese; testi visibili in inglese semplice; documentazione tecnica in italiano impersonale | revisione | tutti |
| NFR-12 Determinismo dell'output | A parità di input la TUI produce lo stesso output (larghezza fissa); `Box.seconds` usa `Locale.ROOT`, gli altri `String.format` il locale predefinito (nota in R-9 di [ANALYSIS](ANALYSIS.md)) | `TuiTest`, `BoxTest` | `Box`, `Tui`, `ResultView`, `LiveView` |

## 7. Success metrics

| Metrica | Obiettivo | Come si misura |
|---|---|---|
| Scenari avviabili dal menu | 5 su 5 | `ScenariosTest`, `TuiTest` |
| Scenari corretti | prodotti == consumati == distinti; persi = duplicati = 0 | `BufferScenarioTest`, `SimulationTest` (10.000 item, 4 producer, 3 consumer) |
| Scenario non sincronizzato | termina senza eccezioni; con ritardo 0, 2 producer, 2 consumer, 10.000 item: tra 1091 e 3477 item persi (misura riportata in design.md D17) | osservazione manuale; nessun test asserisce il difetto |
| Ritorno al menu | sempre, entro timeout + 1 s | `SimulationTest.timeoutStopsBlockedThreads`, `.reportsThreadsThatIgnoreInterrupt` |
| Affidabilità della suite | stesso esito a ogni esecuzione | `mvn test`; 178 esecuzioni dichiarate in `README.md` (conteggio non riverificato in questa sessione) |
| Leggibilità per lo studente | ogni classe comprensibile senza leggere le altre | giudizio dell'utente; [ARCHITECTURE](ARCHITECTURE.md) §3 |

## 8. Scope e out-of-scope

**In scope (implementato):** i cinque scenari; menu, dettaglio, parametri, vista dal vivo, risultato;
nucleo concorrente (`Simulation`, buffer, attori, contatori); suite di test; documentazione
(`README.md`, `docs/GUIDE.md`, `docs/design.md`, questa cartella).

**Out of scope:** benchmark; GUI; salvataggio su file; terminali senza UTF-8; input a tasto singolo;
cancellazione di un run dalla TUI; elenco degli item persi (i producer li contano ma non li registrano);
generics sul buffer; ripresa di un run interrotto.

## 9. Rischi

Sintesi; dettaglio con probabilità e impatto in [ANALYSIS](ANALYSIS.md), §1.

| ID | Rischio | Prob. × Impatto |
|---|---|---|
| R-1 | Scenario 2: una poison pill persa lascia un consumer in attesa fino al timeout (fino a 30 s di default) | alta × media |
| R-2 | Polling con `yield()` nell'`UnsafeBuffer`: carico CPU durante le attese | alta × bassa |
| R-3 | `Runner` non daemon e non interrompibile: un run lungo si ferma solo con Ctrl+C | media × media |
| R-5 | `Simulation.run` interrotta dal chiamante lascia i worker in esecuzione | bassa × bassa |
| R-6 | Dopo un timeout, gli item rimasti nel buffer risultano "persi" | media × bassa |
| R-8 | `Item.isPoison()` per identità ma `equals` per valore | bassa × media |
| R-11 | Scenario Sequential: nessun timeout; con item e ritardo alti dura molto a lungo | media × media |

## 10. Riferimenti agli ADR

| ADR | Titolo | Decisioni di origine |
|---|---|---|
| [0001](ADR/ADR-0001-stack.md) | Stack tecnologico | Vincoli, D17 |
| [0002](ADR/ADR-0002-identificazione-item.md) | Identificazione degli item | D1, D10 |
| [0003](ADR/ADR-0003-timeout-scadenza-unica.md) | Timeout con scadenza unica | D2, D15 |
| [0004](ADR/ADR-0004-test-deterministici.md) | Test: invarianti e timeout | D3 |
| [0005](ADR/ADR-0005-item-e-poison-pill.md) | `Item` e poison pill | D4 |
| [0006](ADR/ADR-0006-interfaccia-buffer.md) | Interfaccia `Buffer` | D5, D6 |
| [0007](ADR/ADR-0007-unsafe-buffer.md) | `UnsafeBuffer` | D7, D14 |
| [0008](ADR/ADR-0008-monitor-e-lock-buffer.md) | `MonitorBuffer` e `LockBuffer` | D8 |
| [0009](ADR/ADR-0009-blocking-queue-buffer.md) | `BlockingQueueBuffer` | D9 |
| [0010](ADR/ADR-0010-producer-quota.md) | `Producer` e `Quota` | D11 |
| [0011](ADR/ADR-0011-terminazione-poison-pill.md) | Terminazione con poison pill | D12 |
| [0012](ADR/ADR-0012-consumer-registro-locale.md) | `Consumer` | D13 |
| [0013](ADR/ADR-0013-osservazione-eventi.md) | Listener, Null Object, ritardo | D14 |
| [0014](ADR/ADR-0014-simulation-config-result.md) | `Simulation`, config, risultato | D15 |
| [0015](ADR/ADR-0015-scenari.md) | Scenari | D16 |
| [0016](ADR/ADR-0016-tui.md) | TUI | D17 |
| [0017](ADR/ADR-0017-stile-codice-classico.md) | Stile di codice classico | nessuna D (lacuna DT-4) |
