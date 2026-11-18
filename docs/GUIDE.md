# Producer-Consumer in Java: Scenarios and Classes

A teaching project that runs the classic producer-consumer problem under different
synchronization strategies, including incorrect ones, and reports the outcome numerically.

Requirements: Java 21, Maven. No runtime dependencies (JUnit 5 for tests only).

**Status legend:** `done` = implemented and tested. `planned` = designed, not yet implemented.

## 1. The problem

Producers create items and put them into a shared bounded buffer. Consumers take items out.
Two rules must hold:

- A producer waits when the buffer is full; a consumer waits when it is empty.
- Every produced item is consumed exactly once.

Without proper synchronization, threads interleave unpredictably and the rules break.

## 2. Scenarios

| # | Scenario | Mechanism | Expected result | Status |
|---|----------|-----------|-----------------|--------|
| 1 | Sequential | Single thread, no concurrency | produced == consumed | planned |
| 2 | Unsynchronized | `UnsafeBuffer`, no locking | Items lost and duplicated | buffer done, scenario planned |
| 3 | Monitor | `MonitorBuffer`: `synchronized`, `wait`, `notifyAll` | produced == consumed | buffer done, scenario planned |
| 4 | Lock and Condition | `LockBuffer`: `ReentrantLock`, two `Condition`s | produced == consumed | buffer done, scenario planned |
| 5 | Blocking queue | `BlockingQueueBuffer`: `ArrayBlockingQueue` | produced == consumed | buffer done, scenario planned |
| 6 | Deadlock | Circular wait on locks, then a fix | Deadlock detected, then resolved | planned |

### 2.1 Sequential

Baseline with no concurrency: the reference behavior against which the concurrent
scenarios are compared.

### 2.2 Unsynchronized

Shows what goes wrong without mutual exclusion. The shared counter `count` is updated
with `count++` / `count--`, which are not atomic (read, modify, write). Two threads can read
the same value and both write `count ± 1`, so one update is lost. The counter then diverges
from the real buffer content:

- a cell is overwritten before being read: the item is **lost**;
- a cell is read again: the item is **duplicated**.

Measured with 10,000 items:

| Producers / consumers / capacity | Lost | Duplicated |
|---|---|---|
| 2 / 2 / 5 | 1091 to 3477 | 1036 to 3193 |
| 1 / 1 / 5 | 386 | 4089 |

Results vary between runs (scheduler-dependent). The poison pill can itself be lost, leaving a
consumer waiting forever; the simulation timeout ends the run (see section 5).

The scenario is designed to produce wrong results **without throwing exceptions**.

### 2.3 Monitor

Uses the intrinsic lock of the object: `synchronized` methods, `wait()` when the buffer is
full or empty, `notifyAll()` after each change. Producers and consumers share a single wait set,
so `notify()` could wake a thread of the wrong kind and leave the needed one asleep.

### 2.4 Lock and Condition

Same logic as the monitor, with explicit primitives: a `ReentrantLock` and two conditions,
`notFull` (producers wait here) and `notEmpty` (consumers wait here). Each wait set holds only
one kind of thread, so `signal()` is enough. `unlock()` is always called in `finally`.

### 2.5 Blocking queue

Delegates to `java.util.concurrent.ArrayBlockingQueue`, which implements the same
lock-and-condition scheme internally. This is the preferred solution in real code.

### 2.6 Deadlock

Shows a deadlock (threads waiting on each other in a cycle), detects it, then applies a fix.
Detection uses `ThreadMXBean.findDeadlockedThreads()`. Details of the deadlocking setup are
not yet designed.

## 3. Classes

Base package: `producerconsumer`.

### 3.1 `model.Item` (done)

Immutable item exchanged between producers and consumers.

| Member | Description |
|---|---|
| `id` | Unique id from a global counter shared by all producers |
| `producerId` | Id of the producer that created the item |
| `POISON` | Constant item (`id = -1`, `producerId = -1`) signaling end of production |
| `isPoison()` | Identity comparison with `POISON` |

Design notes:

- `final` class with `private final` fields: safe to share between threads without synchronization.
- A global id counter (not one per producer) keeps every item distinguishable, which is required to
  count lost and duplicated items.
- The poison pill is a named constant rather than `null`, because standard blocking queues reject `null`.
- `equals` and `hashCode` use `id` and `producerId`.

### 3.2 `buffer.Buffer` (done)

Interface of a bounded buffer holding `Item`s.

```java
void put(Item item) throws InterruptedException;  // waits while full
Item take() throws InterruptedException;          // waits while empty, never returns null
int capacity();                                   // fixed at construction
```

Design notes:

