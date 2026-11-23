# ADR-0016 — TUI: input a righe, schermate ridisegnate, larghezza fissa, truecolor

> Progetto: producer-consumer · Data: 2026-11-23 · Versione: 1.0 · Stato del documento: reviewed
> Documenti: [PRD](../PRD.md) · [ARCHITECTURE](../ARCHITECTURE.md) · [BEHAVIOR](../BEHAVIOR.md) · [DESIGN-PATTERNS](../DESIGN-PATTERNS.md) · [DESIGN-STATUS](../DESIGN-STATUS.md)

**Stato:** accettato, implementato
**Decisione di origine:** D17 (con D14 per la vista dal vivo).

## Contesto

Interfaccia a terminale in Java puro, testabile senza terminale, con il design system personale
(accento `#E8B84B`, testo `#D4D4D4`, secondario `#7A7A7A`, bordi `#3A3A3A`, errore `#E85B4B`, successo `#4BE87A`).

## Decisione

- Input a righe (`BufferedReader` su un `Reader`), output su un `Writer`: i test sostituiscono il terminale. Fine dell'input = tornare indietro fino all'uscita.
- Schermate: menu → dettaglio → (parametri) → esecuzione → (vista dal vivo) → risultato → dettaglio. Ogni schermata pulisce e ridisegna (`ESC[H ESC[2J`): header, corpo, messaggio, footer con scorciatoie, prompt.
- Classi per responsabilità: `App` (main), `Tui` (navigazione, un metodo per schermata), `Theme` (colori, `WIDTH = 60`, default, sequenze ANSI), `Box` (header/box/footer/formattazione), `Parameters` (valori correnti → `SimulationConfig`), `LiveView`, `ResultView`.
- Esecuzione: `Runner` (classe annidata privata di `Tui`, `extends Thread`) esegue lo scenario; il thread della TUI ripete `drain` + ridisegno con `runner.join(100)` finché il runner è vivo. Il risultato e l'eventuale eccezione sono letti dopo il `join`.
- Colori truecolor solo sul testo, nessuno sfondo; sequenze solo in `Theme`.
- Valori predefiniti: 3 producer, 2 consumer, 40 item, capacità 5, timeout 30.000 ms, ritardo 200 ms.

## Conseguenze

- Output identico a parità di input: verificabile nei test (`Ansi.strip`, larghezza di ogni riga).
- Il thread della TUI è l'unico che scrive; non esiste un thread di disegno da fermare.
- `Runner` non è daemon e non è interrompibile dalla TUI: un run lungo si ferma solo con Ctrl+C (R-3).
- Input solo a righe (Invio dopo ogni comando); nessuna larghezza adattiva.
- design.md non cita `Runner` (classe annidata) e indica `ESC[2J` senza il posizionamento `ESC[H`: divergenze minori.

## Alternative scartate

- Raw mode / tasto singolo (`stty`): non portabile, inaffidabile sotto `mvn exec:java`, difficile da testare, rischio di lasciare il terminale senza eco (D17).
- Larghezza dal terminale (`COLUMNS`, `stty size`): non esportata / fragile (D17).
- Una classe per schermata: più classi e stato condiviso da passare (D17).
- 256 colori: la palette non sarebbe esatta; il cambio resta confinato in `Theme` (D17).
- Thread di aggiornamento per il run senza ritardo: dura frazioni di secondo o al più timeout + margine (D17).
