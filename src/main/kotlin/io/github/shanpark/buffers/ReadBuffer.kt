package io.github.shanpark.buffers

interface ReadBuffer {
    fun isReadable(): Boolean
    fun readableBytes(): Int

    fun read(): Int
    fun read(buf: ByteArray): Int
    fun read(buf: ByteArray, offset: Int, length: Int): Int

    fun readByte(): Byte
    fun readShort(): Short
    fun readInt(): Int
    fun readLong(): Long
    fun readFloat(): Float
    fun readDouble(): Double
    fun readChar(): Char

    fun readUByte(): Short
    fun readUShort(): Int
    fun readUInt(): Long

    fun mark()
    fun reset()

    fun rArray(): ByteArray
    fun rOffset(): Int
}