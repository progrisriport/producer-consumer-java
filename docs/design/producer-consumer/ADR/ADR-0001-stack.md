# ADR-0001 — Stack tecnologico

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [ANALYSIS](../ANALYSIS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato (stack imposto dal committente, già implementato)
**Decisione di origine:** `docs/design.md`, sezione "Requisiti > Vincoli" e D17 (TUI senza librerie).

## Contesto

Applicazione didattica a terminale per un corso di programmazione concorrente. Lo studente deve
poter leggere e modificare il codice da solo: lo stack deve quindi essere minimale e senza
magia di framework. Lo stack è imposto, non raccomandato: questo ADR lo documenta e ne elenca i vincoli.

## Decisione

| Aspetto | Scelta | Versione pinnata (`pom.xml`) |
|---|---|---|
| Linguaggio | Java, `maven.compiler.release=21` | JDK 21 (verificato: OpenJDK 21.0.11) |
| Build | Maven, un solo modulo, package base `producerconsumer` | compiler-plugin 3.13.0, surefire 3.5.2, exec-maven-plugin 3.5.0 |
| Test | JUnit 5 (`junit-jupiter`, scope `test`) | 5.11.4 |
| Dipendenze runtime | nessuna | n/a |
| TUI | Java puro, codici ANSI truecolor, input a righe da `System.in` | n/a |
| Avvio | `mvn -q compile exec:java` (main class `producerconsumer.tui.App`) | n/a |

Vincoli che ne derivano:

- Il nucleo (`model`, `buffer`, `counter`, `event`, `actor`, `simulation`, `scenario`) non stampa
  nulla; l'I/O vive solo in `tui`.
- Stile di codice imposto (vedi [ADR-0017](ADR-0017-stile-codice-classico.md)).
- Codice e identificatori in inglese; testi visibili all'utente in inglese semplice; Javadoc e
  commenti in italiano impersonale.
- Il vecchio progetto Eclipse resta recuperabile dal tag git `v0-eclipse-tui`.

**Nota sulla versione di Java.** Il brief di questa sessione indicava Java 17; `pom.xml`, `README.md`,
`docs/GUIDE.md` e `docs/design.md` indicano concordemente Java 21. Fa fede il codice: 21.
Il sorgente non usa API successive a Java 17 (usa `List.of`, `List.copyOf`, nessun `record`,
nessun pattern matching): abbassare `release` a 17 è probabilmente possibile ma non verificato.

## Conseguenze

- Riproducibilità: versioni dei plugin e di JUnit pinnate; la versione del JDK è vincolata solo da
  `release=21` (nessun `maven-enforcer-plugin`, nessun `mvnw`, nessun `.tool-versions`): vedi debito DT-1 in
  [ANALYSIS](../ANALYSIS.md).
- JUnit 5.11.4 è la linea precedente: JUnit 6 (GA 30/09/2025, baseline Java 17) è la linea corrente
  ([release notes](https://docs.junit.org/6.0.0/release-notes/)). Migrazione non urgente.
- Un terminale senza UTF-8 o senza truecolor degrada la leggibilità (nessuno sfondo esplicito: resta leggibile).

## Alternative (non documentate in design.md, indicate come contesto)

| Opzione | Trade-off |
|---|---|
| Librerie TUI (JLine, Lanterna) | Input a tasto singolo e larghezza reale del terminale, ma una dipendenza runtime e API da studiare in più: contrario al vincolo "nessuna dipendenza runtime" |
| Gradle al posto di Maven | Equivalente per il progetto; non motivata in design.md |
| Java 17 | Baseline di JUnit 6; compatibile con il sorgente salvo verifica; non scelta dal progetto |
