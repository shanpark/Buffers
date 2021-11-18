package com.github.shanpark.buffers

import com.github.shanpark.buffers.exception.UnderflowException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
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

    override val isReadable: Boolean
        get() = (rBlock < wBlock) || (rIndex < wIndex)

    override val readableBytes: Int
        get() {
            return when (wBlock - rBlock) {
                0 -> wIndex - rIndex
                1 -> (blocks[rBlock].size - rIndex) + wIndex
                else -> {
                    (blocks[rBlock].size - rIndex) + ((wBlock - rBlock - 1) * blocks[rBlock].size) + wIndex
                }
            }
        }

    override fun read(): Int {
        return if (isReadable) {
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
     * 지정된 length만큼의 데이터를 이용해서 String을 생성 반환한다.
     * Buffer의 내부 구조에따라 더 효율적인 방법으로 구현하기 위해서 override함.
     *
     * @param length String을 생성하는 데 사용되어야 하는 데이터의 길이. (byte 단위)
     * @param charset String을 생성할 때 사용할 charset.
     *
     * @return 생성된 String 객체.
     *
     * @throws UnderflowException 버퍼의 데이터가 length 보다 적게 남아있으면 발생
     */
    override fun readString(length: Int, charset: Charset): String {
        return if (readableBytes >= length) {
            if (length <= (blocks[rBlock].size - rIndex)) {
                val str = String(blocks[rBlock], rIndex, length, charset)
                rSkip(length)
                str
            } else {
                val temp = ByteArray(length)
                read(temp)
                String(temp, charset)
            }
        } else {
            throw UnderflowException()
        }
    }

    /**
     * 이 버퍼에서 length 만큼만 읽을 수 있도록 제한된 ReadBuffer를 생성하여 반환한다.
     * 실제로 데이터의 복사가 일어나지는 않으며 같은 내부 버퍼를 공유한다. 이 버퍼의 read position은 length만큼 이동하게 된다.
     *
     * 남아있는 데이터가 length 보다 작으면 IndexOutOfBoundsException이 발생한다.
     *
     * @param length 생성된 ReadBuffer로부터 읽고자 하는 데이터의 길이.
     *
     * @return 데이터를 읽을 수 있는 ReadBuffer 객체.
     *
     * @throws IndexOutOfBoundsException 남아있는 데이터보다 더 많은 데이터를 요청하는 경우 발생.
     */
    override fun readSlice(length: Int): ReadBuffer {
        if (readableBytes >= length) {
            val slice = Slice(blocks, rBlock, rIndex, length)
            rSkip(length) // read position 이동.
            return slice
        }
        else
            throw IndexOutOfBoundsException()
    }

    /**
     * 이 버퍼를 배경으로 동작하는 InputStream 객체를 반환한다.
     * 반환된 InputStream 객체는 이 버퍼의 proxy 정도의 역할이므로 반환된 InputStream 객체를
     * 통해서 데이터를 읽으면 이 버퍼의 read position도 이동된다.
     *
     * @return 이 버퍼를 배경으로 동작하는 InputStream 객체.
     */
    override fun inputStream(): InputStream {
        return BufferInputStream(this)
    }

    override fun rSkip(skipLength: Int) {
        if (readableBytes >= skipLength) {
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
            throw IndexOutOfBoundsException()
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

    override val rArray: ByteArray
        get() {
            adjustReadPositionIfNeeded()
            return blocks[rBlock]
        }

    override val rOffset: Int
        get() {
            adjustReadPositionIfNeeded()
            return rIndex
        }

    /**
     * 현재 버퍼에 write할 수 있는 데이터 공간의 크기(byte 단위)를 얻어온다.
     * 남은 공간을 다 사용한 후에는 다시 새로운 공간이 할당되므로 0이 반환되는 일은 없다.
     *
     * @return write할 수 있는 공간의 크기(byte 단위)를 반환.
     */
    override val writableBytes: Int
        get() {
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

    /**
     * 이 버퍼를 배경으로 동작하는 OutputStream 객체를 반환한다.
     * 반환된 OutputStream 객체는 이 버퍼의 proxy 정도의 역할이며 반환된 OutputStream 객체를
     * 통해서 write 작업을 하면 이 버퍼의 write position도 이동된다.
     *
     * @return 이 버퍼를 배경으로 동작하는 InputStream 객체.
     */
    override fun outputStream(): OutputStream {
        return BufferOutputStream(this)
    }

    override fun wSkip(skipLength: Int) {
        // write position은 항상 마지막 block에 존재하며 추가 할당은 일어나지 않으므로 다음 block으로
        // 이동하는 건 고려하지 않아도 됨.
        val rest = blocks[wBlock].size - wIndex
        if (skipLength <= rest)
            wIndex += skipLength
        else
            throw IndexOutOfBoundsException()
    }

    /**
     * write 작업을 할 ByteArray를 반환한다.
     * 반환된 array의 공간을 모두 쓸 수 있는 건 아니다. offset() 메소드가 반환하는 위치부터 array의 끝까지 사용할 수 있다.
     * 현재 가용한 공간이 없는 상태라면 추가 공간 할당이 수행된다.
     *
     * @return write를 위해서 사용할 수 있는 ByteArray를 반환.
     */
    override val wArray: ByteArray
        get() {
            allocBufferIfNeeded()
            return blocks[wBlock]
        }

    /**
     * array() 메소드가 반환하는 ByteArray의 사용 가능한 공간의 시작 offset을 반환한다.
     * 현재 가용한 공간이 없는 상태라면 추가 공간 할당이 수행된다.
     *
     * @return wArray()가 반환하는 ByteArray의 writable 공간의 시작 offset.
     */
    override val wOffset: Int
        get() {
            allocBufferIfNeeded()
            return wIndex
        }

    /**
     * 사용이 끝난 내부 버퍼들을 정리한다.
     * 내부 구조가 바뀌게 되므로 mark()를 호출하여 저장된 상태는 모두 invalidate 된다.
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
     * mark()로 저장된 상태도 invalidate된다.
     */
    override fun clear() {
        val newBuffer = ByteArray(blocks.first().size)
        blocks.clear() // 완전히 clear를 해야 한다. 공유하는 객체가 있을 수 있기 때문에 다시 재사용을 하면 공유 객체에게 영향이 생긴다.
        blocks.add(newBuffer)

        rBlock = 0
        wBlock = 0
        rIndex = 0
        wIndex = 0

        invalidateMark()
    }

    /**
     * 저장된 read position 무효화
     */
    private fun invalidateMark() {
        markedBlock = -1
    }

    /**
     * read position이 현재 array의 끝을 가리키고 있는 경우
     * 다음 array가 있다면 다음 array의 시작점으로 옮긴다.
     */
    private fun adjustReadPositionIfNeeded() {
        if (isReadable) {
            if (blocks[rBlock].size == rIndex) {
                rBlock++
                rIndex = 0
            }
        }
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
 * 공유한다. 내부 버퍼는 공유하지만 상태는 별도로 관리되므로 여기서 일어나는 read 작업은 부모 버퍼에 아무런 영향을 미치지 않는다.
 *
 * 부모 버퍼가 해제가 되더라도 내부 버퍼에 대한 참조는 따로 가지고 있기 때문에 생성된 Slice 객체는 안전하게 사용가능하다.
 */
private class Slice(parentBlocks: List<ByteArray>, parentRBlock: Int, parentRIndex: Int, sliceLength: Int) :
    ReadBuffer {
    private val blocks = mutableListOf<ByteArray>()
    private var rBlock: Int = 0
    private var wBlock: Int = parentRBlock
    private var rIndex: Int = parentRIndex
    private var wIndex: Int = parentRIndex

    private var markedBlock: Int = -1
    private var markedIndex: Int = -1

    init {
        var length = sliceLength
        while (length > 0) {
            val rest = parentBlocks[wBlock].size - wIndex
            val len = min(length, rest)
            length -= len
            if (length > 0) {
                wBlock++
                wIndex = 0
            } else {
                wIndex += len
            }
        }

        for (inx in parentRBlock..wBlock)
            blocks.add(parentBlocks[inx])

        wBlock -= parentRBlock
    }

    override val isReadable: Boolean
        get() = (rBlock < wBlock) || (rIndex < wIndex)

    override val readableBytes: Int
        get() {
            return when (wBlock - rBlock) {
                0 -> wIndex - rIndex
                1 -> (blocks[rBlock].size - rIndex) + wIndex
                else -> {
                    (blocks[rBlock].size - rIndex) + ((wBlock - rBlock - 1) * blocks[rBlock].size) + wIndex
                }
            }
        }

    override fun read(): Int {
        return if (isReadable) {
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
     * 이 버퍼에서 length 만큼만 읽을 수 있도록 제한된 ReadBuffer를 생성하여 반환한다.
     * 실제로 데이터의 복사가 일어나지는 않으며 같은 내부 버퍼를 공유한다. 이 버퍼의 read position은 length만큼 이동하게 된다.
     *
     * 남아있는 데이터가 length 보다 작으면 IndexOutOfBoundsException이 발생한다.
     *
     * @param length 생성된 ReadBuffer로부터 읽고자 하는 데이터의 길이.
     *
     * @return 데이터를 읽을 수 있는 ReadBuffer 객체.
     *
     * @throws IndexOutOfBoundsException 남아있는 데이터보다 더 많은 데이터를 요청하는 경우 발생.
     */
    override fun readSlice(length: Int): ReadBuffer {
        if (readableBytes >= length) {
            val slice = Slice(blocks, rBlock, rIndex, length)
            rSkip(length) // read position 이동.
            return slice
        }
        else
            throw IndexOutOfBoundsException()
    }

    override fun inputStream(): InputStream {
        return BufferInputStream(this)
    }

    override fun rSkip(skipLength: Int) {
        if (readableBytes >= skipLength) {
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
            throw IndexOutOfBoundsException()
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

    override val rArray: ByteArray
        get() {
            adjustReadPositionIfNeeded()
            return blocks[rBlock]
        }

    override val rOffset: Int
        get() {
            adjustReadPositionIfNeeded()
            return rIndex
        }

    /**
     * read position이 현재 array의 끝을 가리키고 있는 경우
     * 다음 array가 있다면 다음 array의 시작점으로 옮긴다.
     */
    private fun adjustReadPositionIfNeeded() {
        if (isReadable) {
            if (blocks[rBlock].size == rIndex) {
                rBlock++
                rIndex = 0
            }
        }
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
