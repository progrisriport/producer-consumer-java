package producerconsumer.buffer;

class MonitorBufferTest extends SynchronizedBufferTest {

    @Override
    protected Buffer createBuffer(int capacity) {
        return new MonitorBuffer(capacity);
    }
}
