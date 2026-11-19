# Producer-Consumer in Java: Scenarios, Classes, Tests

Technical reference for the producer-consumer simulator. Design rationale is recorded in
[`design.md`](design.md) (Italian); decision identifiers (D1..Dn) below refer to that file.

Requirements: Java 21, Maven. No runtime dependencies (JUnit 5 for tests only).

**Status legend:** `done` = implemented and tested. `planned` = not yet implemented.

## Contents

1. [Problem](#1-problem)
2. [Scenarios](#2-scenarios)
3. [Implemented classes](#3-implemented-classes)
4. [Planned components](#4-planned-components)
5. [Blocking detection](#5-blocking-detection)
6. [Tests](#6-tests)
7. [Maintenance of this document](#7-maintenance-of-this-document)

## 1. Problem

Producers create items and put them into a shared bounded buffer; consumers take items out.
Two properties must hold:

- A producer waits when the buffer is full; a consumer waits when it is empty.
- Every produced item is consumed exactly once.

Without synchronization, thread interleaving is unpredictable and both properties can be violated.

## 2. Scenarios

| # | Scenario | Mechanism | Expected outcome | Status |
|---|---|---|---|---|
| 1 | Sequential | single thread | produced == consumed | planned |
| 2 | Unsynchronized | `UnsafeBuffer`, no locking | items lost and duplicated | buffer done, scenario planned |
| 3 | Monitor | `MonitorBuffer`: `synchronized`, `wait`, `notifyAll` | produced == consumed | buffer done, scenario planned |
| 4 | Lock and Condition | `LockBuffer`: `ReentrantLock`, two `Condition`s | produced == consumed | buffer done, scenario planned |
| 5 | Blocking queue | `BlockingQueueBuffer`: `ArrayBlockingQueue` | produced == consumed | buffer done, scenario planned |
| 6 | Deadlock | circular wait on locks, then a fix | deadlock detected, then resolved | planned |

### 2.1 Sequential

Baseline without concurrency; reference against which concurrent scenarios are compared.

### 2.2 Unsynchronized

Demonstrates the absence of mutual exclusion. The shared counter `count` is updated with
`count++` / `count--`, which are not atomic (read, modify, write). Two threads may read the same
value and both write `count ± 1`; one update is lost. The counter diverges from the real content:

- a cell is overwritten before being read: the item is **lost**;
- a cell is read again: the item is **duplicated**.

Measured with 10,000 items:

| Producers / consumers / capacity | Lost | Duplicated |
|---|---|---|
| 2 / 2 / 5 | 1091 to 3477 | 1036 to 3193 |
| 1 / 1 / 5 | 386 | 4089 |

Results are scheduler-dependent and vary between runs. The poison pill can itself be lost,
leaving a consumer waiting indefinitely; the simulation timeout terminates the run (section 5).
The scenario is designed to yield wrong results **without throwing exceptions** (D7).

### 2.3 Monitor

Intrinsic lock of the object: `synchronized` methods, `wait()` when the buffer is full or empty,
`notifyAll()` after each change. Producers and consumers share one wait set; `notify()` could
wake a thread of the wrong kind and leave the required one asleep (D8).

### 2.4 Lock and Condition

Same logic as the monitor with explicit primitives: a `ReentrantLock` and two conditions,
`notFull` (producers wait) and `notEmpty` (consumers wait). Each wait set holds one kind of thread,
so `signal()` suffices. `unlock()` is always invoked in `finally` (D8).

### 2.5 Blocking queue

Delegation to `java.util.concurrent.ArrayBlockingQueue`, which implements the same
lock-and-condition scheme internally. Preferred solution in production code (D9).

### 2.6 Deadlock

Threads waiting on each other in a cycle, detected via `ThreadMXBean.findDeadlockedThreads()`
and then resolved. The deadlocking setup is not yet designed.

## 3. Implemented classes

Base package: `producerconsumer`.

### 3.1 `model.Item` (done, D4)

Immutable item exchanged between producers and consumers.

| Member | Description |
|---|---|
| `Item(int id, int producerId)` | constructor |
| `getId()` | unique id from a global counter shared by all producers (D1) |
| `getProducerId()` | id of the producing producer |
| `POISON` | constant item (`id = -1`, `producerId = -1`) signaling end of production |
| `isPoison()` | identity comparison with `POISON` (not by value) |

Notes:

- `final` class, `private final` fields: shareable between threads without synchronization.
- Written as a classic class, not a `record`: `equals`, `hashCode`, `toString` and getters are explicit.
- `equals` / `hashCode` use `id` and `producerId`.
- The poison pill is a named constant instead of `null`: standard blocking queues reject `null`.
- A global (not per-producer) id counter keeps each item distinguishable, required to count
  lost and duplicated items.
- `int` ids: adequate for didactic volumes; the UI parameters will need an upper bound.

### 3.2 `buffer.Buffer` (done, D5, D6)

Interface of a bounded buffer of `Item`s.

```java
void put(Item item) throws InterruptedException;  // waits while full; NullPointerException on null
Item take() throws InterruptedException;          // waits while empty; never returns null
int capacity();                                   // fixed at construction
```

Notes:

- `InterruptedException` is declared so that a timeout can interrupt a blocked thread; the exception
  propagates to the `run()` loop and the thread terminates.
- No `size()` / `isEmpty()`: they invite check-then-act races. End of production is signaled
  by the poison pill, not by inspecting the buffer. `capacity()` is safe because immutable.
- Not generic: the buffer holds only `Item`.

Rules common to all implementations:

- `put(null)` throws `NullPointerException` (explicit `if`; `ArrayBlockingQueue` already does so).
- Capacity `<= 0` throws `IllegalArgumentException` in the constructor.
- Waiting always occurs in a `while` loop, never an `if`: the condition is rechecked after waking
  (another thread may have acted first; spurious wakeups are permitted).

### 3.3 `buffer.UnsafeBuffer` (done, D7)

Deliberately not thread-safe. Circular array `Item[]` with `head`, `tail`, `count`; no lock.

- `count` is `volatile`: ensures visibility, not atomicity.
- Waiting by polling with `Thread.yield()`, since `wait()` requires holding a monitor.
- `Thread.interrupted()` is checked explicitly, as `yield()` does not throw `InterruptedException`.
- Guards use `>=` and `<=` instead of `==`: once `count` drifts below zero, `== 0` would no longer stop consumers.
- Array instead of `LinkedList`: a list modified concurrently may corrupt its nodes and throw,
  whereas the array always remains in a valid state.
- Cells are not cleared after `take`: with `count` above the real value, an already-read cell is read again.
- A never-written cell (`null`) is skipped.

### 3.4 `buffer.MonitorBuffer` (done, D8)

`LinkedList` guarded by `synchronized` methods. `put` waits while full, `take` while empty;
both call `notifyAll()` after modifying the queue.

### 3.5 `buffer.LockBuffer` (done, D8)

`LinkedList` guarded by a `ReentrantLock` with conditions `notFull` and `notEmpty`.
`put` signals `notEmpty`, `take` signals `notFull`. `lock()` is used rather than `lockInterruptibly()`:
the long wait occurs in `await()`, which is already interruptible.

### 3.6 `buffer.BlockingQueueBuffer` (done, D9)

Adapter forwarding `put` and `take` to an `ArrayBlockingQueue<Item>`. Fixed capacity, as in the
other buffers (`LinkedBlockingQueue` is unbounded by default).

### 3.7 Buffer comparison

| Buffer | Synchronization | Wait sets | Correct |
|---|---|---|---|
| `UnsafeBuffer` | none (`volatile` counter, polling) | none | no |
| `MonitorBuffer` | `synchronized` | one (`notifyAll`) | yes |
| `LockBuffer` | `ReentrantLock` | two (`signal`) | yes |
| `BlockingQueueBuffer` | `ArrayBlockingQueue` internals | two (internal) | yes |

## 4. Planned components

No implementation exists for the components below; their design is recorded in `design.md` as it is completed.

| Component | Purpose | Status |
|---|---|---|
| `IdGenerator` | global counter for unique item ids; itself a shared resource (D1) | planned, design in progress |
| `Producer` | thread creating items and putting them into a `Buffer` | planned, design in progress |
| `Consumer` | thread taking items until the poison pill is received | planned, design in progress |
| `SimulationResult` | counts of produced, consumed, distinct consumed | planned |
| `Simulation` | runs a scenario with timeout and deadlock detection | planned |
| Sequential and Deadlock scenarios | scenarios 1 and 6 | planned |
| `App` / `Menu` | terminal UI, plain ANSI codes, no library | planned |

### Result metrics (D1)

- lost = produced − distinct consumed
- duplicated = consumed − distinct consumed

## 5. Blocking detection (D2)

Two complementary mechanisms:

1. `ThreadMXBean.findDeadlockedThreads()` identifies real deadlocks (circular lock waits).
2. A per-scenario timeout interrupts all threads when exceeded. It covers blocks that are not
   deadlocks, such as a consumer waiting for an item lost in the unsynchronized scenario.

The application always returns to the menu.

## 6. Tests

Run with `mvn test`. Current suite: 43 test executions (inherited tests run once per concrete subclass).

| Class | Scope |
|---|---|
| `BufferTest` (abstract) | contract common to all buffers: FIFO order, capacity, `null`, wrap-around, waiting when full or empty, interruption. One subclass per buffer defines only how to create it. |
| `SynchronizedBufferTest` (abstract, extends `BufferTest`) | concurrency invariant for the correct buffers: 4 producers, 3 consumers, each item consumed exactly once. |
| `ItemTest` | `Item` equality and poison pill. |
| `SmokeTest` | build and test infrastructure check. |

`UnsafeBuffer` has no race test: losses vary widely between runs (about 400 to 3500 of 10,000 items,
even with one producer and one consumer). The simulation will verify only that no exception is thrown.

Principles (D3):

- Every concurrent test has a timeout: a block fails the test instead of hanging the build.
- Tests assert invariants valid under any interleaving, never a specific thread order.
- "Blocks when full or empty" is verified by checking that the thread is still alive after a wait.

## 7. Maintenance of this document

When a component is implemented: move it from section 4 to section 3 with a new subsection,
update the status in the scenario table, and update the test count in section 6.
