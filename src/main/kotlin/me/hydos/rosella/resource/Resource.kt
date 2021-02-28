package me.hydos.rosella.resource

import java.io.InputStream
import java.nio.ByteBuffer

interface Resource {

	val identifier: Identifier

	fun openStream(): InputStream

	fun readAllBytes(): ByteBuffer {
		return ByteBuffer.wrap(openStream().readBytes())
	}
}
