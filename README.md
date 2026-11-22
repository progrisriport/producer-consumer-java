# producer-consumer-java

Didactic simulator of the producer-consumer problem, built for the Concurrent Programming
course at Università degli Studi dell'Insubria.

## Overview

One or more producers generate items and place them in a bounded shared buffer; one or more
consumers remove and process them. The problem is the coordination of access to the buffer:
no overwrite of unread items, no read from an empty buffer, no lost or duplicated items.

The application is a terminal program: a scenario is selected from a menu, run with editable
parameters and its outcome reported numerically (produced, consumed, lost, duplicated), including
for an incorrect synchronization strategy. With a positive delay, the run is shown live: buffer
fill level, state of every thread and the latest operations.

## Run

Requirements: Java 21, Maven, a terminal with UTF-8 and 24-bit colour. Runtime dependencies: none.

```bash
mvn -q compile exec:java
```

Every command is a line confirmed with Enter; the footer of each screen lists the available keys.
During a live run the program can be stopped only with Ctrl+C.

## Scenarios

| # | Scenario | Mechanism | Expected outcome |
|---|---|---|---|
| 1 | Sequential | single thread, fill then drain | every item consumed exactly once |
| 2 | Unsynchronized | `UnsafeBuffer`, no locking | items lost and duplicated, no exceptions |
| 3 | Monitor | `synchronized`, `wait`, `notifyAll` | every item consumed exactly once |
| 4 | Lock and Condition | `ReentrantLock`, two `Condition`s | every item consumed exactly once |
| 5 | Blocking queue | `ArrayBlockingQueue` | every item consumed exactly once |

## Parameters

Shared by all scenarios.

| Parameter | Default | Range |
|---|---|---|
| Producers | 3 | 1 to 50 |
| Consumers | 2 | 1 to 50 |
| Items | 40 | 0 to 1,000,000 |
| Buffer capacity | 5 | 1 to 1,000 |
| Timeout (ms) | 30,000 | 100 to 60,000 |
| Delay (ms) | 200 | 0 to 2,000 |

Delay 0 runs at full speed and shows only the result (for measuring); a positive delay slows
every thread and shows the live view (for observing).

## Build and test

```bash
mvn test
```

178 test executions. Concurrent tests carry a timeout and assert only interleaving-independent
invariants, so the suite is deterministic in outcome.

## Documentation

| Document | Content |
|---|---|
| [docs/GUIDE.md](docs/GUIDE.md) | Usage, scenarios, classes and test suite (English) |
| [docs/design.md](docs/design.md) | Requirements and design decision log D1..Dn (Italian) |

## Repository layout

```
src/main/java/producerconsumer/
  model/Item.java
  buffer/{Buffer, UnsafeBuffer, MonitorBuffer, LockBuffer, BlockingQueueBuffer}.java
  counter/{IdGenerator, Quota}.java
  actor/{Producer, Consumer}.java
  event/SimulationListener.java
  simulation/{SimulationConfig, Simulation, SimulationResult, WorkerReport}.java
  scenario/{Scenario, BufferScenario, *Scenario, Scenarios}.java
  tui/{App, Tui, Theme, Box, Parameters, LiveView, ResultView}.java
src/test/java/producerconsumer/
  tests in the same packages, plus shared helpers (RecordingListener, Ansi)
docs/
```

## License

Released under the [MIT License](LICENSE).
