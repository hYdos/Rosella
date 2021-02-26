package me.hydos.rosella.resource

import java.io.InputStream
import java.nio.ByteBuffer

interface ResourceLoader {

	fun loadResource(id: Identifier): InputStream?

	fun loadBytes(id: Identifier): ByteBuffer? {
		return loadResource(id)?.readBytes()?.let(ByteBuffer::wrap)
	}
}

class CascadingResourceLoader(private val loaders: Collection<ResourceLoader>): ResourceLoader {

	override fun loadResource(id: Identifier): InputStream? {
		for (loader in loaders) {
			val resource = loader.loadResource(id)

			if (resource != null) {
				return resource
			}
		}

		return null
	}
}

class ClassLoaderResourceLoader(private val loader: ClassLoader): ResourceLoader {

	override fun loadResource(id: Identifier): InputStream? {
		return loader.getResourceAsStream("${id.namespace}/${id.path}")
	}
}
