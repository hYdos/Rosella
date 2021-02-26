package me.hydos.rosella.texture

import org.lwjgl.BufferUtils.createByteBuffer
import java.nio.ByteBuffer
import java.nio.channels.Channels

fun ioResourceToByteBuffer(resource: String?, bufferSize: Int): ByteBuffer {
	var buffer: ByteBuffer
	ClassLoader.getSystemClassLoader().getResourceAsStream(resource).use { source ->
		Channels.newChannel(source!!).use { rbc ->
			buffer = createByteBuffer(bufferSize)
			while (true) {
				val bytes = rbc.read(buffer)
				if (bytes == -1) {
					break
				}
				if (buffer.remaining() === 0) {
					buffer = resizeBuffer(buffer, buffer.capacity() * 3 / 2)
				}
			}
		}
	}
	buffer.flip()
	return buffer
}

private fun resizeBuffer(buffer: ByteBuffer, newCapacity: Int): ByteBuffer {
	val newBuffer: ByteBuffer = createByteBuffer(newCapacity)
	buffer.flip()
	newBuffer.put(buffer)
	return newBuffer
}
