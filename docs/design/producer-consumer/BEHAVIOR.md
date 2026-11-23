# BEHAVIOR — producer-consumer

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato: reviewed
> Documenti: [PRD](PRD.md) · [ARCHITECTURE](ARCHITECTURE.md) · [DESIGN-PATTERNS](DESIGN-PATTERNS.md) · [DESIGN-IN-THE-SMALL](DESIGN-IN-THE-SMALL.md) · [TEST-STRATEGY](TEST-STRATEGY.md) · [ANALYSIS](ANALYSIS.md) · [ADR](ADR/ADR-0001-stack.md) · [DESIGN-STATUS](DESIGN-STATUS.md)

Vista dinamica ricostruita dal codice: sequence diagram, state diagram e activity diagram.
La struttura statica (package, classi) è in [ARCHITECTURE](ARCHITECTURE.md).

Indice: §1 sequence di un run completo · §2 sequence della terminazione per timeout ·
§3 state diagram dei thread · §4 state diagram della `Simulation` · §5 state diagram delle schermate della TUI ·
§6 activity diagram di `put`/`take` per ciascun buffer · §7 activity diagram dello scenario sequenziale.

## 1. Sequence: run completo con vista dal vivo

Scenario di esempio: `MonitorScenario`, ritardo > 0, N producer, M consumer. TUI → Runner → Scenario →
Simulation → Producer/Consumer → Buffer → LiveView. Per brevità `Q` = `Quota` e `G` = `IdGenerator`.

```mermaid
sequenceDiagram
    autonumber
    actor U as Utente
    participant T as Tui (thread main)
    participant R as Runner (thread)
    participant S as BufferScenario
    participant Sim as Simulation
    participant P as Producer (xN)
    participant C as Consumer (xM)
    participant B as Buffer
    participant L as LiveView
    participant X as terminator
    participant Q as Quota
    participant G as IdGenerator

    U->>T: Invio dal dettaglio dello scenario
    T->>T: Parameters.toConfig()
    T->>L: new LiveView(capacity, items, delay, now)
    T->>R: new Runner(scenario, config, L)
    T->>R: start()
    activate R
    R->>S: run(config, L)
    S->>S: createBuffer(config)
    S->>Sim: new Simulation(buffer, config, L)
    S->>Sim: run()
    Sim->>Sim: deadline = now + timeout
    Sim->>C: start() per ogni consumer
    Sim->>P: start() per ogni producer

    par thread dei producer
        loop finche Q concede permessi
            P->>Q: tryAcquire()
            Q-->>P: true
            P->>G: next()
            G-->>P: id
            P->>L: beforePut(name)
            P->>B: put(item)
            P->>L: onPut(name, item)
            P->>P: sleep(delay)
        end
    and thread dei consumer
        loop finche take() non restituisce POISON
            C->>L: beforeTake(name)
            C->>B: take()
            B-->>C: item
            C->>L: onTake(name, item)
            C->>C: sleep(delay)
        end
    and ridisegno nel thread main
        loop finche Runner e vivo
            T->>L: drain()
            T->>T: draw(L.lines(now, false))
            T->>R: join(100)
        end
    end

    Sim->>P: join(tempo residuo)
    Sim->>X: start()
    loop una volta per consumer
        X->>B: put(POISON)
    end
    Sim->>X: join(tempo residuo)
    Sim->>C: join(tempo residuo)
    Sim->>Sim: buildResult(...)
    Sim-->>S: SimulationResult
    S-->>R: SimulationResult
    deactivate R
    R-->>T: terminazione del thread (join ritorna)
    T->>R: getResult()
    T->>L: drain()
    T->>U: ultima vista dal vivo + "Done."
    U->>T: Invio
    T->>U: ResultView.lines + verdetto
    U->>T: Invio (ritorno al dettaglio)
```

Note:

- I tre blocchi `par` sono concorrenti; i `put`/`take` sono sincronizzati dal `Buffer` scelto.
- Con ritardo 0 la TUI non passa `LiveView` (usa `SimulationListener.NONE`), non esegue `drain`/`draw` nel ciclo
  e mostra una sola riga "Running <scenario>...".
