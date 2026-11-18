package producerconsumer.buffer;

class BlockingQueueBufferTest extends SynchronizedBufferTest {

    @Override
    protected Buffer createBuffer(int capacity) {
        return new BlockingQueueBuffer(capacity);
    }
}
