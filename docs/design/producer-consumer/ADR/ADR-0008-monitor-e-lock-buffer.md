# ADR-0008 — `MonitorBuffer` e `LockBuffer`: stessa logica, primitive diverse

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [DESIGN-PATTERNS](../DESIGN-PATTERNS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D8.

## Contesto

Gli scenari 3 e 4 devono confrontare i due meccanismi di sincronizzazione del corso a parità di logica.

## Decisione

- `MonitorBuffer`: `LinkedList<Item>`, metodi `synchronized`, `wait()` in `while`, `notifyAll()` dopo ogni modifica. Una sola coda di attesa per producer e consumer.
- `LockBuffer`: `LinkedList<Item>`, `ReentrantLock`, due `Condition` (`notFull`, `notEmpty`), `await()` in `while`, `signal()` sull'altra condizione, `unlock()` in `finally`. Si usa `lock()` e non `lockInterruptibly()`.

## Conseguenze

- `notifyAll()` è necessario: con una sola coda `notify()` potrebbe svegliare un thread della categoria sbagliata e lasciare dormire chi serve.
- `signal()` basta: su `notEmpty` attendono solo consumer e un `put` aggiunge un solo item.
- L'attesa lunga è su `await()`/`wait()`, già interrompibili: il timeout funziona senza `lockInterruptibly()`.
- Le due classi duplicano la logica della coda (`LinkedList` + controllo capacità): accettato, la differenza didattica sta nelle primitive.

## Alternative scartate

- `notify()` nel monitor: rischio di risveglio sbagliato (D8).
- `unlock()` fuori da `finally`: un'eccezione lascerebbe il lock preso (D8).
- `lockInterruptibly()`: non necessaria (D8).
