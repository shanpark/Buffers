package com.github.shanpark.buffers

import com.github.shanpark.buffers.exception.BufferException
import com.github.shanpark.buffers.exception.UnderflowException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.math.min

interface ReadBuffer {
    /**
     * 버퍼에 읽을 수 있는 데이터가 있는 지 여부.
     */
    val isReadable: Boolean

    /**
     * 현재 버퍼에 읽을 수 있는 데이터의 크기. (byte 단위)
     */
    val readableBytes: Int

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
     * 파라미터로 전달된 WriteBuffer로 이 버퍼의 내용을 모두 읽어서 write한다.
     *
     * @param buf 읽은 데이터를 기록할 WriteBuffer

     * @return 실제 읽혀진 byte 수.
     */
    fun read(buf: WriteBuffer): Int {
        var total = 0
        while (isReadable) {
            val length = min(min(readableBytes, rArray.size - rOffset), buf.writableBytes)
            System.arraycopy(rArray, rOffset, buf.wArray, buf.wOffset, length)
            buf.wSkip(length)
            rSkip(length)
            total += length
        }
        return total
    }

    /**
     * 파라미터로 전달된 ByteBuffer로 이 버퍼의 내용을 모두 읽어서 write한다.
     *
     * @param byteBuffer 읽은 데이터를 기록할 WriteBuffer

     * @return 실제 읽혀진 byte 수.
     */
    fun read(byteBuffer: ByteBuffer): Int {
        var total = 0
        while (isReadable) {
            val length = min(min(readableBytes, rArray.size - rOffset), byteBuffer.remaining())
            if (length <= 0) // byteBuffer의 remaining 공간이 0인 경우
                break
            byteBuffer.put(rArray, rOffset, length)
            rSkip(length)
            total += length
        }
        return total
    }

    /**
     * 버퍼에서 1 byte를 읽어서 Byte형으로 만들어 반환한다.
     *
     * @return 버퍼로부터 읽은 1 byte의 데이터로 만들어진 Byte 값.
     *
     * @throws UnderflowException 버퍼가 비어있으면 발생
     */
    fun readByte(): Byte {
        return if (isReadable)
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
        return if (readableBytes >= 2)
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
        return if (readableBytes >= 4)
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
        return if (readableBytes >= 8)
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
        return if (readableBytes >= 4)
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
        return if (readableBytes >= 8)
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
        return if (readableBytes >= 2)
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
        return if (readableBytes >= length) {
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
        return if (isReadable)
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
        return if (readableBytes >= 2)
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
        return if (readableBytes >= 4)
            readInt().toLong().and(0xffffffff)
        else
            throw UnderflowException()
    }

    /**
     * 이 버퍼에서 length 만큼만 읽을 수 있도록 제한된 ReadBuffer를 생성하여 반환한다.
     *
     * 남아있는 데이터가 length 보다 작으면 IndexOutOfBoundsException이 발생한다.
     *
     * @param length 생성된 ReadBuffer로부터 읽고자 하는 데이터의 길이.
     *
     * @return 데이터를 읽을 수 있는 ReadBuffer 객체.
     *
     * @throws IndexOutOfBoundsException 남아있는 데이터보다 더 많은 데이터를 요청하는 경우 발생.
     */
    fun readSlice(length: Int): ReadBuffer

    /**
     * 이 버퍼를 배경으로 동작하는 InputStream 객체를 반환한다.
     * 반환된 InputStream 객체는 이 버퍼의 proxy 정도의 역할이며 반환된 InputStream 객체를
     * 통해서 데이터를 읽으면 이 버퍼의 read position도 이동된다.
     *
     * @return 이 버퍼를 배경으로 동작하는 InputStream 객체.
     */
    fun inputStream(): InputStream

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
     * 반환된 array의 전체를 읽을 수 있는 건 아니다. rOffset속성이 참조하는 위치부터 읽을 수 있는 데이터가 있다.
     * readableBytes()가 반환하는 값이 이 남은 공간의 크기보다 크다면 남은 공간 전체를 읽을 수 있고 그렇지 않다면
     * readableBytes()가 반환하는 값 만큼 읽을 수 있다.
     * readableBytes()가 반환하는 값이 이 남은 공간의 크기보다 크다면 버퍼의 내용을 다 읽어내거 rSkip()한 후에
     * 다시 이 속성을 확인하면 그 다음 내용을 담은 ByteArray를 참조하고 있다.
     * 내용이 없는 상태일 지라도 rArray는 항상 ByteArray를 참조하고 있다.
     */
    val rArray: ByteArray

    /**
     * rArray의 readable한 공간의 시작 offset을 보여준다.
     */
    val rOffset: Int
}