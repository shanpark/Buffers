package io.github.shanpark.buffers

interface WriteBuffer {
    fun writableBytes(): Int

    fun write(data: Int)
    fun write(buf: ByteArray)
    fun write(buf: ByteArray, offset: Int, length: Int)

    fun writeByte(value: Byte)
    fun writeShort(value: Short)
    fun writeInt(value: Int)
    fun writeLong(value: Long)
    fun writeFloat(value: Float)
    fun writeDouble(value: Double)
    fun writeChar(value: Char)

    fun wArray(): ByteArray
    fun wOffset(): Int
}