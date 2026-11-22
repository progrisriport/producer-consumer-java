# Producer-Consumer didattico — Design

Requisiti e decisioni di progettazione. Ogni decisione ha la sua motivazione.
Riferimento tecnico per le classi: [GUIDE.md](GUIDE.md) (inglese).

## Indice delle decisioni

Le decisioni sono numerate in ordine cronologico e non vengono rinumerate. Ogni nuova decisione
si aggiunge in coda alla propria sezione, con il formato **Decisione / Motivazione / Conseguenze**.

| ID | Titolo | Area | Stato |
|---|---|---|---|
| D1 | Identificazione degli elementi: contatore globale | Analisi | registrata |
| D2 | Rilevamento dei blocchi: timeout | Analisi | registrata |
| D3 | Test affidabili: invarianti e timeout | Analisi | registrata |
| D4 | `Item`: classe immutabile con poison pill come costante | Classi | implementata |
| D5 | `Buffer`: interfaccia minima, specifica per `Item`, interrompibile | Classi | implementata |
| D6 | Regole comuni a tutti i buffer | Classi | implementata |
| D7 | `UnsafeBuffer`: array circolare, contatore non atomico, attesa in polling | Classi | implementata |
| D8 | `MonitorBuffer` e `LockBuffer`: stessa logica, primitive diverse | Classi | implementata |
| D9 | `BlockingQueueBuffer`: delega a `ArrayBlockingQueue` | Classi | implementata |
| D10 | `IdGenerator`: `AtomicInteger`, id da 0 | Classi | registrata |
| D11 | `Producer`: thread con quota condivisa | Classi | registrata |
| D12 | Terminazione: poison pill inserite dal coordinatore | Classi | registrata |
| D13 | `Consumer`: thread con registro locale degli item consumati | Classi | registrata |
| D14 | Visualizzazione: osservazione dal vivo e rallentamento | Simulazione | in definizione |
| D15 | Simulazione: `Scenario`, `Simulation`, configurazione e risultato | Simulazione | registrata |
| D16 | Scenari: interfaccia `Scenario` e implementazioni | Simulazione | implementata |
| D17 | TUI | TUI | in definizione |

## Requisiti

### Obiettivo

App da terminale didattica per il corso di Programmazione Concorrente (Insubria).
Uno studente la apre, sceglie uno scenario dal menu, lo esegue e vede — numericamente —
cosa succede al problema produttore/consumatore con diverse strategie di sincronizzazione,
comprese quelle sbagliate.

### Scenari richiesti

1. **Sequenziale** — caso base, nessuna concorrenza.
2. **Non sincronizzato** — mostra il problema: elementi persi o duplicati.
3. **Monitor** — `synchronized` + `wait`/`notifyAll`.
4. **Lock e Condition** — `ReentrantLock` + `Condition`.
5. **Coda bloccante** — struttura della libreria standard.

### Criteri di successo

- I cinque scenari si avviano dal menu.
- Gli scenari corretti mostrano prodotti == consumati.
- Lo scenario non sincronizzato mostra elementi persi/duplicati **senza crashare**.
- L'app torna sempre al menu.
- Funziona con qualsiasi numero di producer e consumer (anche più consumer che producer).
- La suite di test passa in modo affidabile (non "a volte").

### Vincoli

- Java 21, Maven, un solo modulo. Package base `producerconsumer`.
- Unica dipendenza: JUnit 5 (solo test). Nessuna dipendenza runtime.
- Codice e identificatori in inglese. Testi visibili all'utente in inglese semplice e chiaro,
  comprensibile anche a chi non è madrelingua.
- Il nucleo della simulazione non stampa nulla: l'I/O sta solo nella parte TUI.
- Un test concorrente non deve mai poter bloccare la build.
- Il vecchio progetto Eclipse resta recuperabile dal tag `v0-eclipse-tui`.

### TUI

- Java puro con codici ANSI, nessuna libreria.
- Design system personale: accento `#E8B84B`, testo `#D4D4D4`, secondario `#7A7A7A`,
  bordi `#3A3A3A`, errore `#E85B4B`, successo `#4BE87A`. Nessuno sfondo esplicito.
  Box-drawing Unicode, header con nome app, footer con scorciatoie sempre presente.
