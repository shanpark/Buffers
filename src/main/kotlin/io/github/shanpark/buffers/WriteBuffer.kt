package io.github.shanpark.buffers

interface WriteBuffer {
    /**
     * 현재 버퍼에 write할 수 있는 데이터 공간의 크기(byte 단위)를 얻어온다.
     * 버퍼의 현재 상태에 따른 값이며 구현된 방식에 따라 현재 상태의 일시적인 값이 될 수도 있다.
     * 예를 들어 동적으로 공간을 확장하도록 구현한다면 무한한 값이 될 수도 있고, 남은 공간을 다 채운 후에
     * 다시 공간을 할당하면 다시 늘어난 값을 반환할 수 있다.
     *
     * @return write할 수 있는 공간의 크기(byte 단위)를 반환.
     */
    fun writableBytes(): Int

    /**
     * 버퍼에 1 byte의 데이터를 write한다.
     *
     * @param data 버퍼에 기록할 data. least significant 8 bits만 기록된다.
     */
    fun write(data: Int)

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
     * 버퍼에 Byte 값을 write한다.
     *
     * @param value 버퍼에 기록할 Byte 값.
     */
    fun writeByte(value: Byte) = write(value.toInt())

    /**
     * 버퍼에 Short 값을 write한다.
     *
     * @param value 버퍼에 기록할 Short 값.
     */
    fun writeShort(value: Short) {
        val i = value.toInt()
        write(i.shr(8))
        write(i)
    }

    /**
     * 버퍼에 Int 값을 write한다.
     *
     * @param value 버퍼에 기록할 Int 값.
     */
    fun writeInt(value: Int) {
        write(value.shr(24))
        write(value.shr(16))
        write(value.shr(8))
        write(value)
    }

    /**
     * 버퍼에 Long 값을 write한다.
     *
     * @param value 버퍼에 기록할 Long 값.
     */
    fun writeLong(value: Long) {
        writeInt(value.shr(32).toInt())
        writeInt(value.toInt()) // least significant 32 bit wil be used.
    }

    /**
     * 버퍼에 Float 값을 write한다.
     *
     * @param value 버퍼에 기록할 Float 값.
     */
    fun writeFloat(value: Float) = writeInt(java.lang.Float.floatToIntBits(value))

    /**
     * 버퍼에 Double 값을 write한다.
     *
     * @param value 버퍼에 기록할 Double 값.
     */
    fun writeDouble(value: Double) = writeLong(java.lang.Double.doubleToLongBits(value))

    /**
     * 버퍼에 Char 값을 write한다.
     *
     * @param value 버퍼에 기록할 Char 값.
     */
    fun writeChar(value: Char) = writeShort(value.code.toShort())

    /**
     * write 작업을 할 ByteArray를 반환한다.
     * 반환된 array의 공간을 모두 쓸 수 있는 건 아니다. offset() 메소드가 반환하는 위치부터 array의 끝까지 사용할 수 있다.
     *
     * @return write를 위해서 사용할 수 있는 ByteArray를 반환.
     */
    fun wArray(): ByteArray


    /**
     * wArray() 메소드가 반환하는 ByteArray의 사용 가능한 공간의 시작 offset을 반환한다.
     *
     * @return wArray()가 반환하는 ByteArray의 writable 공간의 시작 offset.
     */
    fun wOffset(): Int
}