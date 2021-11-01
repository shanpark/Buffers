package io.github.shanpark.buffers

import io.github.shanpark.buffers.exception.BufferException
import io.github.shanpark.buffers.exception.UnderflowException
import java.lang.Double.doubleToLongBits
import java.lang.Double.longBitsToDouble
import java.lang.Float.floatToIntBits
import java.lang.Float.intBitsToFloat
import kotlin.math.max

class Buffer(initialCapacity: Int = 1024) {
    private val blocks = mutableListOf<ByteArray>()
    private var rBlock: Int = 0
    private var wBlock: Int = 0
    private var rIndex: Int = 0
    private var wIndex: Int = 0

    private var markedBlock: Int = -1
    private var markedIndex: Int = -1

    init {
        blocks.add(ByteArray(max(initialCapacity, 1024)))
    }

    /**
     * read()가 반환할 수 있는 데이터가 있는지 여부를 반환한다.
     * 참고로 writable() 메소드는 없다. 항상 writable이기 때문이다.
     *
     * @return reaa()가 반환할 데이터가 있으면 true, 반환할 데이터가 없으면 false.
     */
    fun isReadable(): Boolean = (rBlock < wBlock) || (rIndex < wIndex)

    /**
     * 현재 버퍼에 write할 수 있는 데이터 공간의 크기(byte 단위)를 얻어온다.
     * 버퍼의 현재 상태에 따른 값이며 버퍼에 write할 수 있는 공간이 없는 경우 추가로 공간을 할당하기 때문에
     * 0이 반환되는 일은 없다.
     *
     * @return write할 수 있는 공간의 크기(byte 단위)를 반환.
     */
    fun readableBytes(): Int {
        return when (wBlock - rBlock) {
            0 -> wIndex - rIndex
            1 -> (blocks[rBlock].size - rIndex) + wIndex
            else -> {
                (blocks[rBlock].size - rIndex) + ((wBlock - rBlock - 1) * blocks[rBlock].size) + wIndex
            }
        }
    }

    /**
     * 현재 버퍼에 write할 수 있는 데이터 공간의 크기(byte 단위)를 얻어온다.
     * 버퍼의 현재 상태에 따른 값이며 버퍼에 write할 수 있는 공간이 없는 경우 추가로 공간을 할당하기 때문에
     * 0이 반환되는 일은 없다.
     *
     * @return write할 수 있는 공간의 크기(byte 단위)를 반환.
     */
    fun writableBytes(): Int {
        allocBufferIfNeeded()
        return blocks[wBlock].size - wIndex
    }

    /**
     * 버퍼에서 한 byte의 데이터를 읽어서 반환한다.
     * 읽은 후 read position은 다음 byte로 이동한다.
     * 버퍼가 비어있으면 -1을 반환한다.
     *
     * @return 버퍼에서 읽은 (1 byte)값. 비어있으면 -1.
     */
    fun read(): Int {
        return if (isReadable()) {
            if (blocks[rBlock].size == rIndex) {
                rBlock++
                rIndex = 0
            }
            blocks[rBlock][rIndex++].toInt().and(0xff)
        } else {
            return -1
        }
    }

    /**
     * 파라미터로 전달된 ByteArray에 데이터를 읽어들인다.
     * 전달된 buf의 크기 보다 실제 읽은 데이터의 길이는 더 작을 수 있다.
     * 전달된 buf의 크기가 0이면 0이 반환된다.
     * 버퍼가 비어있다면 -1이 반환될 것이다.
     *
     * @param buf 데이터를 읽어들일 ByteArray
     *
     * @return 실제 읽혀진 byte 수.
     */
    fun read(buf: ByteArray): Int = read(buf, 0, buf.size)

    /**
     * 파라미터로 전달된 ByteArray에 데이터를 읽어들인다.
     * 요청한 길이보다 실제 읽은 데이터의 길이는 더 작을 수 있다.
     * 요청한 길이가 0이면 0이 반환된다.
     * 버퍼가 비어있다면 -1이 반환될 것이다.
     *
     * @param buf 데이터를 읽어들일 ByteArray
     * @param offset buf에서 write를 할 공간의 시작 offset.
     * @param length 읽어들일 데이터의 길이.
     *
     * @return 실제 읽혀진 byte 수.
     *
     * @throws IndexOutOfBoundsException - If offset is negative, length is negative, or length is greater
     *                                     than buf.size - offset
     */
    fun read(buf: ByteArray, offset: Int, length: Int): Int {
        return if (offset >= 0 && length >= 0 && length <= buf.size - offset) {
            if (length == 0) {
                0
            } else {
                var by = this.read()
                if (by == -1) {
                    -1
                } else {
                    buf[offset] = by.toByte()
                    var written = 1
                    try {
                        while (written < length) {
                            by = this.read()
                            if (by == -1) {
                                break
                            }
                            buf[offset + written] = by.toByte()
                            ++written
                        }
                    } catch (e: BufferException) {
                    }
                    written
                }
            }
        } else {
            throw IndexOutOfBoundsException()
        }
    }