- `InterruptedException` is declared so a timeout can interrupt a blocked thread; the exception
  propagates to the thread's `run()` loop and the thread terminates.
- No `size()` or `isEmpty()`: they invite check-then-act races. End of production is signaled
  by the poison pill, not by inspecting the buffer.
- Not generic: the buffer holds only `Item`.

Rules shared by all implementations:

- `put(null)` throws `NullPointerException`.
- Capacity `<= 0` throws `IllegalArgumentException` in the constructor.
- Waiting always happens in a `while` loop, never an `if`: the condition must be rechecked
  after waking up (another thread may have acted first; spurious wakeups are allowed).

### 3.3 `buffer.UnsafeBuffer` (done)

Deliberately not thread-safe. Circular array `Item[]` with `head`, `tail` and `count`; no lock.

- `count` is `volatile`: guarantees visibility, not atomicity.
- Waiting is done by polling with `Thread.yield()`, since `wait()` requires holding a monitor.
- `Thread.interrupted()` is checked explicitly, because `yield()` does not throw `InterruptedException`.
- Guards use `>=` and `<=` instead of `==`: once `count` has drifted below zero, `== 0` would no longer stop consumers.
- An array is used instead of a `LinkedList`: a list modified concurrently can corrupt its nodes and throw,
  whereas the array always stays in a valid state.

### 3.4 `buffer.MonitorBuffer` (done)

`LinkedList` guarded by `synchronized` methods. `put` waits while the queue is full, `take` while it is empty;
both call `notifyAll()` after modifying the queue.

### 3.5 `buffer.LockBuffer` (done)

`LinkedList` guarded by a `ReentrantLock` with conditions `notFull` and `notEmpty`.
`put` signals `notEmpty`, `take` signals `notFull`. `lock()` is used instead of `lockInterruptibly()`
because the long wait happens in `await()`, which is already interruptible.

### 3.6 `buffer.BlockingQueueBuffer` (done)

Adapter forwarding `put` and `take` to an `ArrayBlockingQueue<Item>`. A fixed capacity is used like in the
other buffers (`LinkedBlockingQueue` is unbounded by default).

### 3.7 Comparison of buffers

| Buffer | Synchronization | Wait sets | Correct |
|---|---|---|---|
| `UnsafeBuffer` | none (`volatile` counter, polling) | none | no |
| `MonitorBuffer` | `synchronized` | one (`notifyAll`) | yes |
| `LockBuffer` | `ReentrantLock` | two (`signal`) | yes |
| `BlockingQueueBuffer` | `ArrayBlockingQueue` internals | two (internal) | yes |

## 4. Planned classes

| Class | Purpose | Status |
|---|---|---|
| `IdGenerator` | Global counter for unique item ids (itself a shared resource) | planned |
| `Producer` | Thread that creates items and puts them into a `Buffer` | planned |
| `Consumer` | Thread that takes items until it receives the poison pill | planned |
| `SimulationResult` | Counts produced, consumed, distinct consumed | planned |
| `Simulation` | Runs a scenario with a timeout and deadlock detection | planned |
| Sequential and Deadlock scenarios | Scenarios 1 and 6 | planned |
| `App` / `Menu` | Terminal UI with ANSI codes, no library | planned |

### Result metrics

- lost = produced - distinct consumed
- duplicated = consumed - distinct consumed

## 5. Blocking detection

Two complementary mechanisms:

1. `ThreadMXBean.findDeadlockedThreads()` identifies real deadlocks (circular lock waits).
2. A per-scenario timeout interrupts all threads when exceeded. It covers blocks that are not deadlocks,
   such as a consumer waiting for an item lost in the unsynchronized scenario.

The application always returns to the menu.

## 6. Tests

Run with `mvn test` (currently 43 tests, all passing).

- `BufferTest` (abstract): contract common to all buffers (FIFO order, capacity, `null`, wrap-around,
  waiting when full or empty, interruption). Each buffer has a subclass that only defines how to create it.
- `SynchronizedBufferTest` (abstract, extends `BufferTest`): concurrency invariant for the correct buffers.
  4 producers and 3 consumers; every item is consumed exactly once.
- `UnsafeBuffer` has no race test, since losses vary widely between runs. The simulation will check only that
  no exception is thrown.

Principles:

- Every concurrent test has a timeout, so a block fails the test instead of hanging the build.
- Tests assert invariants valid under any interleaving, never a specific thread order.
- "Blocks when full or empty" is tested by checking that the thread is still alive after a wait.

## 7. Updating this document

When a class is added: move it from section 4 to section 3 with a new subsection, update the status in the
scenario table, and update the test count in section 6.

Source of design decisions: [`design.md`](design.md) (Italian).
