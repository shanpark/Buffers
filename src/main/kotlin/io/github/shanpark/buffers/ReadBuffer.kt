package io.github.shanpark.buffers

import io.github.shanpark.buffers.exception.BufferException
import io.github.shanpark.buffers.exception.UnderflowException
import java.nio.charset.Charset

interface ReadBuffer {
    /**
     * read()가 반환할 수 있는 데이터가 있는지 여부를 반환한다.
     * 참고로 writable() 메소드는 없다. 항상 writable이기 때문이다.
     *
     * @return reaa()가 반환할 데이터가 있으면 true, 반환할 데이터가 없으면 false.
     */
    fun isReadable(): Boolean

    /**
     * 현재 버퍼에 write할 수 있는 데이터 공간의 크기(byte 단위)를 얻어온다.
     * 버퍼의 현재 상태에 따른 값이며 버퍼에 write할 수 있는 공간이 없는 경우 추가로 공간을 할당하기 때문에
     * 0이 반환되는 일은 없다.
     *
     * @return write할 수 있는 공간의 크기(byte 단위)를 반환.
     */
    fun readableBytes(): Int

    /**
     * 버퍼에서 한 byte의 데이터를 읽어서 반환한다.
     * 읽은 후 read position은 다음 byte로 이동한다.
     * 버퍼가 비어있으면 -1을 반환한다.
     *
     * @return 버퍼에서 읽은 (1 byte)값. 비어있으면 -1.
     */
    fun read(): Int

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
            java.lang.Float.intBitsToFloat(readInt())
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
            java.lang.Double.longBitsToDouble(readLong())
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 2 byte를 읽어서 Char형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 2 byte의 데이터로 만들어진 Char 값.
     *
     * @throws UnderflowException 버퍼의 데이터가 2 byte 보다 적게 남아있으면 발생
     */
    fun readChar(): Char {
        return if (readableBytes() >= 2)
            readShort().toInt().toChar()
        else
            throw UnderflowException()
    }

    /**
     * 지정된 length만큼의 데이터를 이용해서 String을 생성 반환한다.
     *
     * @param length String을 생성하는 데 사용되어야 하는 데이터의 길이. (byte 단위)
     * @param charset String을 생성할 때 사용할 charset.
     *
     * @return 생성된 String 객체.
     *
     * @throws UnderflowException 버퍼의 데이터가 length 보다 적게 남아있으면 발생
     */
    fun readString(length: Int, charset: Charset = Charsets.UTF_8): String {
        return if (readableBytes() >= length) {
            val temp = ByteArray(length)
            read(temp)
            String(temp, charset)
        } else {
            throw UnderflowException()
        }
    }

    /**
     * 버퍼에서 1 byte를 읽어서 unsigned로 해석했을 때 값을 Short형으로 반환한다.
     *
     * @return 버퍼로부터 읽은 1 byte의 데이터를 unsigned로 해석했을 때 Short값.
     *
     * @throws UnderflowException 버퍼가 비어있으면 발생
     */
    fun readUByte(): Short {
        return if (isReadable())
            read().toShort()
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 2 byte를 읽어서 unsigned로 해석했을 때 값을 Int형으로 반환한다.
     *
     * @return 버퍼로부터 읽은 2 byte의 데이터를 unsigned로 해석했을 때 Int값.
     *
     * @throws UnderflowException 버퍼의 데이터가 2 byte 보다 적게 남아있으면 발생
     */
    fun readUShort(): Int {
        return if (readableBytes() >= 2)
            readShort().toInt().and(0xffff)
        else
            throw UnderflowException()
    }

    /**
     * 버퍼에서 4 byte를 읽어서 unsigned로 해석했을 때 값을 Long형으로 반환한다.
     *
     * @return 버퍼로부터 읽은 4 byte의 데이터를 unsigned로 해석했을 때 Long값.
     *
     * @throws UnderflowException 버퍼의 데이터가 4 byte 보다 적게 남아있으면 발생
     */
    fun readUInt(): Long {
        return if (readableBytes() >= 4)
            readInt().toLong().and(0xffffffff)
        else
            throw UnderflowException()
    }

    /**
     * read position을 지정된 length 만큼 이동시킨다.
     *
     * @param skipLength read position을 옮길 byte 수.
     *
     * @throws UnderflowException 남아있는 데이터의 크기가 지정된 byte 수 보다 작은 경우 발생
     */
    fun rSkip(skipLength: Int)

    /**
     * 현재 read position을 임시로 저장한다.
     * 이 후 reading 작업을 계속 하다고 reset()이 호출되면 다시 mark()를 호출했던 시점으로 돌아간다.
     * 혹시 이전에 저장된 상태가 있었다면 버려진다.
     *
     * 버퍼의 구현에 따라 내부 상태가 바뀌면 언제든지 저장된 상태는 버려질 수 있다.
     */
    fun mark()

    /**
     * mark()를 호출하여 저장했던 상태로 돌아간다.
     * 저장된 상태가 없으면 아무 일도 하지 않는다.
     */
    fun reset()

    /**
     * read 작업을 할 ByteArray를 반환한다.
     * 반환된 array의 전체를 읽을 수 있는 건 아니다. rOffset() 메소드가 반환하는 위치부터 읽을 수 있는 데이터가 있다.
     * readableBytes()가 반환하는 값이 이 남은 공간의 크기보다 크다면 남은 공간 전체를 읽을 수 있고 그렇지 않다면
     * readableBytes()가 반환하는 값 만큼 읽을 수 있다.
     * readableBytes()가 반환하는 값이 이 남은 공간의 크기보다 크다면 버퍼의 내용을 다 읽어내거 rSkip()한 후에
     * 다시 이 메소드를 호출하면 그 다음 내용을 담은 array()가 반환된다.
     * 버퍼가 비어있더라도 array는 반환된다.
     *
     * @return read를 위해서 사용할 수 있는 ByteArray를 반환.
     */
    fun rArray(): ByteArray

    /**
     * rArray() 메소드가 반환하는 ByteArray의 readable한 공간의 시작 offset을 반환한다.
     *
     * @return rArray()가 반환하는 ByteArray의 readable 공간의 시작 offset.
     */
    fun rOffset(): Int
}