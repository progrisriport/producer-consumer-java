# ADR-0017 — Stile di codice classico (vincolo)

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ANALYSIS](../ANALYSIS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato (vincolo del committente, rispettato in tutto il codice e nei test)
**Decisione di origine:** nessuna D numerata in design.md. Tracce indirette: D4 ("forma classica, non `record`"), cronologia git (commit "rewrite tests in classic style"). Lacuna di documentazione: DT-4 in [ANALYSIS](../ANALYSIS.md).

## Contesto

Il codice è materiale di studio: lo studente deve poterlo leggere a partire dai costrutti di base
del linguaggio, senza dover decodificare idiomi funzionali.

## Decisione

In tutto `src/main` e `src/test`: solo `if`, `for`, `while`, `do`, `switch`, `for-each`; niente lambda,
method reference, stream, operatore ternario, `record`; classi anonime al posto delle lambda
(es. `new Runnable() {...}`, `new Comparator<...>() {...}`, `SimulationListener.NONE`).

## Conseguenze

- È un **vincolo**, non un debito: verbosità accettata in cambio di leggibilità didattica.
- Chi estende il progetto (es. un nuovo buffer, un nuovo test) deve rispettarlo: i punti di estensione in [ARCHITECTURE](../ARCHITECTURE.md) lo ricordano.
- API della libreria che restituiscono collezioni immutabili (`List.of`, `List.copyOf`) sono ammesse: non sono costrutti sintattici funzionali.

## Alternative scartate

- Lambda e stream: più concisi, ma meno leggibili per uno studente che deve capire e modificare il codice.
- `record` per `Item`, `WorkerReport`, `SimulationConfig`: scartati per coerenza con la forma classica (D4 per `Item`).
