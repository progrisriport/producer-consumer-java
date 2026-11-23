# ADR-0002 — Identificazione degli item: contatore globale atomico

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [ANALYSIS](../ANALYSIS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisioni di origine:** D1 (contatore globale), D10 (`IdGenerator` atomico).

## Contesto

Per misurare item persi e duplicati ogni item deve essere distinguibile. Il contatore degli id è
condiviso tra i producer: è a sua volta una risorsa concorrente.

## Decisione

- Un solo `IdGenerator` per run (`counter.IdGenerator`), condiviso da tutti i producer; `int next()`
  con `AtomicInteger.getAndIncrement()`; primo id 0.
- Lo stesso generatore sicuro è usato in tutti gli scenari, compreso quello non sincronizzato.
- Metriche derivate (in `SimulationResult`): persi = prodotti − distinti; duplicati = consumati − distinti.
- L'id −1 è riservato alla poison pill ([ADR-0005](ADR-0005-item-e-poison-pill.md)).

## Conseguenze

- Gli id consumati possono avere buchi (id generato, put interrotto): i distinti si contano con un
  insieme, senza presupporre id consecutivi.
- Persi e duplicati sono attribuibili solo al buffer, mai al generatore.
- Limite: `int` basta per i volumi didattici (`MAX_ITEMS` = 1.000.000).

## Alternative scartate

- Contatore per producer: due producer generano lo stesso id e un duplicato diventa indistinguibile da due item diversi (D1).
- `int` con `counter++` o `synchronized`: id duplicati, oppure scelta di un monitor non necessaria; `AtomicInteger` è lock-free (D10).
