package me.hydos.rosella.resource

import java.io.InputStream
import java.nio.ByteBuffer

interface Resource {

	val identifier: Identifier

	val loader: ResourceLoader

	fun openStream(): InputStream

	fun readAllBytes(native: Boolean = false): ByteBuffer {
		val bytes = openStream().readBytes()

		if (native) {
			val buffer = ByteBuffer.allocateDirect(bytes.size)
			buffer.put(bytes)
			buffer.rewind()
			return buffer
		}

		return ByteBuffer.wrap(bytes)
	}
}
