# ADR-0013 — Osservazione degli eventi: `SimulationListener`, Null Object, coda e ritardo

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [DESIGN-PATTERNS](../DESIGN-PATTERNS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D14.

## Contesto

La TUI deve mostrare dal vivo chi fa cosa, senza che il nucleo faccia I/O e senza che più thread scrivano sul terminale.

## Decisione

- Interfaccia `event.SimulationListener` (package `event`, dipende solo da `model`) con `beforePut(String)`, `onPut(String, Item)`, `beforeTake(String)`, `onTake(String, Item)`. Gli eventi `before` precedono la chiamata al buffer; gli `on` seguono un'operazione riuscita; un'operazione interrotta ha solo `before`. La pillola non genera `on`.
- `SimulationListener.NONE`: istanza anonima che non fa nulla (Null Object), usata per misure e test.
- `tui.LiveView` implementa il listener inserendo eventi con timestamp in una `ConcurrentLinkedQueue`; solo il thread della TUI chiama `drain()` e `lines(...)`: lo stato derivato non è condiviso.
- Rallentamento: `delayMillis` in `SimulationConfig`; producer, consumer e scenario sequenziale eseguono `Thread.sleep(delayMillis)` dopo ogni operazione riuscita, **fuori dal buffer**.
- Lo stato "in attesa" è dedotto dal tempo trascorso dall'ultimo evento `before`: soglia `WAIT_FACTOR × delay` con `WAIT_FACTOR = 3` (nello scenario non sincronizzato un'operazione dura `2 × delay`).
- Vista dal vivo solo con `delayMillis > 0`; con 0 solo il risultato finale.

## Conseguenze

- Il nucleo resta senza I/O; la TUI è l'unico thread di scrittura.
- I metodi del listener sono chiamati in contemporanea da più thread: devono essere thread-safe e brevi.
- Il numero di riempimento è `puts − takes` ricavato dagli eventi: nell'unsafe può non coincidere con il contenuto reale; la barra è limitata a [0, capacità].
- Lo stato "in attesa" è un'euristica temporale, non lo stato reale del thread.

## Alternative scartate

- Campionare `Thread.getState()`: non distingue pausa e attesa sul buffer, il polling resta `RUNNABLE`, e si perdono eventi tra campioni (D14).
- Listener che stampa: I/O nel nucleo e scritture concorrenti sul terminale (D14).
- Pausa dentro il buffer: renderebbe seriali i buffer sicuri (D14).
- Eventi emessi dai buffer: bisognerebbe modificarli tutti e non è possibile in `ArrayBlockingQueue` (D14).
- Vista cella per cella del buffer: nell'unsafe mostrerebbe una coda ideale diversa da quella reale (D14).