- Le pillole inserite da `X` non generano eventi; il consumer che le riceve ha emesso `beforeTake`.
- `Runner.result` è letto solo dopo che `isAlive()`/`join` hanno osservato la terminazione del thread: è ciò che garantisce la visibilità.

## 2. Sequence: terminazione per timeout

Scenario di esempio: `UnsafeScenario`, 2 consumer. Nel buffer non sincronizzato una pillola va persa
(aggiornamento perso di `count`): `consumer-2` resta in polling su `count <= 0` fino alla scadenza.

```mermaid
sequenceDiagram
    autonumber
    participant T as Tui (thread main)
    participant R as Runner (thread)
    participant Sim as Simulation
    participant P as Producer
    participant X as terminator
    participant C1 as consumer-1
    participant C2 as consumer-2
    participant B as UnsafeBuffer

    T->>R: start(), poi join(100) in ciclo
    R->>Sim: run()
    Sim->>Sim: deadline = start + timeout
    Sim->>P: join(tempo residuo)
    Note over P,B: i producer finiscono la quota
    Sim->>X: start()
    X->>B: put(POISON)
    X->>B: put(POISON)
    Note over B: put del terminator e take dei consumer aggiornano count in modo concorrente: un aggiornamento va perso e una pillola non viene mai vista
    C1->>B: take()
    B-->>C1: POISON
    Note over C1: termina regolarmente
    C2->>B: take()
    Note over C2,B: while count <= 0: pause() = yield (polling)
    Sim->>X: join(tempo residuo)
    Sim->>C1: join(tempo residuo)
    Sim->>C2: join(tempo residuo)
    Note over Sim: la scadenza passa: joinAll ritorna false
    Sim->>C2: interrupt()
    C2->>B: pause(): Thread.interrupted() vero
    B-->>C2: InterruptedException
    Note over C2: run() cattura l'eccezione: interrupted = true, il thread termina
    Sim->>Sim: joinAll(tutti i thread, GRACE_MILLIS = 1000)
    Sim->>Sim: notTerminated = nomi dei thread ancora vivi (vuoto)
    Sim->>Sim: buildResult(timedOut = true)
    Sim-->>R: SimulationResult
    R-->>T: join ritorna
    T->>T: draw(vista finale, "Stopped by timeout." in rosso)
    T->>T: draw(ResultView, verdetto in rosso)
```

Se un thread ignora `interrupt()` resta vivo oltre il margine: finisce in `notTerminated` e la TUI mostra
"Still running: ..." in rosso. I thread sono daemon e non impediscono la chiusura della JVM
([ADR-0003](ADR/ADR-0003-timeout-scadenza-unica.md)). Il timeout può scattare in qualsiasi attesa del thread
chiamante (producer, terminator o consumer); se scatta prima dell'avvio del `terminator`, questo non parte mai.

## 3. State diagram dei thread `Producer` e `Consumer`

### 3.1 `Producer`

```mermaid
stateDiagram-v2
    [*] --> New : new Producer(...)
    New --> AcquiringPermit : start()
    AcquiringPermit --> Creating : Quota.tryAcquire() vero
    AcquiringPermit --> Completed : Quota.tryAcquire() falso
    Creating --> BeforePut : Item(generator.next(), id)
    BeforePut --> InBuffer : listener.beforePut()
    InBuffer --> Counted : buffer.put() ritorna
    InBuffer --> Interrupted : InterruptedException
    Counted --> Resting : produced++, listener.onPut()
    Resting --> AcquiringPermit : sleep(delay) concluso, oppure delay 0
    Resting --> Interrupted : InterruptedException in sleep
    Completed --> [*]
    Interrupted --> [*]

    note right of InBuffer
        Thread.State: RUNNABLE nel put,
        BLOCKED su un monitor occupato,
        WAITING se il buffer e pieno
        (wait, await, put bloccante),
        RUNNABLE in polling (UnsafeBuffer)
    end note
    note right of Resting
        Thread.State: TIMED_WAITING
        (Thread.sleep fuori dal buffer)
    end note
    note right of Interrupted
        wasInterrupted() = true
        produced non include il put interrotto
    end note
```

