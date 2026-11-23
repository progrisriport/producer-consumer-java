# ADR-0010 — `Producer` come thread con `Quota` condivisa

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [DESIGN-IN-THE-SMALL](../DESIGN-IN-THE-SMALL.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D11.

## Contesto

Il totale di item prodotti deve essere fisso e indipendente dal numero di producer; il carico si ripartisce dinamicamente.

## Decisione

- `actor.Producer extends Thread`, nome `producer-<id>`.
- `counter.Quota`: `boolean tryAcquire()` = `remaining.getAndDecrement() > 0` su `AtomicInteger`; capacità negativa → `IllegalArgumentException`, 0 ammessa.
- Ciclo: `tryAcquire()` → `new Item(generator.next(), id)` → `beforePut` → `put` → `produced++` → `onPut` → pausa `delayMillis`.
- `try`/`catch (InterruptedException)` esterno al ciclo; flag `interrupted`; `produced` e `interrupted` scritti solo dal thread, letti dopo `join()` (happens-before, niente `volatile`).
- Non inserisce pillole ([ADR-0011](ADR-0011-terminazione-poison-pill.md)).

## Conseguenze

- Prodotti = somma dei `produced`; gli id hanno buchi se un `put` è interrotto.
- La ripartizione per producer varia tra esecuzioni: si verifica solo il totale.
- Limite noto: chiamare `run()` invece di `start()` esegue il corpo nel thread chiamante senza errori.
- `produced++` dopo il `put`: un put interrotto non conta come prodotto.
- Il contatore `remaining` può scendere sotto zero (ogni valore `<= 0` = esaurita); ogni producer esce al primo `false`, quindi dopo l'esaurimento il contatore scende al massimo del numero di producer (≤ 50) e non raggiunge l'overflow.

## Alternative scartate

- Quota fissa per producer: totale dipendente dal numero di producer, carico non dinamico (D11).
- `get()` + decremento separati o `synchronized` sul controllo: check-then-act, o monitor condiviso da scegliere (D11).
- `try` attorno al solo `put`: il ciclo proseguirebbe dopo l'interruzione (D11).
