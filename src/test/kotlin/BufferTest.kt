import io.github.shanpark.buffers.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class BufferTest {

    @Test
    @DisplayName("Buffer 테스트")
    internal fun bufferTest() {
        val buf = ByteArray(1024) // dummy data.
        val data = ByteArray(1024) // dummy data.
        for (inx in data.indices)
            data[inx] = (inx % 256).toByte()

        val buffer = Buffer(1024)

        assertThat(buffer.isReadable).isFalse
        assertThat(buffer.readableBytes).isEqualTo(0)
        assertThat(buffer.wOffset).isEqualTo(0)

        var arr = buffer.wArray
        buffer.write(data, 0, 1023) // <- 1023
        assertThat(buffer.wArray).isEqualTo(arr)
        assertThat(buffer.writableBytes).isEqualTo(1)
        assertThat(buffer.readableBytes).isEqualTo(1023)
        assertThat(buffer.read()).isEqualTo(0) // 1 ->
        assertThat(buffer.read()).isEqualTo(1) // 1 ->
        buffer.read(buf, 0, 10) // 10 ->
        assertThat(buf[0]).isEqualTo(2)
        assertThat(buffer.readableBytes).isEqualTo(1011)
        assertThat(buffer.isReadable).isTrue

        buffer.write(data[1023].toInt()) // <- 1
        assertThat(arr === buffer.wArray).isFalse
        assertThat(buffer.writableBytes).isEqualTo(1024)
        assertThat(buffer.wOffset).isEqualTo(0)

        buffer.write(data) // <- 1024
        assertThat(buffer.readableBytes).isEqualTo(2036)

        buffer.write(data, 0, 256)  // <- 256
        assertThat(buffer.readableBytes).isEqualTo(2292)

        arr = buffer.rArray
        val read = buffer.read(buf) // 1024 ->
        assertThat(read).isEqualTo(buf.size)
        assertThat(arr === buffer.rArray).isFalse
        assertThat(buffer.rOffset).isEqualTo(12)
        assertThat(buf[0]).isEqualTo(12)

        buffer.compact()
        assertThat(arr === buffer.rArray).isFalse
        assertThat(buffer.rOffset).isEqualTo(12)

        buffer.mark()
        assertThat(buffer.readInt()).isEqualTo(0x0c0d0e0f) // 4 ->
        assertThat(buffer.readLong()).isEqualTo(0x10111213_14151617L) // 8 ->

        buffer.reset() // restore <- 12
        assertThat(buffer.readInt()).isEqualTo(0x0c0d0e0f) // 4 ->
        assertThat(buffer.readLong()).isEqualTo(0x10111213_14151617L) // 8 ->

        buffer.read(buf, 0, 999) // 999 ->
        buffer.mark()
        var readable = buffer.readableBytes
        val slice = buffer.readSlice(8) // 8 ->
        assertThat(slice.readLong()).isEqualTo(0xff000102L.shl(32).or(0x03040506L))
        assertThat(buffer.readableBytes).isEqualTo(readable - 8)
        buffer.reset()

        readable = buffer.readableBytes
        val ist = buffer.inputStream()
        assertThat(ist.read()).isEqualTo(0xff) // 1 ->
        assertThat(buffer.readableBytes).isEqualTo(readable - 1)

        val ost = buffer.outputStream()
        ost.write(0x00) // <- 1
        ost.write(buf, 1, 1023) // <- 1023
        assertThat(buffer.readableBytes).isEqualTo(readable + 1023)

        buffer.clear()
        assertThat(buffer.readableBytes).isEqualTo(0)
    }

    @Test
    @DisplayName("Buffer 데이터 RW 테스트")
    internal fun dataRWTest() {
        val buffer = Buffer(1024)

        buffer.writeByte(0x12)
        buffer.writeShort(0x1234)
        buffer.writeInt(0x12345678)
        buffer.writeLong(0x12345678_12345678L)
        buffer.writeFloat(123.45f)
        buffer.writeDouble(1234.56789)
        buffer.writeChar('한')
        buffer.writeByte(-1)
        buffer.writeShort(-1)
        buffer.writeInt(-1)
        buffer.writeString("Hello World!")

        assertThat(buffer.readByte()).isEqualTo(0x12)
        assertThat(buffer.readShort()).isEqualTo(0x1234)
        assertThat(buffer.readInt()).isEqualTo(0x12345678)
        assertThat(buffer.readLong()).isEqualTo(0x12345678_12345678L)
        assertThat(buffer.readFloat()).isEqualTo(123.45f)
        assertThat(buffer.readDouble()).isEqualTo(1234.56789)
        assertThat(buffer.readChar()).isEqualTo('한')
        assertThat(buffer.readUByte()).isEqualTo(0xff)
        assertThat(buffer.readUShort()).isEqualTo(0xffff)
        assertThat(buffer.readUInt()).isEqualTo(0xffffffffL)
        assertThat(buffer.readString(buffer.readableBytes)).isEqualTo("Hello World!")
    }
}