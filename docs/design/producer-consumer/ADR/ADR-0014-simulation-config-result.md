# ADR-0014 — `Simulation`, `SimulationConfig`, `SimulationResult`

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [DESIGN-IN-THE-SMALL](../DESIGN-IN-THE-SMALL.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D15 (con D2 per il timeout, D12 per le pillole).

## Contesto

Avvio, `join`, pillole e timeout vanno scritti una volta sola; il menu deve trattare gli scenari in modo uniforme.

## Decisione

- `simulation.Simulation(Buffer, SimulationConfig, SimulationListener)`: non è un thread; `run()` gira nel thread chiamante e coordina. Un'istanza = un run; producer, consumer e `terminator` sono creati a ogni chiamata. Ordine di avvio: consumer, producer; poi `join` dei producer, avvio del `terminator`, `join` del `terminator`, `join` dei consumer.
- `SimulationConfig`: immutabile; validazione nel costruttore (`IllegalArgumentException` con nome del parametro e intervallo); limiti come costanti pubbliche (producer 1–50, consumer 1–50, item 0–1.000.000, capacità 1–1.000, timeout 100–60.000 ms, ritardo 0–2.000 ms).
- `SimulationResult`: immutabile; contiene `WorkerReport` per producer e consumer (nome, conteggio, interruzione), totali, duplicati con occorrenze, `timedOut`, `notTerminated`, durata. Persi solo contati: i producer non registrano gli item.
- `WorkerReport`: value object introdotto dal codice (design.md parla di "dettaglio per thread" senza nominarlo).

## Conseguenze

- Il buffer conserva lo stato: ogni run richiede un nuovo buffer e una nuova `Simulation` (garantito da `BufferScenario.run`).
- I limiti sono riusati dalla TUI (`Parameters`): fonte unica dei valori, ma la verifica dell'intervallo è duplicata in `Parameters.set` (DT-3).
- I calcoli (distinti, persi, duplicati) stanno nel nucleo e sono testabili; la TUI mostra soltanto.
- Il thread chiamante non attende mai senza limite.

## Alternative scartate

- Liste di `int` scambiabili al posto di un oggetto di configurazione (D15).
- `Simulation` come `Thread`: il coordinatore usa già il thread chiamante, un thread in più non serve (implicazione da D15).
- Elencare gli item persi: impossibile, i producer li contano ma non li registrano (D15).
