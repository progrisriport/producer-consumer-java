# ADR-0004 — Test concorrenti: invarianti e timeout, mai ordini

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [TEST-STRATEGY](../TEST-STRATEGY.md) · [ANALYSIS](../ANALYSIS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisioni di origine:** D3 (invarianti e timeout), sezione "Test dei buffer" di design.md (risposta alla questione aperta di D3).

## Contesto

L'ordine dei thread lo decide lo scheduler. Un test che dipende dall'ordine è instabile; un test
concorrente bloccato non deve bloccare la build.

## Decisione

- Ogni test concorrente ha un timeout JUnit (`@Timeout`): un blocco fa fallire il test.
- Si asseriscono invarianti vere in qualunque interleaving (prodotti == consumati, nessun id consumato due volte), mai un ordine.
- Comportamenti che richiedono un ordine (es. "take si blocca su buffer vuoto") si verificano controllando che il thread sia **ancora vivo** dopo un'attesa (`join(200)` + `isAlive()`), poi che riparta dopo l'azione opposta.
- Contratto comune dei buffer in una classe astratta (`BufferTest`), con una sottoclasse per implementazione che dice solo come crearla (Template Method nei test).
- Il difetto dell'`UnsafeBuffer` **non** è asserito: è non deterministico (misurate ~400–3500 perdite su 10.000 item). Si verificano solo contratto a thread singolo e terminazione (`notTerminated` vuoto).

## Conseguenze

- Nessuna asserzione sul fatto che lo scenario 2 *perda* item: la dimostrazione del difetto è affidata all'osservazione manuale.
- I test dei buffer sicuri usano più thread (4 producer, 3 consumer, 5000 item ciascuno): la durata è dominata dallo scheduler, ma limitata dal timeout di 5 s.
- I test della TUI e dei listener usano scenari e listener finti: non dipendono dai tempi dei buffer reali.

## Alternative scartate

- Asserire ordini specifici o `Thread.sleep` come sincronizzazione: instabile (D3).
- Test di race sull'`UnsafeBuffer` con soglia minima di perdite: variabilità troppo ampia, anche con 1 producer e 1 consumer.
