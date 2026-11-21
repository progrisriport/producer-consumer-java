# producer-consumer-java

Didactic simulator of the producer-consumer problem, built for the Concurrent Programming
course at Università degli Studi dell'Insubria.

## Overview

One or more producers generate items and place them in a bounded shared buffer; one or more
consumers remove and process them. The problem is the coordination of access to the buffer:
no overwrite of unread items, no read from an empty buffer, no lost or duplicated items.

The target application is a terminal program in which a scenario is selected from a menu, executed,
and its outcome reported numerically (produced, consumed, lost, duplicated), including for
incorrect synchronization strategies.

## Project status

| Component | Status |
|---|---|
| `Item` (with poison pill) | implemented, tested |
| `Buffer` interface | implemented, tested |
| `UnsafeBuffer`, `MonitorBuffer`, `LockBuffer`, `BlockingQueueBuffer` | implemented, tested |
| `IdGenerator`, `Producer`, `Consumer` | planned (design in progress) |
| Simulation, scenarios, terminal UI | planned |

The terminal application does not exist yet: `mvn compile exec:java` is configured for the
future entry point `producerconsumer.tui.App` and cannot be run at this stage.

## Scenarios

| # | Scenario | Mechanism | Expected outcome |
|---|---|---|---|
| 1 | Sequential | single thread | produced == consumed |
| 2 | Unsynchronized | `UnsafeBuffer`, no locking | items lost and duplicated, no exceptions |
| 3 | Monitor | `synchronized`, `wait`, `notifyAll` | produced == consumed |
| 4 | Lock and Condition | `ReentrantLock`, two `Condition`s | produced == consumed |
| 5 | Blocking queue | `ArrayBlockingQueue` | produced == consumed |

The buffers used by scenarios 2 to 5 are implemented; the scenarios themselves are planned.

## Build and test

Requirements: Java 21, Maven. Runtime dependencies: none (JUnit 5 for tests only).

```bash
mvn test
```

Concurrent tests carry a timeout and assert only interleaving-independent invariants, so the
suite is deterministic in outcome.

## Documentation

| Document | Content |
|---|---|
| [docs/GUIDE.md](docs/GUIDE.md) | Scenarios, classes and test suite (English) |
| [docs/design.md](docs/design.md) | Requirements and design decision log D1..Dn (Italian) |

## Repository layout

```
src/main/java/producerconsumer/
  model/Item.java
  buffer/{Buffer, UnsafeBuffer, MonitorBuffer, LockBuffer, BlockingQueueBuffer}.java
src/test/java/producerconsumer/
  model/ItemTest.java
  buffer/{BufferTest, SynchronizedBufferTest, *BufferTest}.java
docs/
```

## License

Released under the [MIT License](LICENSE).
