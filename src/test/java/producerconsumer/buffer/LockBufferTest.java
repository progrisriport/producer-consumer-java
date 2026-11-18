package producerconsumer.buffer;

class LockBufferTest extends SynchronizedBufferTest {

    @Override
    protected Buffer createBuffer(int capacity) {
        return new LockBuffer(capacity);
    }
}