- Parametri della simulazione modificabili dall'utente.
- Input non valido: messaggio breve in rosso e nuova richiesta.
- Colori definiti in un solo punto; marcatore di selezione `>`.

### Fuori scope

Benchmark, GUI, salvataggio risultati su file.

## Fase 1 — Analisi dei requisiti

### D1. Identificazione degli elementi: contatore globale

**Decisione:** ogni elemento prodotto ha un id unico, generato da un contatore condiviso da tutti
i producer (non un contatore per producer).

**Motivazione:** per misurare persi e duplicati ogni elemento deve essere distinguibile. Con un contatore
per producer due producer generano lo stesso id e un "duplicato" diventa indistinguibile da due
elementi diversi.

**Conseguenze:**
- A fine simulazione si contano tre valori: prodotti, consumati, consumati distinti.
  - persi = prodotti − distinti
  - duplicati = consumati − distinti
- Il contatore è condiviso tra thread → è anch'esso una risorsa concorrente (da risolvere in Fase 5).

### D2. Rilevamento dei blocchi: timeout

**Decisione:** un timeout per ogni scenario come rete di sicurezza: scaduto, i thread vengono
interrotti.

**Motivazione:** un thread può restare bloccato per sempre (es. consumer che attende un
elemento perso nello scenario non sincronizzato). Il timeout garantisce che l'app torni sempre
al menu.

### D3. Test affidabili: invarianti e timeout

**Decisione:**
- Ogni test concorrente ha un timeout: un blocco fa fallire il test, non blocca la build.
- Si testano invarianti (proprietà vere in qualunque interleaving), mai un ordine specifico
  di esecuzione dei thread.

**Motivazione:** l'ordine dei thread lo decide lo scheduler e cambia a ogni esecuzione.
Un test che dipende dall'ordine è flaky. Esempi di invarianti: prodotti == consumati,
nessun id consumato due volte.

**Questione aperta:** come testare comportamenti che *richiedono* un ordine
(es. "take si blocca se il buffer è vuoto")? → Fase 6.

## Classi

### D4. `Item`: classe immutabile con poison pill come costante

**Decisione:**
- `Item` è una classe `final` immutabile (campi `private final`, nessun setter) con due campi:
  `id` (`int`) e `producerId` (`int`). Scritta in forma classica, non come `record`:
  `equals`, `hashCode`, `toString` e getter sono espliciti.
- La poison pill è una costante di `Item` con valori impossibili per un elemento reale (−1),
  più un metodo che dice se un elemento è la pillola.

**Motivazione:**
- Immutabile → sicuro da condividere tra thread senza sincronizzazione.
- `producerId` permette di mostrare da quale producer veniva un elemento perso o duplicato.
- `int` basta per i volumi didattici. Limite noto: i parametri della TUI devono avere un massimo.
- Pillola come costante e non `null`: le code bloccanti della libreria standard rifiutano `null`;
  inoltre `null` non dice nulla, una costante con un nome sì.

### D5. `Buffer`: interfaccia minima, specifica per `Item`, interrompibile

**Decisione:**
- Package `producerconsumer.buffer`. Tre metodi: `put(Item)`, `take()`, `capacity()`.
- `put` e `take` dichiarano `throws InterruptedException`.
- Nessun `size()` / `isEmpty()`.
- Nessun generic: il buffer contiene solo `Item`.
- `take()` non restituisce mai `null`: su buffer vuoto attende (anche l'implementazione non
  sincronizzata, che attende in polling).

**Motivazione:**
- `InterruptedException` dichiarata → quando il timeout (D2) interrompe un thread bloccato,
  l'eccezione risale fino al ciclo di `run()` e il thread termina. Catturarla dentro il buffer
  la farebbe sparire e il thread resterebbe appeso.
- `size()`/`isEmpty()` invitano al check-then-act (`if (!isEmpty()) take()`): tra controllo e
  azione un altro thread può cambiare lo stato. La fine della simulazione la segnala la poison
  pill, non un controllo sul buffer. `capacity()` è sicuro perché immutabile.
- Un solo tipo di elemento → i generics aggiungerebbero complessità senza beneficio.
- Mai `null` → il consumer non deve distinguere "vuoto" da "item"; la pillola resta l'unico
  segnale speciale.

### D6. Regole comuni a tutti i buffer

