import io.github.shanpark.buffers.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BufferTest {

    @Test
    internal fun bufferTest() {
        val buf = ByteArray(1024) // dummy data.
        val data = ByteArray(1024) // dummy data.
        for (inx in data.indices)
            data[inx] = (inx % 256).toByte()

        val buffer = Buffer(1024)

        assertThat(buffer.isReadable()).isFalse
        assertThat(buffer.readableBytes()).isEqualTo(0)
        assertThat(buffer.wOffset()).isEqualTo(0)

        var arr = buffer.wArray()
        buffer.write(data, 0, 1023)
        assertThat(buffer.wArray()).isEqualTo(arr)
        assertThat(buffer.writableBytes()).isEqualTo(1)
        assertThat(buffer.readableBytes()).isEqualTo(1023)
        assertThat(buffer.read()).isEqualTo(0)
        assertThat(buffer.read()).isEqualTo(1)
        buffer.read(buf, 0, 10)
        assertThat(buf[0]).isEqualTo(2)
        assertThat(buffer.readableBytes()).isEqualTo(1011)
        assertThat(buffer.isReadable()).isTrue

        buffer.write(data[1023].toInt())
        assertThat(arr === buffer.wArray()).isFalse
        assertThat(buffer.writableBytes()).isEqualTo(1024)
        assertThat(buffer.wOffset()).isEqualTo(0)

        buffer.write(data)
        assertThat(buffer.readableBytes()).isEqualTo(2036)

        buffer.write(data, 0, 256)
        assertThat(buffer.readableBytes()).isEqualTo(2292)

        arr = buffer.rArray()
        val read = buffer.read(buf)
        assertThat(read).isEqualTo(buf.size)
        assertThat(arr === buffer.rArray()).isFalse
        assertThat(buffer.rOffset()).isEqualTo(12)
        assertThat(buf[0]).isEqualTo(12)

        assertThat(buffer.readInt()).isEqualTo(0x0c0d0e0f)
        assertThat(buffer.readLong()).isEqualTo(0x10111213_14151617L)
    }
}