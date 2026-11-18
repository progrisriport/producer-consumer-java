# Producer-Consumer didattico — Design

Requisiti e decisioni di progettazione. Ogni decisione ha la sua motivazione.

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
6. **Deadlock** — mostra il deadlock, poi la sua cura.

### Criteri di successo

- I sei scenari si avviano dal menu.
- Gli scenari corretti mostrano prodotti == consumati.
- Lo scenario non sincronizzato mostra elementi persi/duplicati **senza crashare**.
- Il deadlock viene rilevato e poi curato; l'app torna sempre al menu.
- Funziona con qualsiasi numero di producer e consumer (anche più consumer che producer).
- La suite di test passa in modo affidabile (non "a volte").

### Vincoli

- Java 21, Maven, un solo modulo. Package base `producerconsumer`.
- Unica dipendenza: JUnit 5 (solo test). Nessuna dipendenza runtime.
- Codice e identificatori in inglese, testi visibili all'utente in italiano.
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

### D2. Rilevamento dei blocchi: rilevamento deadlock + timeout

**Decisione:** due meccanismi complementari.
1. `ThreadMXBean.findDeadlockedThreads()` per riconoscere un deadlock vero e dirlo all'utente.
2. Un timeout per ogni scenario come rete di sicurezza: scaduto, i thread vengono interrotti.

**Motivazione:** il rilevamento JVM vede solo l'attesa circolare sui lock. Altri blocchi (es.
consumer che attende per sempre un elemento perso nello scenario non sincronizzato) non sono
deadlock e non verrebbero visti. Il timeout garantisce che l'app torni sempre al menu.

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