### 3.2 `Consumer`

```mermaid
stateDiagram-v2
    state CheckItem <<choice>>
    [*] --> New : new Consumer(...)
    New --> BeforeTake : start()
    BeforeTake --> InBuffer : listener.beforeTake()
    InBuffer --> CheckItem : buffer.take() ritorna
    InBuffer --> Interrupted : InterruptedException
    CheckItem --> Completed : item.isPoison() (return)
    CheckItem --> Recorded : item reale
    Recorded --> Resting : consumed.add(item), listener.onTake()
    Resting --> BeforeTake : sleep(delay) concluso, oppure delay 0
    Resting --> Interrupted : InterruptedException in sleep
    Completed --> [*]
    Interrupted --> [*]

    note right of InBuffer
        Thread.State: WAITING se il buffer e vuoto
        (wait, await, take bloccante),
        RUNNABLE in polling (UnsafeBuffer)
    end note
    note right of Completed
        la pillola non e registrata
        ne reinserita nel buffer
    end note
```

Entrambi i thread scrivono i propri campi (`produced`, `consumed`, `interrupted`) senza sincronizzazione:
la lettura avviene dopo `join()`/`isAlive() == false`, che stabilisce l'happens-before
([ADR-0010](ADR/ADR-0010-producer-quota.md), [ADR-0012](ADR/ADR-0012-consumer-registro-locale.md)).
Dopo un timeout con thread ancora vivi i valori possono non essere aggiornati (R-7).

## 4. State diagram della `Simulation`

```mermaid
stateDiagram-v2
    [*] --> Starting : run()
    Starting --> AwaitingProducers : start() di consumer e producer
    AwaitingProducers --> PuttingPills : producer tutti terminati
    AwaitingProducers --> Interrupting : scadenza raggiunta
    PuttingPills --> AwaitingConsumers : terminator terminato
    PuttingPills --> Interrupting : scadenza raggiunta
    AwaitingConsumers --> Building : consumer tutti terminati
    AwaitingConsumers --> Interrupting : scadenza raggiunta
    Interrupting --> Grace : interrupt() sui thread vivi
    Grace --> Building : joinAll con GRACE_MILLIS, poi nomi dei vivi
    Building --> [*] : SimulationResult (timedOut vero se si e passati da Interrupting)
```

## 5. State diagram delle schermate della TUI

```mermaid
stateDiagram-v2
    [*] --> Menu
    Menu --> Menu : numero non valido (messaggio rosso)
    Menu --> Detail : numero da 1 a 5
    Menu --> Exit : q oppure fine dell'input
    Detail --> Detail : comando sconosciuto (messaggio rosso)
    Detail --> Parameters : p
    Detail --> Running : Invio
    Detail --> Menu : b oppure fine dell'input
    Parameters --> Parameters : numero non valido (messaggio rosso)
    Parameters --> EditValue : numero da 1 a 6
    Parameters --> Detail : b oppure fine dell'input
    EditValue --> EditValue : valore non valido (nuova richiesta)
    EditValue --> Parameters : valore valido
    EditValue --> Parameters : riga vuota (annulla)
    Running --> Detail : run fallito (messaggio "Run failed")
    Running --> LiveFinal : run riuscito, ritardo maggiore di 0
    Running --> Result : run riuscito, ritardo 0
    LiveFinal --> Result : Invio
    Result --> Detail : Invio
    Exit --> [*]
```

Dettagli:

- `Running` ridisegna ogni 100 ms con ritardo maggiore di 0; con ritardo 0 mostra una sola riga "Running <scenario>...".
- `LiveFinal` è l'ultima vista dal vivo con "Done." (verde) o "Stopped by timeout." (rosso).
- Ogni schermata attende una riga dall'utente; la fine dell'input equivale a "indietro".
- Il footer di `Running` indica "Ctrl+C quit": non esiste un comando per annullare un run (R-3).

