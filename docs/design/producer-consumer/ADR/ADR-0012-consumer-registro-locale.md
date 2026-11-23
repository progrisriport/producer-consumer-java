# ADR-0012 — `Consumer` con registro locale degli item

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [DESIGN-IN-THE-SMALL](../DESIGN-IN-THE-SMALL.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D13.

## Contesto

Per calcolare distinti, persi e duplicati servono gli item consumati, senza alterare l'interleaving misurato.

## Decisione

`actor.Consumer extends Thread` (nome `consumer-<id>`). Ciclo `beforeTake` → `take` → (se pillola: `return`) → `consumed.add(item)` → `onTake` → pausa. `consumed` è una `ArrayList<Item>` privata scritta solo dal thread e letta dopo `join()`; esposta come vista non modificabile (`Collections.unmodifiableList`). Terminazione per interruzione come in `Producer` (flag `interrupted`).

## Conseguenze

- Nessuna sincronizzazione aggiuntiva durante il run; la visibilità è garantita da `join()`/`isAlive()`.
- Si conserva l'`Item` intero (con `producerId`), non solo l'id.
- Consumati = somma delle dimensioni delle liste; distinti = insiemi di id.
- Dopo un timeout con thread ancora vivi la lista letta può essere non aggiornata (R-7).

## Alternative scartate

- Lista condivisa e sincronizzata: altera l'interleaving che si vuole misurare (D13).
- Registrare solo l'id: perde il `producerId` dei duplicati (D13).
