package io.github.shanpark.buffers

import kotlin.math.max
import kotlin.math.min

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