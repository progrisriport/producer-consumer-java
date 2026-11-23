# ADR-0007 — `UnsafeBuffer`: race condition riproducibile senza eccezioni

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [ANALYSIS](../ANALYSIS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisioni di origine:** D7 (struttura), D14 (parametro `raceMillis`).

## Contesto

Lo scenario 2 deve mostrare item persi e duplicati senza che l'applicazione vada in crash e,
con ritardo positivo, senza che la pausa nei thread nasconda la race.

## Decisione

- Array circolare `Item[]` con `head`, `tail`, `count`; nessun lock; `count` è `volatile`, `head`/`tail` no.
- Aggiornamenti in tre passi espliciti: lettura in locale, `racePause()`, scrittura (`count`, `head`, `tail`). `put` scrive la cella *prima* di avanzare `tail`.
- Attesa su pieno/vuoto in polling (`while (count >= length)` / `while (count <= 0)` con `pause()` = controllo esplicito di `Thread.interrupted()` + `Thread.yield()`).
- Guardie con `>=`/`<=`; cella `null` saltata con nuovo tentativo; celle non svuotate dopo `take`.
- `raceMillis` (costruttore, `>= 0`): `Thread.sleep(raceMillis)` tra lettura e scrittura; con 0 nessuna pausa. `UnsafeScenario` passa `delayMillis` come `raceMillis`.

## Conseguenze

- Persi/duplicati senza eccezioni: l'array resta sempre in uno stato valido.
- Anche le pillole possono andare perse → un consumer può restare in polling fino al timeout (R-1). Il polling occupa CPU (R-2).
- Con `delayMillis > 0` un'operazione dura `2 × delay` (due pause interne) oltre alla pausa del thread: da qui `LiveView.WAIT_FACTOR = 3` ([ADR-0013](ADR-0013-osservazione-eventi.md)).
- `SequentialScenario` riusa `UnsafeBuffer` con `raceMillis = 0`: a thread singolo è corretto, quindi gli errori dello scenario 2 derivano dall'accesso concorrente.
- Non esiste test che dimostri il difetto ([ADR-0004](ADR-0004-test-deterministici.md)).

## Alternative scartate

- `LinkedList`: modifiche concorrenti possono corrompere i nodi e lanciare eccezioni (D7).
- `wait()`: richiede il possesso del monitor, quindi sincronizzazione (D7).
- `Thread.yield()` senza controllo dell'interruzione: non lancia `InterruptedException`, il timeout non fermerebbe il thread (D7).
- `count == 0`/`== capacity`: con il contatore fuori scala il consumer non si fermerebbe più (D7).
- Pausa nei buffer sicuri o nel listener: renderebbe seriali i buffer, o introdurrebbe un effetto nascosto della TUI (D14).
