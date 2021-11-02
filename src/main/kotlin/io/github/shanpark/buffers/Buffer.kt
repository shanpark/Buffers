package io.github.shanpark.buffers

import io.github.shanpark.buffers.exception.OverflowException
import io.github.shanpark.buffers.exception.UnderflowException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * read, write가 모두 가능한 버퍼 기능 구현.
 * 내부적으로 여러 ByteArray를 두고 데이터를 저장한다.
 */
class Buffer(initialCapacity: Int = 1024): ReadBuffer, WriteBuffer, Compactable, Clearable {
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

    override fun isReadable(): Boolean = (rBlock < wBlock) || (rIndex < wIndex)

    override fun readableBytes(): Int {
        return when (wBlock - rBlock) {
            0 -> wIndex - rIndex
            1 -> (blocks[rBlock].size - rIndex) + wIndex
            else -> {
                (blocks[rBlock].size - rIndex) + ((wBlock - rBlock - 1) * blocks[rBlock].size) + wIndex
            }
        }
    }

    override fun read(): Int {
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

    override fun rSkip(skipLength: Int) {
        if (readableBytes() >= skipLength) {
            var length = skipLength
            while (length > 0) {
                val rest = blocks[rBlock].size - rIndex
                val len = min(length, rest)
                length -= len
                if (length > 0) {
                    rBlock++
                    rIndex = 0
                } else {
                    rIndex += len
                }
            }
        } else {
            throw UnderflowException()
        }
    }

    /**
     * 현재 read position을 임시로 저장한다.
     * 이 후 reading 작업을 계속 하다고 reset()이 호출되면 다시 mark()를 호출했던 시점으로 돌아간다.
     * 혹시 이전에 저장된 상태가 있었다면 버려진다.
     *
     * compact(), clear() 등의 메소드를 호출하면 저장된 상태도 모두 무효화된다.
     */
    override fun mark() {
        markedBlock = rBlock
        markedIndex = rIndex
    }

    override fun reset() {
        if ((markedBlock >= 0)) {
            rBlock = markedBlock
            rIndex = markedIndex
        }
    }

    override fun rArray(): ByteArray {
        return blocks[rBlock]
    }

    override fun rOffset(): Int {
        return rIndex
    }

    /**
     * 현재 버퍼에 write할 수 있는 데이터 공간의 크기(byte 단위)를 얻어온다.
     * 남은 공간을 다 사용한 후에는 다시 새로운 공간이 할당되므로 0이 반환되는 일은 없다.
     *
     * @return write할 수 있는 공간의 크기(byte 단위)를 반환.
     */
    override fun writableBytes(): Int {
        allocBufferIfNeeded()
        return blocks[wBlock].size - wIndex
    }

    /**
     * 버퍼에 1 byte의 데이터를 write한다.
     * 현재 write가 가능한 공간이 없다면 추가로 공간을 할당한 후 write를 수행한다.
     */
    override fun write(data: Int) {
        allocBufferIfNeeded()
        blocks[wBlock][wIndex++] = data.toByte()
    }

    override fun wSkip(skipLength: Int) {
        // write position은 항상 마지막 block에 존재하며 추가 할당은 일어나지 않으므로 다음 block으로
        // 이동하는 건 고려하지 않아도 됨.
        val rest = blocks[wBlock].size - wIndex
        if (skipLength <= rest)
            rIndex += skipLength
        else
            throw OverflowException()
    }

    /**
     * write 작업을 할 ByteArray를 반환한다.
     * 반환된 array의 공간을 모두 쓸 수 있는 건 아니다. offset() 메소드가 반환하는 위치부터 array의 끝까지 사용할 수 있다.
     * 현재 가용한 공간이 없는 상태라면 추가 공간 할당이 수행된다.
     *
     * @return write를 위해서 사용할 수 있는 ByteArray를 반환.
     */
    override fun wArray(): ByteArray {
        allocBufferIfNeeded()
        return blocks[wBlock]
    }

    /**
     * array() 메소드가 반환하는 ByteArray의 사용 가능한 공간의 시작 offset을 반환한다.
     * 현재 가용한 공간이 없는 상태라면 추가 공간 할당이 수행된다.
     *
     * @return wArray()가 반환하는 ByteArray의 writable 공간의 시작 offset.
     */
    override fun wOffset(): Int {
        allocBufferIfNeeded()
        return wIndex
    }

    /**
     * 사용이 끝난 내부 버퍼들을 정리한다.
     * 내부 구조가 바뀌게 되므로 mark()를 호출하여 저장된 상태는 모두 invalidate 된다.
     * 이 버퍼에 의해서 생성된 다른 버퍼나 stream들도 모두 invalidate되며 이렇게 내부 상태의 변경에 따라
     * 유효성이 바뀌는 객체들의 사용은 사용자의 책임이다.
     */
    override fun compact() {
        while (rBlock > 0) {
            blocks.removeFirst()
            rBlock--
            wBlock--
        }

        invalidateMark()
    }

    /**
     * 버퍼의 모든 데이터를 삭제하고 처음 상태로 되돌린다.
     * mark()를 호출해서 저장된 상태는 물론이고 이 버퍼에 의해서 생성된 다른 버퍼나 stream들도 모두 invalidate되며
     * 이렇게 내부 상태의 변경에 따라 유효성이 바뀌는 객체들의 사용은 사용자의 책임이다.
     */
    override fun clear() {
        while (blocks.size > 1)
            blocks.removeLast()
        rBlock = 0
        wBlock = 0
        rIndex = 0
        wIndex = 0

        invalidateMark()
    }

    /**
     * 이 버퍼의 현재 상태에서 length 만큼만 읽을 수 있도록 제한된 ReadBuffer를 반환한다.
     * 실제로 데이터의 복사가 일어나지는 않으며 같은 내부 버퍼를 공유한다.
     *
     * 버퍼 공간은 공유되지만 반환된 ReadBuffer로부터 읽기를 수행해도 이 버퍼는 아무런 영향을 받지 않는다.
     * 반면에 반환된 ReadBuffer 객체는 이 버퍼의 내부 구조가 바뀌면 모두 무효화된다.
     * 무효화된 후의 사용에 따른 동작은 undefined.
     *
     * 남아있는 데이터가 length 보다 작으면 UnderflowException이 발생한다.
     *
     * @param length 생성된 ReadBuffer로부터 읽고자 하는 데이터의 길이.
     *
     * @return 데이터를 읽을 수 있는 ReadBuffer 객체.
     *
     * @throws UnderflowException 남아있는 데이터보다 더 많은 데이터를 요청하는 경우 발생.
     */
    fun slice(length: Int): ReadBuffer {
        if (readableBytes() >= length)
            return Slice(blocks, rBlock, rIndex, length)
        else
            throw UnderflowException()
    }

    /**
     * 이 버퍼를 배경으로 동작하는 InputStream 객체를 반환한다.
     * 반환된 InputStream 객체를 통해서 데이터를 읽어들이면 이 버퍼의 read position도 이동된다.
     *
     * @return 이 버퍼를 배경으로 동작하는 InputStream 객체.
     */
    fun inputStream(): InputStream {
        return BufferInputStream(this)
    }

    fun outputStream(): OutputStream {
        return BufferOutputStream(this)
    }

    /**
     * 저장된 read position 무효화
     */
    private fun invalidateMark() {
        markedBlock = -1
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

/**
 * Buffer 클래스 내부에서만 생성되는 클래스로 ReadBuffer를 구현하고 자신을 생성한 Buffer 인스턴스와 내부 버퍼를
 * 공유한다. 자신은 내부 버퍼의 내용을 변경할 수 없지만 자신을 생성한 부모 Buffer 클래스는 clear(), compact() 같은
 * 메소드를 통해서 내부 버퍼의 구조를 변경할 수 있기 때문에 부모 클래스가 내부 버퍼의 내용을 변경하면 자기 자신은
 * invalid한 상태가 된다.
 *
 * 부모 인스턴스의 slice()를 메소드로 생성되어 사용되지만 valid한 상태에서만 사용해야 하며 이에 대한 책임은
 * 사용자에게 있다.
 */
private class Slice(private val blocks: List<ByteArray>, private var rBlock: Int, private var rIndex: Int, sliceLength: Int): ReadBuffer {
    private var wBlock: Int = rBlock
    private var wIndex: Int = rIndex

    private var markedBlock: Int = -1
    private var markedIndex: Int = -1

    init {
        var length = sliceLength
        while (length > 0) {
            val rest = blocks[wBlock].size - wIndex
            val len = min(length, rest)
            length -= len
            if (length > 0) {
                wBlock++
                wIndex = 0
            } else {
                wIndex += len
            }
        }
    }

    override fun isReadable(): Boolean = (rBlock < wBlock) || (rIndex < wIndex)

    override fun readableBytes(): Int {
        return when (wBlock - rBlock) {
            0 -> wIndex - rIndex
            1 -> (blocks[rBlock].size - rIndex) + wIndex
            else -> {
                (blocks[rBlock].size - rIndex) + ((wBlock - rBlock - 1) * blocks[rBlock].size) + wIndex
            }
        }
    }

    override fun read(): Int {
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

    override fun rSkip(skipLength: Int) {
        if (readableBytes() >= skipLength) {
            var length = skipLength
            while (length > 0) {
                val rest = blocks[rBlock].size - rIndex
                val len = min(length, rest)
                length -= len
                if (length > 0) {
                    rBlock++
                    rIndex = 0
                } else {
                    rIndex += len
                }
            }
        } else {
            throw UnderflowException()
        }
    }

    override fun mark() {
        markedBlock = rBlock
        markedIndex = rIndex
    }

    override fun reset() {
        if ((markedBlock >= 0)) {
            rBlock = markedBlock
            rIndex = markedIndex
        }
    }

    override fun rArray(): ByteArray {
        return blocks[rBlock]
    }

    override fun rOffset(): Int {
        return rIndex
    }
}

/**
 * Buffer를 배경으로 구현된 InputStream 구현 클래스.
 * Buffer 인스턴스를 통해서만 생성된다.
 */
private class BufferInputStream(private val buffer: ReadBuffer): InputStream() {
    override fun read(): Int {
        return buffer.read()
    }
}

/**
 * Buffer를 배경으로 구현된 OutputStream 구현 클래스.
 * Buffer 인스턴스를 통해서만 생성된다.
 */
private class BufferOutputStream(private val buffer: WriteBuffer): OutputStream() {
    override fun write(b: Int) {
        buffer.write(b)
    }
}