    /**
     * 버퍼에 1 byte의 데이터를 write한다.
     * 현재 write가 가능한 공간이 없다면 추가로 공간을 할당한 후 write를 수행한다.
     */
    fun write(data: Int) {
        allocBufferIfNeeded()
        blocks[wBlock][wIndex++] = data.toByte()
    }

    /**
     * 파라미터로 전달된 buf의 데이터를 버퍼에 write한다.
     *
     * @param buf write할 데이터를 담은 ByteArray
     *
     * @return 버퍼에 실제 write가 된 byte 수.
     */
    fun write(buf: ByteArray) = write(buf, 0, buf.size)

    /**
     * 파라미터로 전달된 buf의 offset부터 length 만큼의 데이터를 버퍼에 write한다.
     *
     * @param buf write할 데이터를 담은 ByteArray
     * @param offset buf에서 에서 write를 할 데이터의 시작 offset.
     * @param length write할 데이터의 길이.
     *
     * @return 버퍼에 실제 write가 된 byte 수.
     *
     * @throws IndexOutOfBoundsException If offset is negative, or length is negative, or offset+length is greater than
     *                                   the size of the array buf, then an IndexOutOfBoundsException is thrown.
     */
    fun write(buf: ByteArray, offset: Int, length: Int) {
        if (offset >= 0 && offset <= buf.size && length >= 0 && offset + length <= buf.size && offset + length >= 0) {
            if (length != 0) {
                for (inx in 0 until length) {
                    write(buf[offset + inx].toInt())
                }
            }
        } else {
            throw IndexOutOfBoundsException()
        }
    }

