# ADR-0015 — Scenari: `Scenario`, `BufferScenario`, catalogo, sequenziale

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [DESIGN-PATTERNS](../DESIGN-PATTERNS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D16.

## Contesto

Il menu deve elencare e avviare cinque scenari senza conoscerne il tipo; aggiungerne uno non deve toccare la TUI.

## Decisione

- Interfaccia `Scenario`: `getName()`, `getDescription()`, `run(SimulationConfig, SimulationListener) throws InterruptedException`.
- `BufferScenario` (astratta): nome e descrizione dal costruttore, `protected abstract Buffer createBuffer(SimulationConfig)`, `run` e getter `final`; `run` crea il buffer e delega a `Simulation`. Sottoclassi con nome: `UnsafeScenario`, `MonitorScenario`, `LockScenario`, `BlockingQueueScenario`.
- `SequentialScenario` implementa `Scenario` direttamente: un solo thread (il chiamante) alterna riempimento fino alla capacità e svuotamento, su `UnsafeBuffer(capacity, 0)`; item da `IdGenerator` e `Quota`; riporta `producer-1` e `consumer-1`; non usa numero di producer/consumer né il timeout.
- `Scenarios.all()`: catalogo non istanziabile, lista non modificabile nell'ordine del menu (Sequential, Unsynchronized, Monitor, Lock and Condition, Blocking queue).

## Conseguenze

- Nuovo scenario basato su buffer = una sottoclasse di `BufferScenario` + una riga in `Scenarios.all()` (vedi punto di estensione E-2 in [ARCHITECTURE](../ARCHITECTURE.md)).
- Dipendenze in un solo senso: `scenario` → `simulation`, `buffer`, `counter`, `event`, `model`; `tui` → `scenario`.
- `SequentialScenario` sceglie `UnsafeBuffer` per mostrare che gli errori dello scenario 2 derivano dalla concorrenza, non dalla struttura.
- Il testo di design.md D15 "Scenario, da definire" è obsoleto (l'interfaccia esiste).

## Alternative scartate

- Sequenziale tramite `Simulation` con 1+1 thread: userebbe comunque tre thread (D16).
- Sottoclassi anonime: sottoclassi con nome sono più semplici da trovare e testare (D16).
- Catalogo nella TUI: elenco e ordine non verificabili con i test del nucleo (D16).
- Sequenziale con `put` su buffer pieno a thread singolo: attesa infinita (D16).