**Decisione:**
- `put(null)` lancia `NullPointerException` in tutte le implementazioni, con un `if` esplicito (`ArrayBlockingQueue` lo fa già da sé).
- Capacità `<= 0` → `IllegalArgumentException` nel costruttore.
- Su buffer pieno/vuoto si attende sempre in un `while`, mai in un `if`.

**Motivazione:**
- Se `put(null)` fosse accettato, `take()` potrebbe restituire `null` e romperebbe D5.
  `ArrayBlockingQueue` rifiuta già `null`: così tutte le implementazioni si comportano uguale.
- Al risveglio la condizione va ricontrollata: un altro thread può essere arrivato prima,
  e la JVM ammette risvegli spuri.

### D7. `UnsafeBuffer`: array circolare, contatore non atomico, attesa in polling

**Decisione:**
- Array circolare `Item[]` con `head`, `tail`, `count`. Nessun lock.
- `count` è `volatile`; `count++` e `count--` restano non atomici.
- Su pieno/vuoto: polling con `Thread.yield()`, controllando a mano `Thread.interrupted()`.
- Guardie `count >= capacità` e `count <= 0` (non `==`); una cella mai scritta (`null`) viene saltata.
- Le celle non vengono svuotate dopo `take`: con `count` più alto del reale una cella già letta
  viene riletta (duplicato).

**Motivazione:**
- Array e non `LinkedList`: una lista modificata da più thread può corrompere i nodi e lanciare
  eccezioni. L'array resta sempre in uno stato valido → persi/duplicati **senza eccezioni**.