## 6. Activity diagram di `put` e `take` per ciascun buffer

Legenda: i rombi sono decisioni; i nodi rosa nell'`UnsafeBuffer` sono le finestre della race
condition (lettura e scrittura non atomiche); `racePause` è `Thread.sleep(raceMillis)` se `raceMillis > 0`.

### 6.1 `UnsafeBuffer.put`

```mermaid
flowchart TD
    start(["put(item)"]) --> chkNull{"item == null?"}
    chkNull -- "si" --> npe[/"throw NullPointerException"/]
    chkNull -- "no" --> full{"count >= capacity?"}
    full -- "si" --> pause["pause(): se Thread.interrupted() lancia InterruptedException, altrimenti yield()"]
    pause --> full
    full -- "no" --> write["items[tail] = item"]
    write --> readTail["oldTail = tail"]
    readTail --> rp1["racePause()"]
    rp1 --> writeTail["tail = (oldTail + 1) mod capacity"]
    writeTail --> readCount["oldCount = count"]
    readCount --> rp2["racePause()"]
    rp2 --> writeCount["count = oldCount + 1"]
    writeCount --> done(["return"])

    classDef race fill:#fdd,stroke:#c33
    class readTail,rp1,readCount,rp2 race
```

### 6.2 `UnsafeBuffer.take`

```mermaid
flowchart TD
    start(["take()"]) --> empty{"count <= 0?"}
    empty -- "si" --> pause["pause(): se Thread.interrupted() lancia InterruptedException, altrimenti yield()"]
    pause --> empty
    empty -- "no" --> read["item = items[head]"]
    read --> isNull{"item != null?"}
    isNull -- "no (cella mai scritta)" --> pause2["pause()"]
    pause2 --> empty
    isNull -- "si" --> readHead["oldHead = head"]
    readHead --> rp1["racePause()"]
    rp1 --> writeHead["head = (oldHead + 1) mod capacity"]
    writeHead --> readCount["oldCount = count"]
    readCount --> rp2["racePause()"]
    rp2 --> writeCount["count = oldCount - 1"]
    writeCount --> ret(["return item (la cella non viene svuotata)"])

    classDef race fill:#fdd,stroke:#c33
    class readHead,rp1,readCount,rp2 race
```

Effetti della race: due `put` che leggono lo stesso `tail` scrivono nella stessa cella (un item perso);
un `count` più alto del reale fa rileggere una cella già letta (duplicato); un `count` più basso lascia item non letti.

### 6.3 `MonitorBuffer.put`

```mermaid
flowchart TD
    start(["put(item)"]) --> enter["entra nel monitor (synchronized): può restare BLOCKED"]
    enter --> chkNull{"item == null?"}
    chkNull -- "si" --> npe[/"throw NullPointerException (il monitor viene rilasciato)"/]
    chkNull -- "no" --> full{"queue.size() == capacity?"}
    full -- "si" --> wait["wait(): rilascia il monitor e attende notifyAll"]
    wait -- "InterruptedException" --> ex[/"propaga l'eccezione (monitor rilasciato)"/]
    wait -- "risveglio" --> full
    full -- "no" --> add["queue.addLast(item)"]
    add --> notify["notifyAll()"]
    notify --> leave(["esce dal monitor, return"])
```

### 6.4 `MonitorBuffer.take`

```mermaid
flowchart TD
    start(["take()"]) --> enter["entra nel monitor (synchronized)"]
    enter --> empty{"queue.isEmpty()?"}
    empty -- "si" --> wait["wait(): rilascia il monitor e attende notifyAll"]
    wait -- "InterruptedException" --> ex[/"propaga l'eccezione (monitor rilasciato)"/]
    wait -- "risveglio" --> empty
    empty -- "no" --> rem["item = queue.removeFirst()"]
    rem --> notify["notifyAll()"]
    notify --> leave(["esce dal monitor, return item"])
```

### 6.5 `LockBuffer.put`

