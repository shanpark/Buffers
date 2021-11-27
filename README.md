# Buffers

[![](https://jitpack.io/v/shanpark/buffers.svg)](https://jitpack.io/#shanpark/buffers)

## Usage

```kotlin
import java.nio.ByteBuffer

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

buffer.readByte() // == 0x12
buffer.readShort() // == 0x1234
buffer.readInt() // == 0x12345678
buffer.readLong() // == 0x12345678_12345678L
buffer.readFloat() // == 123.45f
buffer.readDouble() // == 1234.56789
buffer.readChar() // == '한'
buffer.readUByte() // == 0xff
buffer.readUShort() // == 0xffff
buffer.readUInt() // == 0xffffffffL
buffer.readString() // == "Hello World!"

// OutputStream from Buffer.
val ostream = buffer.outputStream()
ostream.write(100)

// InputStream from Buffer.
val istream = buffer.inputStream()
istream.read() // == 100

// ByteBuffer read/write
val byteBuffer = ByteBuffer.allocate(1024)
    /* write some data... */
byteBuffer.flip()
buffer.write(byteBuffer) // byteBuffer -> buffer

byteBuffer.clear()
buffer.read(byteBuffer) // buffer -> byteBuffer
```

## Install

To install the library add:

* Gradle

```gradle
repositories { 
   ...
   maven { url "https://jitpack.io" }
}

dependencies {
   implementation 'com.github.shanpark:buffers:0.1.0'
}
```

* Gradle(Kotlin)

```gradle
repositories { 
    ...
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation('com.github.shanpark:buffers:0.1.0')
}
```
