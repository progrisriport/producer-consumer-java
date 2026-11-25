# ADR-0005 — `Item` immutabile e poison pill come costante

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [DESIGN-IN-THE-SMALL](../DESIGN-IN-THE-SMALL.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D4.

## Contesto

Gli item passano tra thread senza copia; serve un segnale di fine flusso che non sia `null`
(le code bloccanti della libreria lo rifiutano).

## Decisione

- `model.Item`: classe `final`, campi `private final int id` e `int producerId`, nessun setter, `equals`/`hashCode`/`toString` espliciti (non un `record`).
- `Item.POISON = new Item(-1, -1)`; `isPoison()` confronta **per identità** (`this == POISON`).

## Conseguenze

- Immutabile: condivisibile tra thread senza sincronizzazione.
- `producerId` permette di mostrare da quale producer proviene un duplicato.
- **Asimmetria nota:** `new Item(-1, -1).equals(Item.POISON)` è `true`, ma `new Item(-1, -1).isPoison()` è `false`. Il costruttore pubblico non impedisce id negativi. Con il codice attuale nessun producer genera −1 (gli id partono da 0), quindi il problema è latente: rischio R-8 in [ANALYSIS](../ANALYSIS.md).
- design.md non menziona il confronto per identità (V-6 in [ANALYSIS](../ANALYSIS.md)).

## Alternative scartate

- `null` come pillola: rifiutato da `ArrayBlockingQueue`, e privo di significato (D4).
- `record`: scelta stilistica del progetto, forma classica con metodi espliciti (D4; vedi [ADR-0017](ADR-0017-stile-codice-classico.md)).
