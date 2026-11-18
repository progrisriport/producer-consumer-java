# producer-consumer-java

A didactic simulator of the producer-consumer problem, built for the
Concurrent Programming course at Università degli Studi dell'Insubria.

## What is this

The producer-consumer problem is a classic concurrency challenge: one or more
producers generate data and place it in a shared buffer, while one or more
consumers retrieve and process it. The difficulty lies in coordinating access
to the buffer — preventing overwrites, avoiding reads from an empty buffer,
and ensuring no data is lost.

This application makes that problem tangible. Users select a scenario from a
terminal menu, run it and observe — numerically — how each synchronization
strategy behaves, including the wrong ones.

## Scenarios

1. **Sequential** — baseline, no concurrency.
2. **Unsynchronized** — shows the problem: lost or duplicated items.
3. **Monitor** — `synchronized` + `wait`/`notifyAll`.
4. **Lock and Condition** — `ReentrantLock` + `Condition`.
5. **Blocking queue** — `ArrayBlockingQueue` from the standard library.
6. **Deadlock** — shows a deadlock, then its fix.

See [docs/GUIDE.md](docs/GUIDE.md) for a description of all scenarios and classes.

## Usage

```bash
mvn test        # run the test suite
mvn compile exec:java
```

## Requirements

- Java 21
- Maven

## License

Released under the [MIT License](LICENSE).
