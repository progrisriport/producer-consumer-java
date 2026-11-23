# ADR-0003 — Timeout come rete di sicurezza, con scadenza unica

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [ANALYSIS](../ANALYSIS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisioni di origine:** D2 (timeout), D15 (scadenza unica, margine, daemon).

## Contesto

Nello scenario non sincronizzato un consumer può attendere per sempre un item (o una pillola) perso.
L'applicazione deve comunque tornare sempre al menu.

## Decisione

- Un timeout per run (`SimulationConfig.timeoutMillis`, 100–60.000 ms, predefinito 30.000).
- `Simulation.run()` calcola **una sola scadenza** (`deadline`); ogni attesa del thread chiamante usa il tempo residuo (`joinAll`); non si usa mai `join(0)`.
- Alla scadenza: `interrupt()` sui thread vivi, attesa di un margine fisso `GRACE_MILLIS = 1000`, registrazione dei thread ancora vivi in `SimulationResult.notTerminated`.
- Tutti i thread della simulazione (producer, consumer, `terminator`) sono daemon.
- Il thread chiamante non esegue mai attese illimitate: l'inserimento delle pillole è delegato al thread `terminator`.

## Conseguenze

- Il run termina entro `timeout + GRACE_MILLIS`, salvo ritardi dello scheduler.
- Un thread che ignora `interrupt()` resta vivo fino all'uscita della JVM (daemon): è riportato nel risultato e mostrato in rosso dalla TUI.
- Il timeout non è applicato a `SequentialScenario` (un solo thread, nessun blocco possibile; vedi [ADR-0015](ADR-0015-scenari.md)).
- Item ancora nel buffer allo scadere contano come "persi" (prodotti − distinti): vedi rischio R-6 in [ANALYSIS](../ANALYSIS.md).
- Se il thread chiamante viene interrotto durante `joinAll`, `run()` esce con `InterruptedException` senza interrompere i worker: rischio R-5.

## Alternative scartate

- Rilevamento esplicito di deadlock: rimosso (commit `2614f01` "remove deadlock detection"); il timeout è più semplice e copre anche le pillole perse.
- Timeout per singola `join`: i tempi si sommerebbero e il totale supererebbe il timeout configurato (D15).
- Inserimento delle pillole dal thread chiamante: un `put` su buffer pieno con consumer bloccati lo bloccherebbe per sempre (D15).
