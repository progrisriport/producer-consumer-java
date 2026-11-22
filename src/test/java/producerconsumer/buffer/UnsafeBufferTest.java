package producerconsumer.buffer;

class UnsafeBufferTest extends BufferTest {

    @Override
    protected Buffer createBuffer(int capacity) {
        return new UnsafeBuffer(capacity, 0);
    }
}