```mermaid
flowchart TD
    start(["put(item)"]) --> chkNull{"item == null?"}
    chkNull -- "si" --> npe[/"throw NullPointerException (prima del lock)"/]
    chkNull -- "no" --> lock["lock.lock()"]
    lock --> full{"queue.size() == capacity?"}
    full -- "si" --> await["notFull.await(): rilascia il lock e attende signal"]
    await -- "InterruptedException" --> fin
    await -- "risveglio (lock riacquisito)" --> full
    full -- "no" --> add["queue.addLast(item)"]
    add --> signal["notEmpty.signal()"]
    signal --> fin["finally: lock.unlock()"]
    fin --> done(["return oppure propaga l'eccezione"])
```

### 6.6 `LockBuffer.take`

```mermaid
flowchart TD
    start(["take()"]) --> lock["lock.lock()"]
    lock --> empty{"queue.isEmpty()?"}
    empty -- "si" --> await["notEmpty.await(): rilascia il lock e attende signal"]
    await -- "InterruptedException" --> fin
    await -- "risveglio (lock riacquisito)" --> empty
    empty -- "no" --> rem["item = queue.removeFirst()"]
    rem --> signal["notFull.signal()"]
    signal --> fin["finally: lock.unlock()"]
    fin --> done(["return item oppure propaga l'eccezione"])
```

### 6.7 `BlockingQueueBuffer.put`

`BlockingQueueBuffer.put` inoltra a `ArrayBlockingQueue.put`. Il diagramma descrive il comportamento della
classe JDK (OpenJDK 21, schema equivalente a `LockBuffer`), non codice del progetto.

```mermaid
flowchart TD
    start(["BlockingQueueBuffer.put(item)"]) --> delegate["queue.put(item)"]
    delegate --> chkNull{"item == null?"}
    chkNull -- "si" --> npe[/"throw NullPointerException"/]
    chkNull -- "no" --> lock["lockInterruptibly()"]
    lock -- "interrotto" --> ex[/"InterruptedException"/]
    lock --> full{"count == items.length?"}
    full -- "si" --> await["notFull.await()"]
    await -- "InterruptedException" --> fin
    await -- "risveglio" --> full
    full -- "no" --> enq["enqueue(item) + notEmpty.signal()"]
    enq --> fin["finally: unlock()"]
    fin --> done(["return oppure propaga l'eccezione"])
```

### 6.8 `BlockingQueueBuffer.take`

```mermaid
flowchart TD
    start(["BlockingQueueBuffer.take()"]) --> delegate["queue.take()"]
    delegate --> lock["lockInterruptibly()"]
    lock -- "interrotto" --> ex[/"InterruptedException"/]
    lock --> empty{"count == 0?"}
    empty -- "si" --> await["notEmpty.await()"]
    await -- "InterruptedException" --> fin
    await -- "risveglio" --> empty
    empty -- "no" --> deq["dequeue() + notFull.signal()"]
    deq --> fin["finally: unlock()"]
    fin --> done(["return item oppure propaga l'eccezione"])
```

## 7. Activity diagram dello scenario sequenziale

`SequentialScenario.run` esegue tutto nel thread chiamante, con `UnsafeBuffer(capacity, 0)`.

```mermaid
flowchart TD
    start(["run(config, listener)"]) --> init["buffer, IdGenerator, Quota(items), consumed = [], produced = 0"]
    init --> outer{"exhausted?"}
    outer -- "si" --> result["SimulationResult con producer-1 e consumer-1, timedOut = false"]
    outer -- "no" --> reset["inBuffer = 0"]
    reset --> fill{"non exhausted e inBuffer < capacity?"}
    fill -- "si" --> acq{"quota.tryAcquire()?"}
    acq -- "si" --> put["beforePut, buffer.put, produced++, inBuffer++, onPut, sleep(delay)"]
    put --> fill
    acq -- "no" --> ex["exhausted = true"]
    ex --> fill
    fill -- "no" --> drain{"i < inBuffer?"}
    drain -- "si" --> take["beforeTake, buffer.take, consumed.add, onTake, sleep(delay)"]
    take --> drain
    drain -- "no" --> outer
```
