# ADR-0011 — Terminazione con poison pill inserite dal coordinatore

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [ANALYSIS](../ANALYSIS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D12 (con D13 per il lato consumer).

## Contesto

I consumer devono terminare dopo l'ultimo item reale, con un numero di producer e consumer qualsiasi.

## Decisione

`Simulation` attende tutti i producer con `join` (limitato dalla scadenza), poi avvia il thread
`terminator` che inserisce nel buffer `Item.POISON` una volta per consumer (`putPoisonPills`).
Né `Producer` né `Consumer` inseriscono pillole. Il consumer che riceve la pillola esce senza registrarla né reinserirla.

## Conseguenze

- Le pillole entrano dopo l'ultimo item reale: nessun consumer termina con item ancora in arrivo.
- Numero di pillole = numero di consumer, indipendentemente dal numero di producer.
- Logica di terminazione in un solo punto.
- Le pillole non generano eventi per il listener; il consumer che la riceve ha però emesso `beforeTake` (un `beforeTake` in più per consumer: verificato in `SimulationTest.notifiesEveryOperation`).
- Con `UnsafeBuffer` una pillola può andare persa: il consumer resta in attesa e fa scattare il timeout ([ADR-0003](ADR-0003-timeout-scadenza-unica.md)); un `put` della pillola può anche sovrascrivere un item non letto.

## Alternative scartate

- Pillole inserite dai producer o dai consumer: scartate da D12 (la terminazione sta in un solo punto). Il motivo "il numero di pillole dipenderebbe dal numero di producer" è l'implicazione ricavata da D12, non una frase esplicita.
- Pillola inserita dal thread chiamante: attesa illimitata possibile (D15).
