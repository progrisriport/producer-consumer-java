# Producer-Consumer in Java: Usage, Scenarios, Classes, Tests

Technical reference for the producer-consumer simulator. Design rationale is recorded in
[`design.md`](design.md) (Italian); decision identifiers (D1..Dn) below refer to that file.

Requirements: Java 21, Maven. No runtime dependencies (JUnit 5 for tests only).

## Contents

1. [Problem](#1-problem)
2. [Using the application](#2-using-the-application)
3. [Scenarios](#3-scenarios)
4. [Architecture](#4-architecture)
5. [Classes](#5-classes)
6. [Termination and timeout](#6-termination-and-timeout)
7. [Tests](#7-tests)
8. [Maintenance of this document](#8-maintenance-of-this-document)

## 1. Problem

Producers create items and put them into a shared bounded buffer; consumers take items out.
Two properties must hold:

- A producer waits when the buffer is full; a consumer waits when it is empty.
- Every produced item is consumed exactly once.

Without synchronization, thread interleaving is unpredictable and both properties can be violated.

Result metrics (D1), computed from all consumed items:

- lost = produced − distinct consumed
- duplicated = consumed − distinct consumed

## 2. Using the application

```bash
mvn -q compile exec:java
```

The terminal must support UTF-8 (box-drawing borders) and 24-bit colour. Input is line based:
every command is a line confirmed with Enter (D17). Every screen has the same structure: header,
body, one message line, footer with the available keys, prompt `>`.

### 2.1 Screens

| Screen | Content | Keys |
|---|---|---|
| Menu | numbered list of the scenarios | `1`–`5` open, `q` quit |
| Detail | scenario description and current parameters | Enter run, `p` parameters, `b` back |
| Parameters | numbered parameters with value and range | `1`–`6` edit, `b` back |
| Live view | buffer, threads and log boxes, redrawn every 100 ms | Ctrl+C quit |
| Result | totals, threads, duplicates, verdict | Enter back to the detail |

Editing a parameter: choose its number, then type the new value. An invalid value shows a short
message in red and the value is asked again; an empty line cancels the edit. Parameters are shared
by all scenarios, so scenarios can be compared under the same conditions.

### 2.2 Parameters

| # | Parameter | Default | Range | Meaning |
|---|---|---|---|---|
| 1 | Producers | 3 | 1 to 50 | Number of producer threads |
| 2 | Consumers | 2 | 1 to 50 | Number of consumer threads |
| 3 | Items | 40 | 0 to 1,000,000 | Total items produced in the run, shared by all producers |
| 4 | Buffer capacity | 5 | 1 to 1,000 | Maximum number of items the buffer holds at the same time |
| 5 | Timeout (ms) | 30,000 | 100 to 60,000 | Maximum duration of the whole run; when it expires the run is stopped |
| 6 | Delay (ms) | 200 | 0 to 2,000 | Pause of every producer and consumer after each successful operation; also selects live view or result only |

Ranges are the constants of `SimulationConfig`; defaults are constants of `Theme`. The values are
shared by all scenarios. The Sequential scenario uses only Items, Buffer capacity and Delay
(section 3.1).

**Producers and Consumers.** Each producer is a thread named `producer-N`, each consumer a thread
named `consumer-N`. All of them share one buffer.

**Items.** A single quota of items is shared by all producers: each producer asks for a permit
before producing an item, and the run produces exactly Items items in total. Faster producers
produce more. With 0 the producers stop immediately and the consumers only receive the poison
pills.

**Buffer capacity.** A `put` on a full buffer waits until a consumer takes an item; a `take` on an
empty buffer waits until a producer puts one. A small capacity makes these waits frequent and
visible in the live view.

**Timeout.** Safety limit on the duration of the run, measured from its start with a single
deadline (it does not restart between phases). It exists because a run can fail to terminate: in
the Unsynchronized scenario a poison pill can be overwritten, and the consumer that misses it would
wait forever. When the deadline expires:

1. every thread still alive is interrupted;
2. the run waits at most 1 more second (`Simulation.GRACE_MILLIS`) for the threads to exit;
3. the result is built from what was done so far and the verdict reports "stopped by timeout";
   interrupted threads are marked with `*`.

A run therefore lasts at most about Timeout + 1 s. The timeout can also expire in a correct
scenario when the delay makes the run too long: for example Monitor with 40 items, 1 consumer and
delay 2,000 ms needs about 80 s, more than the default 30 s. The Sequential scenario does not apply
the timeout. Implementation details are in section 6.

**Delay.** Pause that every producer and consumer makes after each successful `put` or `take`,
outside the buffer: it slows the run down without changing the synchronization. The value also
selects what the screen shows:

- **Delay above 0** (observing): the run is shown live. At the end the last frame stays on screen
  with "Done." (green) or "Stopped by timeout." (red); Enter opens the result.
- **Delay 0** (measuring): the run is executed at full speed; the screen shows "Running ..." and
  then the result.

The delay exists for the simulation, not for the problem: without it a run of 40 items ends in a
few milliseconds and nothing can be followed on screen. With a positive delay the elapsed time is
almost entirely made of pauses. With the defaults each consumer takes about 20 items and pauses
200 ms after each one, about 4 s in total, while a synchronized `put` or `take` costs a few
microseconds. Monitor, Lock and Condition and Blocking queue therefore show the same elapsed time:
in this mode the time does not compare the strategies.

With delay 0 the elapsed time measures the cost of the synchronization itself and gives an
indication of which solution is more efficient. Useful settings: a large number of items (for
example 1,000,000), then different capacities (for example 5 and 100) and different numbers of
producers and consumers, since the ranking of the strategies changes with them. Lock and Condition
and Blocking queue have the same structure (one lock, two conditions) and give similar times.
The values depend on the machine and vary between runs: repeat each run a few times and compare
the ranges, not single values. The application is not a benchmark tool: it measures one run at a
time, without JVM warm-up.

In the Unsynchronized scenario the delay has a second effect: it is also the pause inside `put` and
`take` of `UnsafeBuffer`, between reading and writing `count`, `head` and `tail` (`raceMillis`,
section 3.2). A longer delay widens the race window and produces more lost and duplicated items.

With the defaults, the correct scenarios take about 4 s, the unsynchronized one about 9 s and the
sequential one about 16 s (one thread, a pause after every put and every take).

### 2.3 Live view

- **Buffer**: fill bar (one cell per slot, scaled to 20 cells above capacity 20), produced over
  total, consumed, elapsed time. The fill level is computed from the events (puts − takes): in the
  unsynchronized scenario it can leave the range [0, capacity], so the bar is clamped while the
  number is shown as is.
- **Threads**: one row per thread (at most 10, then "... and N more"), producers first, in numeric
  order. States, derived from the last event of the thread:

  | State | Meaning |
  |---|---|
  | `putting` / `taking` | inside the buffer operation |
  | `waiting: buffer full` / `waiting: buffer empty` | inside the operation for more than 3× the delay |
  | `put #id` / `took #id` | operation just completed (first half of the pause) |
  | `resting` | pausing after the operation |
  | `done` | shown in the last frame of a run that was not stopped by the timeout |

- **Log**: the last 6 completed operations.

### 2.4 Result

- **Totals**, titled with the scenario name: produced, consumed, distinct, lost, duplicated, elapsed.
- **Threads**: count per thread on two columns (at most 8 rows, then "... and N more"); `*` marks
  an interrupted thread; threads still alive after the timeout are listed in red.
- **Duplicates**, only when present: the first 10 by id, as `#id xN`, then "... and N more".
- **Verdict**: "Every item consumed exactly once." in green, otherwise the causes in red
  (lost, duplicated, stopped by timeout, threads still running).

## 3. Scenarios

| # | Scenario | Mechanism | Expected outcome |
|---|---|---|---|
| 1 | Sequential | single thread, fill then drain, `UnsafeBuffer` | every item consumed exactly once |
| 2 | Unsynchronized | `UnsafeBuffer`, no locking | items lost and duplicated, no exceptions |
| 3 | Monitor | `MonitorBuffer`: `synchronized`, `wait`, `notifyAll` | every item consumed exactly once |
| 4 | Lock and Condition | `LockBuffer`: `ReentrantLock`, two `Condition`s | every item consumed exactly once |
| 5 | Blocking queue | `BlockingQueueBuffer`: `ArrayBlockingQueue` | every item consumed exactly once |

### 3.1 Sequential

Baseline without concurrency (D16). The calling thread alternates two phases until the quota is
exhausted: it fills the buffer, then drains it. With a single thread a `put` on a full buffer would
wait forever, so the phases alternate instead. The configured numbers of producers and consumers are
not used: the result reports `producer-1` and `consumer-1`. The timeout is not applied.

The buffer is the same `UnsafeBuffer` as scenario 2: without concurrent access it neither loses nor
duplicates items, so the errors of scenario 2 come from concurrency, not from the data structure.

### 3.2 Unsynchronized

Demonstrates the absence of mutual exclusion. `count`, `head` and `tail` are updated in three
separate steps (read, modify, write), which are not atomic. Two threads may read the same value
and both write it plus one; one update is lost. The counter diverges from the real content:

- a cell is overwritten before being read: the item is **lost**;
- a cell is read again: the item is **duplicated**.

Measured with 10,000 items and delay 0:

| Producers / consumers / capacity | Lost | Duplicated |
|---|---|---|
| 2 / 2 / 5 | 1091 to 3477 | 1036 to 3193 |
| 1 / 1 / 5 | 386 | 4089 |

With a positive delay the scenario passes the delay to the buffer as `raceMillis`: a pause between
the read and the write of each shared field. Without it, the operations (about 1 µs) would almost
never overlap when slowed down and no error would appear (D14). With the defaults (40 items,
delay 200 ms) a run typically loses and duplicates a few tens of items.

Results are scheduler-dependent and vary between runs. The poison pill can itself be lost,
leaving a consumer waiting indefinitely; the timeout terminates the run (section 6).
The scenario is designed to yield wrong results **without throwing exceptions** (D7).

### 3.3 Monitor

Intrinsic lock of the object: `synchronized` methods, `wait()` when the buffer is full or empty,
`notifyAll()` after each change. Producers and consumers share one wait set; `notify()` could
wake a thread of the wrong kind and leave the required one asleep (D8).

### 3.4 Lock and Condition

Same logic as the monitor with explicit primitives: a `ReentrantLock` and two conditions,
`notFull` (producers wait) and `notEmpty` (consumers wait). Each wait set holds one kind of thread,
so `signal()` suffices. `unlock()` is always invoked in `finally` (D8).

### 3.5 Blocking queue

Delegation to `java.util.concurrent.ArrayBlockingQueue`, which implements the same
lock-and-condition scheme internally. Preferred solution in production code (D9).

## 4. Architecture

Base package: `producerconsumer`. Dependencies go in one direction only:

```
tui ──► scenario ──► simulation ──► actor ──► buffer, counter, model
 │          │            │            │
 └──────────┴────────────┴────────────┴──► event ──► model
```

| Package | Responsibility |
|---|---|
| `model` | the exchanged item and the poison pill |
| `buffer` | the four buffer implementations behind one interface |
| `counter` | shared id generator and production quota |
| `actor` | producer and consumer threads |
| `event` | listener interface for observing operations |
| `simulation` | configuration, execution of a concurrent run, result |
| `scenario` | the five scenarios and their catalog |
| `tui` | terminal interface; the only package that performs I/O |

## 5. Classes

### 5.1 `model.Item` (D4)

Immutable item exchanged between producers and consumers.

| Member | Description |
|---|---|
| `Item(int id, int producerId)` | constructor |
| `getId()` | unique id from a global counter shared by all producers (D1) |
| `getProducerId()` | id of the producing producer |
| `POISON` | constant item (`id = -1`, `producerId = -1`) signaling end of production |
| `isPoison()` | identity comparison with `POISON` (not by value) |

- `final` class, `private final` fields: shareable between threads without synchronization.
- Written as a classic class, not a `record`: `equals`, `hashCode`, `toString` and getters are explicit.
- The poison pill is a named constant instead of `null`: standard blocking queues reject `null`.

### 5.2 `buffer.Buffer` (D5, D6)

```java
void put(Item item) throws InterruptedException;  // waits while full; NullPointerException on null
Item take() throws InterruptedException;          // waits while empty; never returns null
int capacity();                                   // fixed at construction
```

- `InterruptedException` is declared so that the timeout can interrupt a blocked thread.
- No `size()` / `isEmpty()`: they invite check-then-act races. End of production is signaled
  by the poison pill, not by inspecting the buffer.
- Common rules: `put(null)` throws `NullPointerException`; capacity `<= 0` throws
  `IllegalArgumentException`; waiting always occurs in a `while` loop, never an `if`.

### 5.3 `buffer.UnsafeBuffer` (D7, D14)

Deliberately not thread-safe. Circular array `Item[]` with `head`, `tail`, `count`; no lock.

- `UnsafeBuffer(int capacity, long raceMillis)`: `count`, `head` and `tail` are each updated by
  reading into a local variable, pausing `raceMillis`, writing. With 0 there is no pause and the
  update is equivalent to `count++`.
- `count` is `volatile`: ensures visibility, not atomicity.
- Waiting by polling with `Thread.yield()`, since `wait()` requires holding a monitor;
  `Thread.interrupted()` is checked explicitly.
- Guards use `>=` and `<=` instead of `==`: once `count` drifts below zero, `== 0` would no longer
  stop consumers.
- Array instead of `LinkedList`: a list modified concurrently may corrupt its nodes and throw,
  whereas the array always remains in a valid state. Cells are not cleared after `take`, so an
  already-read cell can be read again; a never-written cell (`null`) is skipped.

### 5.4 `buffer.MonitorBuffer`, `buffer.LockBuffer`, `buffer.BlockingQueueBuffer` (D8, D9)

| Buffer | Synchronization | Wait sets | Correct |
|---|---|---|---|
| `UnsafeBuffer` | none (`volatile` counter, polling) | none | no |
| `MonitorBuffer` | `synchronized`, `wait`, `notifyAll` | one | yes |
| `LockBuffer` | `ReentrantLock`, `notFull` / `notEmpty` | two | yes |
| `BlockingQueueBuffer` | `ArrayBlockingQueue` internals | two (internal) | yes |

`LockBuffer` uses `lock()` rather than `lockInterruptibly()`: the long wait occurs in `await()`,
which is already interruptible. `BlockingQueueBuffer` uses a fixed capacity, as the other buffers.

### 5.5 `counter.IdGenerator`, `counter.Quota` (D10, D11)

- `IdGenerator.next()`: `AtomicInteger.getAndIncrement()`, first id 0. The same safe generator is
  used in every scenario, so lost and duplicated items are attributable only to the buffer.
- `Quota.tryAcquire()`: atomic check-and-decrement of the remaining items to produce. Producers
  draw from one shared quota: the total is fixed, the distribution among producers is dynamic.

### 5.6 `actor.Producer`, `actor.Consumer` (D11, D13, D14)

Both extend `Thread`, named `producer-<id>` / `consumer-<id>`, and take a `SimulationListener`
and a `delayMillis`.

| | Producer | Consumer |
|---|---|---|
| Loop | quota → id → `beforePut` → `put` → count → `onPut` → pause | `beforeTake` → `take` → (pill: exit) → record → `onTake` → pause |
| Result | `getProduced()` | `getConsumed()` (read-only list) |
| Interruption | in `put` or in the pause: thread ends, `wasInterrupted()` true | in `take` or in the pause: same |

- The pause is `Thread.sleep(delayMillis)` outside the buffer, skipped when the delay is 0.
- Results are private fields written only by the thread and read after `join()`, which
  guarantees visibility.
- The producer counts an item only after a successful `put`. The consumer does not record the
  pill and does not put it back.

### 5.7 `event.SimulationListener` (D14)

```java
void beforePut(String thread);
void onPut(String thread, Item item);
void beforeTake(String thread);
void onTake(String thread, Item item);
SimulationListener NONE;  // ignores every event
```

- `before*` is notified before calling the buffer, `on*` after a successful operation; an
  interrupted operation has only `before*`.
- The poison pill generates no events: the terminator's puts are not notified and the take of the
  pill has only `beforeTake`.
- Methods are called concurrently by the simulation threads: implementations must be thread-safe
  and short.

### 5.8 `simulation.SimulationConfig` (D15)

Immutable: producers, consumers, items, capacity, timeout, delay. Values are validated in the
constructor (`IllegalArgumentException` naming the parameter and the range); limits are public
constants (`MIN_*`, `MAX_*`), reused by the interface.

### 5.9 `simulation.Simulation` (D12, D15)

`new Simulation(buffer, config, listener).run()` executes one concurrent run on the calling
thread. The class is not a thread; one instance runs once.

1. Consumers are started, then producers. All threads are daemon.
2. The calling thread waits for the producers, then a dedicated `terminator` thread puts one
   poison pill per consumer, then the consumers are awaited.
3. Every wait uses the time remaining before a single deadline (section 6).

### 5.10 `simulation.SimulationResult`, `simulation.WorkerReport` (D15)

`WorkerReport`: name, count, interrupted flag of one thread. `SimulationResult`: reports of
producers and consumers, consumed items, timeout flag, names of threads still alive, elapsed
time. It computes produced, consumed, distinct, lost, duplicated and the map of duplicated items
with their number of occurrences. Lost items are only counted.

### 5.11 `scenario` (D16)

- `Scenario`: `getName()`, `getDescription()`, `run(SimulationConfig, SimulationListener)`.
- `BufferScenario` (abstract, template method): `run` is `final`, creates a new buffer with
  `createBuffer(config)` and delegates to `Simulation`. Subclasses: `UnsafeScenario`,
  `MonitorScenario`, `LockScenario`, `BlockingQueueScenario`.
- `SequentialScenario`: implements `Scenario` directly (section 3.1); notifies the same events and
  applies the same pause.
- `Scenarios.all()`: the five scenarios in menu order, in an unmodifiable list.

### 5.12 `tui` (D17)

| Class | Responsibility |
|---|---|
| `App` | `main`: connects the interface to `System.in` / `System.out` in UTF-8 |
| `Tui` | screen loop, one method per screen; runs the scenario on a dedicated `runner` thread |
| `Theme` | colours (24-bit ANSI), `WIDTH` 60, app name and tagline, parameter defaults |
| `Box` | header, box with title in the top border, footer, wrapping, visible length |
| `Parameters` | current parameter values, conversion to `SimulationConfig` |
| `LiveView` | listener queueing events; derives thread states and the live view lines |
| `ResultView` | lines of the result screen and the verdict |

- Input and output go through an injected `Reader` and `Writer`, replaced in the tests.
- `LiveView` listener methods only add events to a `ConcurrentLinkedQueue`. The interface thread
  drains the queue, redraws, and waits for the runner with `join(100)`, which is both the wait and
  the refresh rate. It is the only thread that writes to the terminal.
- The result is read after the runner is no longer alive, which makes its fields visible.
- Fixed width of 60 columns: Java does not expose the terminal width, and fixed output can be
  verified in tests.

## 6. Termination and timeout

(D2, D12, D15)

- Normal end: producers finish the quota, the terminator puts one pill per consumer, every
  consumer ends on its pill.
- Timeout: a single deadline covers the whole run. When it expires, every live thread is
  interrupted and awaited for a fixed grace period (1 s); threads still alive are reported in the
  result. A thread that ignores interruption stays alive until the application exits, which daemon
  threads do not prevent.
- The calling thread never waits without a limit, so the application always returns to the menu.

## 7. Tests

Run with `mvn test`. Current suite: 178 test executions (inherited and parameterized tests count
once per execution).

| Class | Scope |
|---|---|
| `ItemTest` | equality and poison pill |
| `BufferTest` (abstract) | contract common to all buffers: FIFO order, capacity, `null`, wrap-around, waiting when full or empty, interruption |
| `SynchronizedBufferTest` (abstract) | correct buffers: 4 producers, 3 consumers, each item consumed exactly once |
| `UnsafeBufferTest`, `UnsafeBufferRaceTest` | contract without and with the internal pause |
| `IdGeneratorTest`, `QuotaTest` | unique ids and exact quota under concurrency |
| `ProducerTest`, `ConsumerTest` | loops, events, pause, interruption while blocked or pausing |
| `SimulationConfigTest`, `SimulationResultTest` | validation and limits; metrics |
| `SimulationTest` | correct buffers, timeout, thread ignoring interruption, events, delay |
| `BufferScenarioTest`, `SequentialScenarioTest`, `ScenariosTest` | buffers created, results, events, catalog |
| `BoxTest`, `ParametersTest`, `LiveViewTest`, `ResultViewTest` | lines and widths of every view, states, truncation, verdict |
| `TuiTest` | complete sessions with scripted input and fake scenarios |
| `SmokeTest` | build and test infrastructure check |

Shared test helpers: `event.RecordingListener` (records events as strings) and `tui.Ansi`
(removes ANSI sequences, checks line width).

Principles (D3):

- Every concurrent test has a timeout: a block fails the test instead of hanging the build.
- Tests assert invariants valid under any interleaving, never a specific thread order.
- "Blocks when full or empty" is verified by checking that the thread is still alive after a wait.
- Timing tests assert only a lower bound (for example, three pauses of 50 ms take at least 150 ms).
- `UnsafeBuffer` has no race test: losses vary widely between runs. The scenario test verifies only
  that the run returns.

## 8. Maintenance of this document

When a component changes: update its subsection in section 5, the tables in sections 2 and 3 if
the behaviour visible to the user changes, and the test count in section 7 and in the README.