- `volatile` garantisce visibilità (il polling legge l'ultimo valore scritto), non atomicità.
  Due thread leggono lo stesso `count`, entrambi scrivono `count ± 1`: un aggiornamento
  va perso. Il contatore diverge → celle sovrascritte (persi) o rilette (duplicati).
- Niente `wait()`: richiede il possesso del monitor, quindi sincronizzazione.
- `yield()` non lancia `InterruptedException`: il controllo esplicito di `Thread.interrupted()` preserva D2 (il timeout interrompe i thread).
- `<=`/`>=`: con il contatore andato negativo, `== 0` non fermerebbe più i consumer.

### D8. `MonitorBuffer` e `LockBuffer`: stessa logica, primitive diverse

**Decisione:**
- `MonitorBuffer`: `synchronized` + `wait()` + `notifyAll()`, `LinkedList` come coda.
- `LockBuffer`: `ReentrantLock` + due `Condition` (`notFull`, `notEmpty`) + `signal()`,
  `unlock()` sempre in `finally`.

**Motivazione:**
- Monitor: una sola coda di attesa per producer e consumer. `notify()` potrebbe svegliare un thread
  della categoria sbagliata e lasciare dormire chi serve → serve `notifyAll()`.
- Lock: due code separate → su `notEmpty` attendono solo consumer, e un `put` aggiunge un solo item,
  quindi basta svegliarne uno con `signal()`.
- `finally`: senza, un'eccezione lascerebbe il lock preso per sempre.
- Si usa `lock()` (non `lockInterruptibly()`): l'attesa lunga è su `await()`, che è già interrompibile.

### D9. `BlockingQueueBuffer`: delega a `ArrayBlockingQueue`

**Decisione:** adattatore che inoltra `put`/`take` a una `ArrayBlockingQueue<Item>`.

**Motivazione:** la libreria standard implementa già il buffer limitato con lock e condizioni interne
(come D8); è la soluzione da preferire in produzione. Capacità fissa come gli altri buffer (non `LinkedBlockingQueue`,
che di default è illimitata).

### D10. `IdGenerator`: contatore atomico

**Decisione:**
- Package `producerconsumer.counter`. Un solo metodo `int next()`, implementato con
  `AtomicInteger.getAndIncrement()`.
- Il primo id è 0.
- Lo stesso generatore (sicuro) è usato in tutti gli scenari, compreso quello non sincronizzato.

**Motivazione:**
- D1 rende il contatore una risorsa condivisa: `counter++` su un `int` produrrebbe id duplicati.
- `AtomicInteger` è lock-free e non richiede scelta del monitor.
- Generatore sempre sicuro → persi e duplicati misurati sono attribuibili solo al buffer.
- Partenza da 0: comportamento predefinito di `AtomicInteger`; −1 resta riservato alla poison pill (D4).

### D11. `Producer`: thread con quota condivisa

**Decisione:**
- Package `producerconsumer.actor`. `Producer extends Thread`; nome del thread `producer-<id>`.
- Numero di item non fissato per producer: i producer attingono a una `Quota` condivisa
  (package `producerconsumer.counter`) con un solo metodo `boolean tryAcquire()`, implementato con
  un `AtomicInteger` interno. Capacità negativa → `IllegalArgumentException`; capacità 0 ammessa.
- Ciclo: `tryAcquire()` → `IdGenerator.next()` → `Buffer.put(item)` → incremento di `produced`.
- `InterruptedException` catturata in `run()` attorno all'intero ciclo: il thread termina e
  registra il flag `interrupted`.
- `produced` e `interrupted` sono campi privati scritti solo dal producer, letti dopo `join()`.
- Il producer non inserisce poison pill (→ D12).
- "Prodotti" (D1) = somma dei `produced`, non la quota iniziale né il numero di id generati.

**Motivazione:**
- `extends Thread`: il producer è modellato come processo con proprio ciclo di vita, come nel
  modello del corso. Limite noto: chiamare `run()` al posto di `start()` esegue il codice nel thread
  chiamante senza errori.
- Quota condivisa: distribuzione del carico dinamica, totale fisso indipendente dal numero di producer.
- Classe `Quota` dedicata: il controllo e il decremento devono essere una sola operazione atomica
  (altrimenti check-then-act, come in D5). Incapsulandola, il `Producer` non può scrivere la
  sequenza `get()` + `decrement` separata. `synchronized` sarebbe corretto solo su un monitor condiviso
  e con il blocco limitato al controllo; l'`AtomicInteger` evita entrambe le scelte.
- `try` esterno al ciclo: un `try` attorno al solo `put` farebbe proseguire il ciclo dopo l'interruzione.
- Flag `interrupted`: dopo il timeout (D2) distingue una terminazione regolare da un'interruzione.
  Il ripristino del flag con `Thread.currentThread().interrupt()` non è richiesto: `run()` è il punto
  più esterno del thread.
- `join()` stabilisce happens-before: i campi non richiedono `volatile`.
- `produced++` dopo `put`: un item il cui `put` è stato interrotto ha consumato un permesso e un id
  ma non è entrato nel buffer; contarlo lo farebbe risultare perso dal buffer.

**Conseguenze:**
- Gli id consumati possono avere buchi (id generati ma mai inseriti). Il conteggio dei distinti
  usa un insieme e non presuppone id consecutivi.
- La ripartizione per producer varia tra esecuzioni: si verifica solo il totale.

### D12. Terminazione: poison pill inserite dal coordinatore

**Decisione:** la simulazione attende la fine di tutti i producer con `join()`, poi inserisce nel
buffer una `Item.POISON` per ogni consumer. Né `Producer` né `Consumer` inseriscono pillole.

**Motivazione:**
- Il `join()` garantisce che le pillole entrino nel buffer dopo l'ultimo item reale: nessun consumer
  termina mentre ci sono ancora item in arrivo.
- Il numero di pillole è esattamente il numero di consumer, indipendentemente dal numero di
  producer (anche con più consumer che producer).
- La logica di terminazione è in un solo punto.

**Conseguenze:** nello scenario non sincronizzato una pillola può andare persa; il consumer
che non la riceve viene fermato dal timeout (D2).

### D13. `Consumer`: thread con registro locale degli item consumati

**Decisione:**
- Package `producerconsumer.actor`. `Consumer extends Thread`; nome del thread `consumer-<id>`.
- Ciclo `take()` fino alla ricezione di `Item.POISON`. La pillola non viene registrata e non viene
  reinserita nel buffer.
- Ogni item consumato viene aggiunto a una `List<Item>` privata del consumer, letta dopo `join()`.
- `InterruptedException` catturata in `run()` attorno all'intero ciclo: il thread termina e
  registra il flag `interrupted`.

**Motivazione:**
- Lista locale: nessuna sincronizzazione aggiuntiva durante la simulazione, quindi nessuna
  alterazione dell'interleaving che si misura. `join()` garantisce la visibilità.
- `Item` intero e non solo l'id: conserva il `producerId` degli item duplicati.
- Terminazione e interruzione seguono lo stesso schema di `Producer` (D11).

**Conseguenze:** consumati = somma delle dimensioni delle liste; distinti = dimensione dell'insieme
degli id di tutte le liste.

### D14. Visualizzazione: osservazione dal vivo e rallentamento

**Decisione:**
- Interfaccia `SimulationListener` nel nucleo, con `onPut(String thread, Item item)` e
  `onTake(String thread, Item item)`: producer e consumer notificano ogni operazione riuscita.
  In assenza di osservatori si usa un listener che non fa nulla.
- La TUI implementa il listener inserendo gli eventi in una coda thread-safe
  (`ConcurrentLinkedQueue`), senza stampare. Un solo thread della TUI svuota la coda a intervalli
  regolari, aggiorna la vista e ridisegna.
- Rallentamento: parametro `delayMillis` di `SimulationConfig` (0 = nessuna pausa). Producer e
  consumer eseguono `Thread.sleep(delayMillis)` dopo ogni operazione riuscita, fuori dal buffer.
- `UnsafeBuffer` riceve una pausa `raceMillis` (costruttore) inserita tra la lettura e la
  scrittura dello stato condiviso: `count` è aggiornato in tre passi espliciti (lettura, pausa,
  scrittura), e lo stesso vale per `head` e `tail`. Lo scenario non sincronizzato passa
  `delayMillis` come `raceMillis`; con 0 il comportamento coincide con D7.
- Il listener riceve anche `beforePut(String thread)` e `beforeTake(String thread)`, notificati
  da producer e consumer prima di chiamare il buffer. Stati mostrati dalla TUI: operazione
  appena eseguita, in attesa (dentro il buffer oltre una soglia superiore alla durata normale
  dell'operazione), in pausa.
- Vista dal vivo attiva quando `delayMillis > 0`; con 0 la TUI mostra solo il risultato finale.
- Layout della vista dal vivo: box del buffer (barra di riempimento, prodotti sul totale,
  consumati, tempo trascorso), box dei thread (stato corrente e conteggio, al massimo 10 righe,
  poi "... and N more"), box del log (ultimi 6 eventi). Il riempimento è ricavato dagli eventi
  (put − take); la barra è scalata a 20 caratteri oltre capacità 20.
- Il listener è passato dalla TUI allo scenario: `Scenario.run(SimulationConfig,
  SimulationListener)`. `BufferScenario` lo passa a `Simulation`, che lo passa a producer e
  consumer. Per misure e test si usa il listener che non fa nulla.
- Lo scenario sequenziale notifica gli stessi eventi (a nome di `producer-1` e `consumer-1`) ed
  esegue la stessa pausa dopo ogni operazione.

**Motivazione:**
- Il campionamento dello stato dei thread non dice chi ha fatto cosa e perde gli eventi tra un
  campione e l'altro.
- Un listener che stampa farebbe eseguire I/O al nucleo e scrivere più thread sul terminale
  contemporaneamente.
- Con la coda il nucleo resta senza I/O e un solo thread scrive sullo schermo. I thread della
  simulazione producono eventi, la TUI li consuma.
- Pausa nei thread e fuori dal buffer: i buffer non cambiano e le operazioni restano brevi, solo
  più rare. Una pausa dentro il buffer, con il lock acquisito, renderebbe seriali i buffer
  sicuri; una pausa nel listener sarebbe un effetto nascosto della TUI. `sleep` è interrompibile
  e rispetta il timeout.
- Con la pausa nei thread le operazioni sul buffer (circa 1 µs) quasi non si sovrappongono più:
  lo scenario non sincronizzato rallentato non mostrerebbe errori. La pausa tra lettura e
  scrittura allarga la finestra della race condition fino alla durata della pausa e rende
  esplicita nel codice la non atomicità di lettura, modifica e scrittura.
- Attesa ricavata dagli eventi di inizio: `Thread.getState()` non distingue la pausa
  (`TIMED_WAITING`) dall'attesa sul buffer e l'attesa in polling resta `RUNNABLE`; eventi emessi
  dai buffer richiederebbero di modificarli tutti e non sono possibili in `ArrayBlockingQueue`.
- Un solo insieme di parametri e una sola regola: ritardo 0 per misurare, ritardo positivo per
  osservare.
- Il contenuto reale del buffer non è leggibile dall'esterno: una vista cella per cella,
  ricostruita dagli eventi, mostrerebbe nello scenario non sincronizzato una coda ideale diversa
  da quella reale. Il conteggio put − take indica quanti item dovrebbero essere presenti; le
  discrepanze emergono nel risultato finale.
- Sequenziale animato: la TUI non ha casi speciali; il buffer si riempie e si svuota
  completamente a ogni giro e nessun thread attende, in contrasto con gli scenari concorrenti.

**Vincoli già emersi:**
- un ritardo per operazione riduce la sovrapposizione delle operazioni e quindi le perdite nello
  scenario non sincronizzato: il ritardo deve essere un parametro, con valore 0 per la misura.

### D15. Simulazione: `Scenario`, `Simulation`, configurazione e risultato

**Decisione:**
- Package `producerconsumer.simulation`.
- `Simulation` esegue un run con thread su un `Buffer` dato. Gli scenari (interfaccia `Scenario`,
  da definire) scelgono il buffer e delegano a `Simulation`; il sequenziale è uno scenario con
  logica propria.
- `Simulation` non è un thread: `run()` è eseguito dal thread chiamante, che coordina avvio,
  attese e terminazione. Ogni istanza esegue un solo run; producer, consumer e terminator sono
  creati a ogni chiamata.
- `SimulationConfig`: classe immutabile con producer, consumer, item totali, capacità del buffer,
  timeout. Valida i valori nel costruttore (`IllegalArgumentException`); i limiti sono costanti
  pubbliche:

  | parametro | min | max |
  |---|---|---|
  | producer | 1 | 50 |
  | consumer | 1 | 50 |
  | item totali | 0 | 1.000.000 |
  | capacità | 1 | 1.000 |
  | timeout (ms) | 100 | 60.000 |
  | ritardo (ms) | 0 | 2.000 |

- `SimulationResult`: totali (prodotti, consumati, distinti, persi, duplicati), dettaglio per
  thread (nome, conteggio, interruzione), item duplicati con numero di occorrenze, esito del
  timeout, thread non terminati, durata. Gli item persi sono solo contati.
- Timeout con scadenza unica: ogni attesa del thread chiamante usa il tempo residuo; le pillole
  (D12) sono inserite da un thread dedicato (`terminator`), atteso anch'esso con il tempo residuo.
  Alla scadenza: interruzione dei thread vivi, attesa di un margine fisso (1 s), registrazione dei thread ancora vivi.
- Tutti i thread della simulazione sono daemon.

**Motivazione:**
- Logica dei thread (avvio, `join`, pillole, timeout) in un solo punto; il menu tratta tutti gli
  scenari in modo uniforme.
- Configurazione in un solo oggetto: niente liste di `int` scambiabili; limiti riusabili dalla TUI.
- Calcoli nel nucleo, testabili; la TUI mostra soltanto. I persi non sono elencabili perché
  `Producer` conta gli item ma non li registra.
- Il thread chiamante non esegue mai attese senza limite: un `put` della pillola su buffer pieno,
  con consumer bloccati, lo bloccherebbe per sempre.
- Un thread che non risponde a `interrupt()` resta vivo dopo il margine: i thread daemon non
  impediscono la chiusura della JVM.

**Conseguenze:** un thread che non risponde all'interruzione resta vivo fino all'uscita
dall'applicazione. Il buffer
conserva lo stato tra un run e l'altro e un thread non può essere riavviato: ogni run richiede
un nuovo buffer e una nuova `Simulation`.

### D16. Scenari: interfaccia `Scenario` e implementazioni

**Decisione:**
- Interfaccia `Scenario` con tre metodi:
  - `String getName()`: nome mostrato nel menu;
  - `String getDescription()`: breve spiegazione, in inglese, di cosa mostra lo scenario;
  - `SimulationResult run(SimulationConfig config, SimulationListener listener) throws
    InterruptedException` (listener aggiunto con D14).
- Ogni chiamata a `run` crea un nuovo buffer con la capacità di `config` e una nuova `Simulation`.
- Scenari basati su buffer: classe astratta `BufferScenario implements Scenario` con nome e
  descrizione passati al costruttore, metodo astratto `createBuffer(SimulationConfig)` e `run`
  `final`, che crea il buffer e delega a `Simulation`. Una sottoclasse con nome per scenario.
- Scenario sequenziale: implementa `Scenario` direttamente, senza thread. Il thread chiamante
  alterna due fasi finché la quota non è esaurita: produce fino a riempire `UnsafeBuffer`,
  poi lo svuota. Gli item sono generati con `IdGenerator` e `Quota`. Il risultato contiene un
  producer (`producer-1`) e un consumer (`consumer-1`), nessun timeout. Il
  numero di producer e consumer della configurazione non viene usato.
- Catalogo: classe `Scenarios` non istanziabile con `static List<Scenario> all()`, che
  restituisce i cinque scenari nell'ordine del menu in una lista non modificabile.
- Package `producerconsumer.scenario`: `Scenario`, `BufferScenario`, `Scenarios`,
  `SequentialScenario`, `UnsafeScenario`, `MonitorScenario`, `LockScenario`,
  `BlockingQueueScenario`.

**Motivazione:**
- Il menu elenca e avvia gli scenari senza conoscerne il tipo.
- Nome e spiegazione stanno nella stessa classe: aggiungere uno scenario non richiede modifiche
  alla TUI. Il nucleo restituisce i testi senza stamparli.
- `BufferScenario` (template method): l'avvio di `Simulation` è scritto una volta; `run` `final`
  impedisce alle sottoclassi di riusare un buffer. Ogni sottoclasse dichiara solo nome,
  descrizione e buffer. Sottoclassi con nome anziché anonime: più semplici da trovare e testare.
- Sequenziale: con un solo thread un `put` su buffer pieno resterebbe bloccato per sempre,
  quindi produzione e consumo si alternano. `Simulation` con 1 producer e 1 consumer userebbe
  comunque tre thread. `UnsafeBuffer` corretto senza concorrenza mostra che gli errori dello
  scenario non sincronizzato derivano dall'accesso concorrente, non dalla struttura del buffer.
- Catalogo nel nucleo: elenco e ordine in un solo punto, verificabili con i test; la TUI non
  dipende dalle singole classi.
- Package separato: `simulation` resta il motore e non conosce gli scenari; le dipendenze vanno
  in una sola direzione (`scenario` → `simulation`, `buffer`; TUI → `scenario`). I nomi degli
  scenari seguono quelli dei buffer.

### Test dei buffer

- `BufferTest` (astratta): contratto comune — FIFO, capacità, `null`, wrap-around, attesa su
  pieno/vuoto, interruzione. Ogni buffer ha una sottoclasse che dice solo come crearlo.
- `SynchronizedBufferTest` (astratta, estende `BufferTest`): invariante concorrente per i buffer
  corretti — 4 producer, 3 consumer, ogni item consumato esattamente una volta.
- Risposta alla questione aperta di D3: un test "si blocca" verifica che il thread sia **ancora vivo**
  dopo un'attesa, proprietà vera in qualunque interleaving.
- `UnsafeBuffer` non ha un test di race: le perdite variano molto tra esecuzioni (misurate
  ~400–3500 su 10000 item, anche con 1 producer e 1 consumer). Verrà verificato dalla simulazione,
  asserendo solo "nessuna eccezione". La poison pill stessa può andare persa → il consumer resta in
  attesa e serve il timeout di D2.

## TUI

### D17. TUI: interazione, schermate e stile

**Decisione:**
- Input a righe: l'utente digita una scelta e preme Invio. Ogni schermata pulisce il terminale
  (`ESC[2J`) e viene ridisegnata. Le scorciatoie del footer sono tasti seguiti da Invio
  (es. `1-5 run`, `q quit`).
- Schermate: menu (elenco degli scenari) → dettaglio dello scenario (descrizione e parametri
  correnti) → esecuzione → risultato → ritorno al dettaglio. Dal dettaglio: Invio esegue,
  `p` apre i parametri, `b` torna al menu. I parametri sono condivisi tra tutti gli scenari.
- Parametri: elenco numerato con valore corrente e limiti; l'utente sceglie il numero, poi
  digita il nuovo valore. Valore non valido: messaggio breve in rosso e nuova richiesta dello
  stesso valore. I limiti sono letti dalle costanti di `SimulationConfig`.
- Valori predefiniti, costanti della TUI: 3 producer, 2 consumer, 40 item, capacità 5,
  timeout 30.000 ms, ritardo 200 ms.
- Risultato in una sola schermata: box dei totali (prodotti, consumati, distinti, persi,
  duplicati, durata), box dei thread su più colonne (al massimo 8 righe, poi "... and N more"),
  box dei duplicati (i primi 10, poi "... and N more"), riga di verdetto in verde se ogni item è
  consumato una sola volta, altrimenti in rosso (item persi o duplicati, timeout, thread non
  terminati).
- Esecuzione: con ritardo 0 una riga fissa "Running <scenario>..." in testo secondario fino al
  risultato. Con ritardo positivo, a fine run la vista dal vivo resta ferma sull'ultimo stato
  con una riga di esito ("Done." in verde, "Stopped by timeout." in rosso); Invio apre il
  risultato.
- Larghezza fissa di 60 colonne (costante `WIDTH`), separatore del footer compreso.
- Vista dal vivo: lo scenario è eseguito da un thread dedicato; `main` ripete svuotamento della
  coda e ridisegno, attendendo il thread con `join(100)`, finché il thread è vivo, poi disegna
  l'ultimo stato. Il risultato e un'eventuale eccezione del thread sono letti dopo il `join`.
- Package `producerconsumer.tui`: `App` (`main`, collega `System.in`/`System.out`), `Tui` (ciclo
  delle schermate, un metodo per schermata), `Theme` (colori, `WIDTH`, valori predefiniti),
  `Box` (header, box con titolo, footer), `Parameters` (valori correnti, conversione in
  `SimulationConfig`), `LiveView` (listener con coda, stato dei thread, righe della vista dal
  vivo), `ResultView` (righe della schermata del risultato).

**Motivazione:**
- Java senza librerie legge l'input per righe. La lettura tasto per tasto richiede il raw mode
  (`stty`), non portabile, inaffidabile sotto `mvn exec:java` e difficile da testare; un'uscita
  anomala lascerebbe il terminale senza eco.
- Il ridisegno mantiene la struttura header / corpo / footer del design system.
- Input e output passano da un `Reader` e da un `Writer`: i test sostituiscono il terminale.
- La descrizione è letta prima dell'esecuzione. Il ritorno al dettaglio permette di rieseguire
  lo stesso scenario con un tasto (utile per lo scenario non sincronizzato, che varia a ogni run).
  Parametri condivisi: gli scenari si confrontano a parità di condizioni.
- Modifica di un parametro alla volta con i limiti visibili: nessun nome da ricordare, errore
  circoscritto al valore appena inserito. Una sola fonte dei limiti.
- Valori predefiniti pensati per la vista dal vivo: con più producer che consumer il buffer si
  riempie e si osservano sia l'attesa su buffer vuoto (all'avvio) sia quella su buffer pieno.
  Durata stimata: circa 4 s per i buffer corretti, circa 8 s per quello non sincronizzato
  (pausa interna). Per la misura: ritardo 0, ad esempio 10.000 item con 2 producer e 2 consumer
  (tra 1091 e 3477 item persi nello scenario non sincronizzato).
- Risultato: il verdetto dà l'esito a colpo d'occhio, i box ne mostrano la causa. Il
  troncamento mantiene la schermata entro un'altezza fissa anche con 50 + 50 thread o migliaia
  di duplicati.
- L'ultimo stato della vista dal vivo resta osservabile; ogni schermata attende l'utente. Nessun
  thread di aggiornamento per l'esecuzione senza ritardo, che dura frazioni di secondo o al
  massimo timeout + margine.
- Larghezza fissa: Java non espone la larghezza del terminale; `COLUMNS` di norma non è
  esportata e `stty size` dipende dal sistema ed è fragile sotto `mvn exec:java`. Output
  identico a parità di input, quindi verificabile nei test. 60 colonne sono il minimo usato
  anche in leva-sh.
- `main` è l'unico thread che scrive sul terminale, per costruzione. `join(100)` è insieme
  attesa e cadenza del ridisegno: alla fine del run ritorna subito e non c'è un thread di disegno
  da fermare. Il `join` rende visibili a `main` i dati scritti dal thread.
- Classi per responsabilità: la navigazione si legge in un solo file; la trasformazione di dati
  in righe di testo (`Box`, `LiveView`, `ResultView`) è separata dall'I/O e verificabile senza
  terminale. Una classe per schermata aumenterebbe le classi e richiederebbe di passare lo stato
  condiviso tra le schermate.
