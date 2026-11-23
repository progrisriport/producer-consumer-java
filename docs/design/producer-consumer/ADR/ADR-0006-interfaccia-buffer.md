# ADR-0006 — Interfaccia `Buffer` minima, interrompibile, con regole comuni

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [DESIGN-IN-THE-SMALL](../DESIGN-IN-THE-SMALL.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisioni di origine:** D5 (interfaccia), D6 (regole comuni).

## Contesto

Quattro strategie di sincronizzazione devono essere intercambiabili dietro la stessa interfaccia,
senza permettere al chiamante di scrivere codice soggetto a check-then-act.

## Decisione

- `buffer.Buffer`: tre metodi: `void put(Item) throws InterruptedException`, `Item take() throws InterruptedException`, `int capacity()`.
- Nessun `size()` / `isEmpty()`; nessun generic; `take()` non restituisce mai `null`.
- Regole comuni a tutte le implementazioni: `put(null)` → `NullPointerException` (`if` esplicito, tranne `BlockingQueueBuffer` che delega); capacità `<= 0` → `IllegalArgumentException` nel costruttore; l'attesa su pieno/vuoto è sempre in un `while`, mai in un `if`.

## Conseguenze

- `InterruptedException` risale fino a `run()` di producer/consumer: è il meccanismo con cui il timeout ([ADR-0003](ADR-0003-timeout-scadenza-unica.md)) ferma i thread bloccati.
- La fine della simulazione è segnalata dalla pillola, non da un controllo sul buffer.
- La validazione (NPE, capacità) è ripetuta in ogni implementazione: duplicazione voluta, minima (debito DT-8, trascurabile).
- Estensibilità: un nuovo buffer implementa tre metodi e riusa `BufferTest` (vedi [ARCHITECTURE](../ARCHITECTURE.md), punto di estensione E-1).

## Alternative scartate

- `size()`/`isEmpty()`: invitano a `if (!isEmpty()) take()`; tra controllo e azione lo stato cambia (D5).
- Buffer generico `Buffer<T>`: complessità senza beneficio con un solo tipo di elemento (D5).
- `take()` che restituisce `null` se vuoto: obbligherebbe il consumer a distinguere "vuoto" da "item" (D5).
- `if` al posto di `while`: risvegli spuri e thread concorrenti già arrivati (D6).
