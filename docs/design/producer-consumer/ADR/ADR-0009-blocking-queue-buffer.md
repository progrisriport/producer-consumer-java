# ADR-0009 — `BlockingQueueBuffer`: adattatore su `ArrayBlockingQueue`

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [DESIGN-PATTERNS](../DESIGN-PATTERNS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D9.

## Contesto

Lo scenario 5 mostra la soluzione da preferire in produzione: la struttura della libreria standard.

## Decisione

`BlockingQueueBuffer` inoltra `put`/`take` a una `ArrayBlockingQueue<Item>` di capacità fissa; valida la
capacità nel costruttore; non controlla `null` (lo fa già la coda con `NullPointerException`).

## Conseguenze

- Pattern Adapter: l'interfaccia `Buffer` è adattata a `BlockingQueue`.
- Comportamento uniforme con gli altri buffer su `null`, capacità, interrupt (verificato da `BufferTest`).
- `capacity` è memorizzata a parte perché `BlockingQueue` non espone la capacità totale.

## Alternative scartate

- `LinkedBlockingQueue`: di default illimitata, non coerente con il buffer limitato (D9).