    /**
     * 버퍼에서 1 byte를 읽어서 Byte형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 1 byte의 데이터로 만들어진 Byte 값.
     *
     * @throws UnderflowException 버퍼가 비어있으면 발생
     */
    fun readByte(): Byte {
        return if (isReadable())
            read().toByte()
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 2 byte를 읽어서 Short형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 2 byte의 데이터로 만들어진 Int 값.
     *
     * @throws UnderflowException 버퍼의 데이터가 2 byte 보다 적게 남아있으면 발생
     */
    fun readShort(): Short {
        return if (readableBytes() >= 2)
            read().shl(8).or(read()).toShort()
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 4 byte를 읽어서 Int형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 4 byte의 데이터로 만들어진 Int 값.
     *
     * @throws UnderflowException 버퍼의 데이터가 4 byte 보다 적게 남아있으면 발생
     */
    fun readInt(): Int {
        return if (readableBytes() >= 4)
            read().shl(8).or(read()).shl(8).or(read()).shl(8).or(read())
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 8 byte를 읽어서 Long형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 8 byte의 데이터로 만들어진 Long 값.
     *
     * @throws UnderflowException 버퍼의 데이터가 8 byte 보다 적게 남아있으면 발생
     */
    fun readLong(): Long {
        return if (readableBytes() >= 8)
            readInt().toLong().shl(32).or(readInt().toLong().and(0xffffffff))
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 4 byte를 읽어서 Float형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 4 byte의 데이터로 만들어진 Float 값.
     *
     * @throws UnderflowException 버퍼의 데이터가 4 byte 보다 적게 남아있으면 발생
     */
    fun readFloat(): Float {
        return if (readableBytes() >= 4)
            intBitsToFloat(readInt())
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 8 byte를 읽어서 Double형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 8 byte의 데이터로 만들어진 Double 값.
     *
     * @throws UnderflowException 버퍼의 데이터가 8 byte 보다 적게 남아있으면 발생
     */
    fun readDouble(): Double {
        return if (readableBytes() >= 8)
            longBitsToDouble(readLong())
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에 Byte 값을 write한다.
     */
    fun writeByte(value: Byte) = write(value.toInt())

    /**
     * 버퍼에 Short 값을 write한다.
     */
    fun writeShort(value: Short) {
        val i = value.toInt()
        write(i.shr(8))
        write(i)
    }

    /**
     * 버퍼에 Int 값을 write한다.
     */
    fun writeInt(value: Int) {
        write(value.shr(24))
        write(value.shr(16))
        write(value.shr(8))
        write(value)
    }

    /**
     * 버퍼에 Long 값을 write한다.
     */
    fun writeLong(value: Long) {
        writeInt(value.shr(32).toInt())
        writeInt(value.toInt()) // least significant 32 bit wil be used.
    }

    /**
     * 버퍼에 Float 값을 write한다.
     */
    fun writeFloat(value: Float) = writeInt(floatToIntBits(value))

    /**
     * 버퍼에 Double 값을 write한다.
     */
    fun writeDouble(value: Double) = writeLong(doubleToLongBits(value))

    /**
     * 현재 read position을 임시로 저장한다.
     * 이 후 reading 작업을 계속 하다고 reset()이 호출되면 다시 mark()를 호출했던 시점으로 돌아간다.
     * 혹시 이전에 저장된 상태가 있었다면 버려진다.
     *
     * compact(), clear() 등의 메소드를 호출하면 저장된 상태도 모두 무효화된다.
     */
    fun mark() {
        saveMark()
    }

    /**
     * mark()를 호출하여 저장했던 상태로 돌아간다.
     * 저장된 상태가 없으면 아무 일도 하지 않는다.
     */
    fun reset() {
        if (isMarkValid())
            loadMark()
    }

    /**
     * 사용이 끝난 내부 버퍼들을 정리한다.
     * mark()를 호출하여 저장된 상태는 모두 invalidate 된다.
     */
    fun compact() {
        while (rBlock > 0) {
            blocks.removeFirst()
            rBlock--
            wBlock--
        }

        invalidateMark()
    }

    /**
     * 버퍼의 모든 데이터를 삭제하고 처음 상태로 되돌린다.
     */
    fun clear() {
        while (blocks.size > 1)
            blocks.removeLast()
        rBlock = 0
        wBlock = 0
        rIndex = 0
        wIndex = 0

        invalidateMark()
    }

    /**
     * read 작업을 할 ByteArray를 반환한다.
     * 반환된 array의 전체를 읽을 수 있는 건 아니다. rOffset() 메소드가 반환하는 위치부터 읽을 수 있는 데이터가 있다.
     * readableBytes()가 반환하는 값이 이 남은 공간의 크기보다 크다면 남은 공간 전체를 읽을 수 있고 그렇지 않다면
     * readableBytes()가 반환하는 값 만큼 읽을 수 있다.
     * 버퍼가 비어있더라도 array는 반환된다.
     *
     * @return read를 위해서 사용할 수 있는 ByteArray를 반환.
     */
    fun rArray(): ByteArray {
        return blocks[rBlock]
    }

    /**
     * rArray() 메소드가 반환하는 ByteArray의 readable한 공간의 시작 offset을 반환한다.
     *
     * @return rArray()가 반환하는 ByteArray의 readable 공간의 시작 offset.
     */
    fun rOffset(): Int {
        return rIndex
    }

    /**
     * write 작업을 할 ByteArray를 반환한다.
     * 반환된 array의 공간을 모두 쓸 수 있는 건 아니다. offset() 메소드가 반환하는 위치부터 array의 끝까지 사용할 수 있다.
     * 현재 가용한 공간이 없는 상태라면 추가 공간 할당이 수행된다.
     *
     * @return write를 위해서 사용할 수 있는 ByteArray를 반환.
     */
    fun wArray(): ByteArray {
        allocBufferIfNeeded()
        return blocks[wBlock]
    }

    /**
     * array() 메소드가 반환하는 ByteArray의 사용 가능한 공간의 시작 offset을 반환한다.
     * 현재 가용한 공간이 없는 상태라면 추가 공간 할당이 수행된다.
     *
     * @return wArray()가 반환하는 ByteArray의 writable 공간의 시작 offset.
     */
    fun wOffset(): Int {
        allocBufferIfNeeded()
        return wIndex
    }

    /**
     * 저장된 read position이 있는 지 여부 반환.
     */
    private fun isMarkValid(): Boolean {
        return (markedBlock >= 0)
    }

    /**
     * 저장된 read position 무효화
     */
    private fun invalidateMark() {
        markedBlock = -1
    }

    /**
     * 저장된 read position 복원
     */
    private fun loadMark() {
        rBlock = markedBlock
        rIndex = markedIndex
    }

    /**
     * 현재 read position 저장.
     */
    private fun saveMark() {
        markedBlock = rBlock
        markedIndex = rIndex
    }

    /**
     * buffer가 꽉찼으면 추가 buffer를 할당하고 wBlock, wIndex를 적절히 수정해준다.
     * 빈 공간이 1 byte라도 있으면 아무 일도 하지 않는다.
     */
    private fun allocBufferIfNeeded() {
        val writable = blocks[wBlock].size - wIndex
        if (writable == 0) {
            blocks.add(ByteArray(blocks.first().size))
            wBlock = blocks.size - 1
            wIndex = 0
        }
    }
}